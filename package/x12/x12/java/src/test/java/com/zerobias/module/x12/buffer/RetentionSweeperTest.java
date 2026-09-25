package com.zerobias.module.x12.buffer;

import com.zerobias.module.x12.buffer.TestRows.MutableClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;

import static com.zerobias.module.x12.buffer.TestRows.BASE;
import static com.zerobias.module.x12.buffer.TestRows.FILE_A;
import static com.zerobias.module.x12.buffer.TestRows.file;
import static com.zerobias.module.x12.buffer.TestRows.tx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Retention + purge (DESIGN §8, §2.5): only ACKED transaction rows are ever evicted;
 * un-acked rows and {@code files} rows (the audit trail) survive every sweep.
 */
class RetentionSweeperTest {

    private BufferStore open(Path dir, MutableClock clock) throws Exception {
        return new BufferStore(dir.resolve("buffer.db").toString(), false, clock);
    }

    /** Consume a file with 3 transactions, ack the first two (at BASE), leave the third new. */
    private static void seed(BufferStore s) throws Exception {
        s.consumeFile(file(FILE_A, "inbox", "c1", FileStatus.CONSUMED, 3),
            List.of(tx("1", "0001", 0), tx("1", "0002", 1), tx("1", "0003", 2)));
        s.ack(s.take(null, 2, Duration.ofMinutes(5)).leaseId(), null);
    }

    @Test
    void purgeRemovesOldAckedOnlyAndIsInclusiveAtCutoff(@TempDir Path dir) throws Exception {
        MutableClock clock = new MutableClock(BASE);
        try (BufferStore s = open(dir, clock)) {
            seed(s);
            // acked_at == now == cutoff → purge(PT0S) must include just-acked rows
            assertEquals(2, s.purge(Duration.ZERO));
            assertEquals(0, s.count(Status.ACKED));
            assertEquals(1, s.count(Status.NEW), "un-acked row survives purge");
            assertEquals(1, s.fileCount(), "files rows are never purged");
        }
    }

    @Test
    void purgeRespectsOlderThan(@TempDir Path dir) throws Exception {
        MutableClock clock = new MutableClock(BASE);
        try (BufferStore s = open(dir, clock)) {
            seed(s);
            clock.advance(Duration.ofMinutes(30));
            assertEquals(0, s.purge(Duration.ofHours(1)), "acked 30 min ago is younger than 1h");
            assertEquals(2, s.purge(Duration.ofMinutes(10)));
        }
    }

    @Test
    void maxAgeEvictsAckedOnly(@TempDir Path dir) throws Exception {
        MutableClock clock = new MutableClock(BASE);
        try (BufferStore s = open(dir, clock)) {
            seed(s);
            RetentionSweeper sweeper = new RetentionSweeper(s, RetentionConfig.maxAge(Duration.ofDays(7)), clock);
            assertEquals(0, sweeper.sweep(), "nothing old enough yet");
            clock.advance(Duration.ofDays(10));
            assertEquals(2, sweeper.sweep());
            assertEquals(0, s.count(Status.ACKED));
            assertEquals(1, s.count(Status.NEW), "un-acked row is never evicted");
            assertEquals(1, s.fileCount(), "files rows are never evicted");
        }
    }

    @Test
    void maxBytesEvictsAckedOnlyAndReportsBackpressure(@TempDir Path dir) throws Exception {
        MutableClock clock = new MutableClock(BASE);
        try (BufferStore s = open(dir, clock)) {
            seed(s);
            // maxBytes=0 forces the eviction loop until no acked rows remain, and leaves
            // the buffer "over capacity" with nothing left to evict → backpressure.
            RetentionSweeper sweeper = new RetentionSweeper(s, new RetentionConfig(null, 0L), clock);
            assertEquals(2, sweeper.sweep());
            assertEquals(0, s.count(Status.ACKED));
            assertEquals(1, s.count(Status.NEW), "un-acked row is never evicted by maxBytes");
            assertEquals(1, s.fileCount());
            assertTrue(sweeper.overCapacity(), "still over the (zero) ceiling with nothing evictable");

            RetentionSweeper roomy = new RetentionSweeper(s, new RetentionConfig(null, Long.MAX_VALUE), clock);
            assertFalse(roomy.overCapacity());
            assertFalse(new RetentionSweeper(s, RetentionConfig.none(), clock).overCapacity(), "unbounded = never");
        }
    }

    @Test
    void unboundedConfigSweepsNothing(@TempDir Path dir) throws Exception {
        MutableClock clock = new MutableClock(BASE);
        try (BufferStore s = open(dir, clock)) {
            seed(s);
            clock.advance(Duration.ofDays(365));
            assertEquals(0, new RetentionSweeper(s, RetentionConfig.none(), clock).sweep());
            assertEquals(2, s.count(Status.ACKED));
        }
    }

    @Test
    void startAndStopAreIdempotent(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            RetentionSweeper sweeper = new RetentionSweeper(s, RetentionConfig.maxAge(Duration.ofDays(1)),
                new MutableClock(BASE));
            sweeper.start(Duration.ofHours(1));
            sweeper.start(Duration.ofHours(1));
            sweeper.stop();
            sweeper.stop();
        }
    }

    // ---- batching, atomicity, reclaiming --------------------------------------------------

    /** A second connection onto the same buffer file, for seeding and fault injection. */
    private static Connection side(Path dir) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("buffer.db"));
    }

    /**
     * {@code n} acked transaction rows (acked at BASE), each with a one-entity graph, one value
     * and one dimension, written in bulk through a side connection.
     */
    private static void seedAcked(Path dir, int n) throws SQLException {
        try (Connection c = side(dir); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            st.execute("WITH RECURSIVE seq(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM seq WHERE i < " + n + ") "
                + "INSERT INTO transactions (element_key, file_id, source_name, received_at, gs08, "
                + "transaction_type, schema_id, raw_x12, status, acked_at) "
                + "SELECT 'k' || i, 'f', 'inbox', " + BASE.toEpochMilli() + ", 'g', '835', 's', x'00', 'acked', "
                + BASE.toEpochMilli() + " FROM seq");
            st.execute("INSERT INTO entities (element_key, file_id, gs08, schema_id, xid, kind, path) "
                + "SELECT element_key, 'f', 'g', 's', 'ST', 'loop', '' FROM transactions");
            st.execute("INSERT INTO entity_values (entity_id, seq, property, data_type, value_text) "
                + "SELECT id, 0, 'st02', 'string', 'x' FROM entities");
            st.execute("INSERT INTO transaction_dims (element_key, dim, data_type, value_text) "
                + "SELECT element_key, 'payerName', 'string', 'P' FROM transactions");
            c.commit();
        }
    }

    private static long countOf(Path dir, String table) throws SQLException {
        try (Connection c = side(dir); Statement st = c.createStatement();
             var rs = st.executeQuery("SELECT count(*) FROM " + table)) {
            return rs.next() ? rs.getLong(1) : -1;
        }
    }

    @Test
    void purgeDeletesMoreRowsThanSqliteHasHostParameters(@TempDir Path dir) throws Exception {
        // sqlite-jdbc allows 250000 host parameters (stock SQLite 32766). The old purge bound one
        // parameter per acked key in a single statement and failed past that.
        int n = 250_001;
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            seedAcked(dir, n);
            assertEquals(n, s.purge(Duration.ZERO));
            assertEquals(0, s.count());
        }
        assertEquals(0, countOf(dir, "entities"), "no orphaned entities");
        assertEquals(0, countOf(dir, "entity_values"), "no orphaned values");
        assertEquals(0, countOf(dir, "transaction_dims"), "no orphaned dimensions");
    }

    @Test
    void aFailedGraphDeleteRollsItsTransactionRowsBack(@TempDir Path dir) throws Exception {
        // The transactions DELETE used to autocommit before the graph delete ran: a failure in
        // between left graph rows whose transaction was gone (or the reverse). Now a batch is one
        // SQL transaction, so a failing graph delete keeps the rows it was deleting.
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            seedAcked(dir, 3);
            try (Connection c = side(dir); Statement st = c.createStatement()) {
                st.execute("CREATE TRIGGER no_entity_delete BEFORE DELETE ON entities "
                    + "BEGIN SELECT RAISE(ABORT, 'injected'); END");
            }
            assertThrows(SQLException.class, () -> s.purge(Duration.ZERO));
            assertEquals(3, s.count(), "transaction rows survive a failed batch");
            assertEquals(3, s.count(Status.ACKED));
        }
        assertEquals(3, countOf(dir, "entities"));
        assertEquals(3, countOf(dir, "transaction_dims"), "dimensions rolled back too");
    }

    @Test
    void purgeHandsTheFreedPagesBack(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            seedAcked(dir, 5_000);
            long before = s.dbSizeBytes();
            s.purge(Duration.ZERO);
            assertEquals(0, s.freePages(), "purge vacuums what it freed");
            assertTrue(s.dbSizeBytes() < before, "the file shrank");
        }
    }

    @Test
    void maxAgeSweepVacuumsToo(@TempDir Path dir) throws Exception {
        // The sweep used to vacuum only inside the maxBytes loop (and then just one page).
        MutableClock clock = new MutableClock(BASE);
        try (BufferStore s = open(dir, clock)) {
            seedAcked(dir, 5_000);
            clock.advance(Duration.ofDays(10));
            RetentionSweeper sweeper = new RetentionSweeper(s, RetentionConfig.maxAge(Duration.ofDays(7)), clock);
            assertEquals(5_000, sweeper.sweep());
            assertEquals(0, s.freePages(), "maxAge eviction is reclaimed as well");
        }
    }

    @Test
    void maxBytesSweepReclaimsEveryFreedPage(@TempDir Path dir) throws Exception {
        MutableClock clock = new MutableClock(BASE);
        try (BufferStore s = open(dir, clock)) {
            seedAcked(dir, 5_000);
            RetentionSweeper sweeper = new RetentionSweeper(s, new RetentionConfig(null, 0L), clock);
            assertEquals(5_000, sweeper.sweep());
            assertEquals(0, s.freePages(), "not just the one page a bare incremental_vacuum step frees");
        }
    }

    @Test
    void freePagesAreRoomNotLoad(@TempDir Path dir) throws Exception {
        // Backpressure measured page_count × page_size, free pages included: right after an
        // eviction (before a vacuum) the buffer still read as full.
        MutableClock clock = new MutableClock(BASE);
        try (BufferStore s = open(dir, clock)) {
            seedAcked(dir, 5_000);
            s.deleteAckedOlderThanMillis(BASE.toEpochMilli());   // the raw primitive: no vacuum
            assertTrue(s.freePages() > 0, "precondition: freed pages not yet handed back");
            long used = s.usedBytes();
            assertTrue(used < s.dbSizeBytes());
            RetentionSweeper sweeper = new RetentionSweeper(s, new RetentionConfig(null, used), clock);
            assertFalse(sweeper.overCapacity(), "live data fits; the free pages are room");
        }
    }
}

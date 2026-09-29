package com.zerobias.module.x12.buffer;

import com.zerobias.module.x12.buffer.TestRows.MutableClock;
import com.zerobias.module.x12.materializer.EntityGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.zerobias.module.x12.buffer.TestRows.BASE;
import static com.zerobias.module.x12.buffer.TestRows.FILE_A;
import static com.zerobias.module.x12.buffer.TestRows.SCHEMA_835;
import static com.zerobias.module.x12.buffer.TestRows.tx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An {@link Error} in the middle of a buffer transaction (an OOM while inserting a large graph,
 * say) rolls the whole unit back — rows, graph and dimensions — exactly like an
 * {@link java.sql.SQLException}. With the xerial driver, restoring autocommit on the way out
 * issues {@code COMMIT}, so a handler that catches only exceptions commits the partial work.
 */
class ErrorRollbackTest {

    private static BufferStore open(Path dir) throws Exception {
        return new BufferStore(dir.resolve("buffer.db").toString(), false, new MutableClock(BASE));
    }

    /** A graph of several instances whose second one cannot be produced: the first is already inserted. */
    private static List<EntityGraph.Entity> dyingGraph(List<EntityGraph.Entity> real) {
        assertTrue(real.size() > 1);
        return new AbstractList<>() {
            @Override
            public EntityGraph.Entity get(int i) {
                if (i == 1) {
                    throw new OutOfMemoryError("simulated: graph too large for the heap");
                }
                return real.get(i);
            }

            @Override
            public int size() {
                return real.size();
            }
        };
    }

    private static List<EntityGraph.Entity> graph(String st02) {
        return TestRows.graphFromMap(SCHEMA_835, Map.of("header", Map.of("st", Map.of("st02", st02)),
            "trailer", Map.of("se", Map.of("se02", st02))));
    }

    @Test
    void consumeFileLeavesNoRowGraphOrFileBehindWhenAnErrorStrikesMidway(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir)) {
            TransactionRow first = tx("1", "0001", 0);
            TransactionRow second = tx("1", "0002", 1);
            List<TransactionRow> rows = new AbstractList<>() {
                @Override
                public TransactionRow get(int i) {
                    if (i == 1) {
                        throw new OutOfMemoryError("simulated: second transaction set");
                    }
                    return first;
                }

                @Override
                public int size() {
                    return 2;
                }
            };
            assertThrows(OutOfMemoryError.class, () -> s.consumeFile(
                TestRows.file(FILE_A, "inbox", "abc", FileStatus.CONSUMED, 2), rows,
                Map.of(first.elementKey(), graph("0001"), second.elementKey(), graph("0002")),
                Map.of(first.elementKey(), Map.of("payerName", new EntityGraph.Value("payerName", "string", "P", null, null)))));
            assertEquals(0, s.count(), "the first row was rolled back");
            assertEquals(0, s.entityCount(first.elementKey()), "and its graph with it");
            assertTrue(s.dimsFor(first.elementKey()).isEmpty(), "and its dimensions");
            assertEquals(0, s.fileCount());
            assertTrue(s.insertTransaction(tx("1", "0003", 2)), "the connection is usable again");
            assertEquals(1, s.count(), "and back in autocommit");
        }
    }

    @Test
    void insertTransactionWithGraphRollsBackOnAnError(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir)) {
            TransactionRow row = tx("1", "0001", 0);
            assertThrows(OutOfMemoryError.class, () -> s.insertTransaction(row, dyingGraph(graph("0001"))));
            assertEquals(0, s.count(), "no row without its graph");
            assertEquals(0, s.entityCount(row.elementKey()), "no half graph");
            assertTrue(s.insertTransaction(row, graph("0001")), "the same row goes in cleanly afterwards");
            assertEquals(1, s.count());
        }
    }

    @Test
    void replaceGraphKeepsTheOldGraphWhenAnErrorStrikesMidway(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir)) {
            TransactionRow row = tx("1", "0001", 0);
            assertTrue(s.insertTransaction(row, graph("0001")));
            TransactionRow stored = s.byElementKey(row.elementKey()).orElseThrow();
            long before = s.entityCount(row.elementKey());
            Map<String, Object> document = s.documentFor(row.elementKey());

            assertThrows(OutOfMemoryError.class, () ->
                s.replaceGraph(stored, "schema:table:x12.other", dyingGraph(graph("9999")), Map.of()));
            assertEquals(before, s.entityCount(row.elementKey()), "the old graph was not deleted");
            assertEquals(document, s.documentFor(row.elementKey()), "and still reads the same");
            assertEquals(SCHEMA_835, s.byElementKey(row.elementKey()).orElseThrow().schemaId(), "schema id not rebound");
        }
    }

    @Test
    void takeRollsBackOnAnErrorInsteadOfLeavingRowsLeasedToNobody(@TempDir Path dir) throws Exception {
        String db = dir.resolve("buffer.db").toString();
        try (BufferStore s = open(dir)) {
            s.insertTransaction(tx("1", "0001", 0));
            s.insertTransaction(tx("1", "0002", 1));
        }
        // Reading the leased rows back runs after they were marked in_flight.
        try (Connection real = DriverManager.getConnection("jdbc:sqlite:" + db)) {
            Connection dying = FailingConnection.wrap(real, sql -> sql.startsWith("SELECT " + BufferStore.TX_COLS));
            LeaseManager leases = new LeaseManager(dying, new MutableClock(BASE));
            assertThrows(OutOfMemoryError.class, () -> leases.take(null, 10, Duration.ofMinutes(5)));
            assertTrue(real.getAutoCommit(), "autocommit restored after the rollback");
        }
        try (BufferStore s = open(dir)) {
            assertEquals(2, s.count(Status.NEW), "nothing was left in_flight");
            assertEquals(2, s.take(null, 10, Duration.ofMinutes(5)).transactions().size(), "both still drainable");
        }
    }

    @Test
    void migrationRollsBackOnAnErrorInsteadOfCommittingHalfOfIt(@TempDir Path dir) throws Exception {
        String db = dir.resolve("legacy.db").toString();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE transactions (id INTEGER PRIMARY KEY, mapped_json TEXT NOT NULL)");
        }
        // The ALTER has run by the time the version is read.
        try (Connection real = DriverManager.getConnection("jdbc:sqlite:" + db)) {
            Connection dying = FailingConnection.wrap(real, sql -> sql.equals("PRAGMA user_version"));
            assertThrows(OutOfMemoryError.class, () -> BufferStore.migrate(dying));
        }
        assertTrue(columns(db).contains("mapped_json"), "the dropped column came back with the rollback");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db)) {
            BufferStore.migrate(c);
        }
        assertTrue(!columns(db).contains("mapped_json"), "a clean retry migrates");
    }

    private static List<String> columns(String db) throws Exception {
        List<String> out = new ArrayList<>();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(transactions)")) {
            while (rs.next()) {
                out.add(rs.getString("name"));
            }
        }
        return out;
    }
}

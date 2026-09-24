package com.zerobias.module.x12.buffer;

import com.zerobias.module.x12.buffer.TestRows.MutableClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.zerobias.module.x12.buffer.TestRows.BASE;
import static com.zerobias.module.x12.buffer.TestRows.FILE_A;
import static com.zerobias.module.x12.buffer.TestRows.FILE_B;
import static com.zerobias.module.x12.buffer.TestRows.SCHEMA_835;
import static com.zerobias.module.x12.buffer.TestRows.SCHEMA_837P;
import static com.zerobias.module.x12.buffer.TestRows.file;
import static com.zerobias.module.x12.buffer.TestRows.insert;
import static com.zerobias.module.x12.buffer.TestRows.key;
import static com.zerobias.module.x12.buffer.TestRows.tx;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Buffer behavior against a real (temp-file) SQLite database (DESIGN §8): both tables,
 * the atomic consume unit, dedup by element key and by checksum, the rename-failed
 * marker, browse/count/distinct, and the metrics the health payload draws on. Lease
 * mechanics are in {@link LeaseTest}; retention in {@link RetentionSweeperTest}.
 */
class BufferStoreTest {

    private BufferStore open(Path dir, MutableClock clock) throws Exception {
        return new BufferStore(dir.resolve("buffer.db").toString(), false, clock);
    }

    @Test
    void schemaBootstrapsAndReopens(@TempDir Path dir) throws Exception {
        // CREATE ... IF NOT EXISTS on both tables AND both index sets must survive a second open.
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            assertEquals(0, s.count());
            assertEquals(0, s.fileCount());
        }
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            assertEquals(0, s.count());
        }
    }

    @Test
    void insertDedupsOnElementKeyAndRoundTrips(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            TransactionRow row = tx("1", "0001", 0);
            assertEquals(FILE_A + ":000000001:1:0001", row.elementKey(), "fixture key = <fileId>:<ISA13>:<GS06>:<ST02>");
            assertTrue(insert(s, row), "first insert");
            assertFalse(insert(s, row), "a taken element key is reported, not inserted (consumeFile rolls back on it)");
            assertEquals(1, s.count());
            assertEquals(1, s.count(Status.NEW));

            TransactionRow got = s.byElementKey(row.elementKey()).orElseThrow();
            assertTrue(got.id() > 0);
            assertEquals("inbox", got.sourceName());
            assertEquals("005010X221A1", got.gs08());
            assertEquals("835", got.transactionType());
            assertEquals("PAYERA", got.senderId());
            assertEquals("PROVIDER1", got.receiverId());
            assertEquals(BASE.minusSeconds(3600), got.interchangeAt());
            assertEquals(SCHEMA_835, got.schemaId());
            assertArrayEquals(row.rawX12(), got.rawX12());
            assertEquals(row.mappedJson(), got.mappedJson());
            assertEquals(TransactionRow.ENVELOPE_FILE, got.envelope());
            assertEquals(0, got.parserErrorCount());
            assertEquals(Status.NEW, got.status());
            assertNull(got.leaseId());
            assertTrue(s.byElementKey("nope").isEmpty());
        }
    }

    @Test
    void consumeFileIsOneUnitAndInsertsFileRowWithTransactions(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            List<TransactionRow> rows = List.of(tx("1", "0001", 0), tx("1", "0002", 0));
            assertEquals(2, s.consumeFile(file(FILE_A, "inbox", "abc123", FileStatus.CONSUMED, 2), rows));
            assertEquals(2, s.count());
            assertEquals(1, s.fileCount());
            assertEquals(1, s.fileCount(FileStatus.CONSUMED));

            FileRow f = s.fileById(FILE_A).orElseThrow();
            assertEquals("remit-a.835", f.fileName());
            assertEquals("/var/lib/x12/inbox/remit-a.835", f.filePath());
            assertEquals("/var/lib/x12/inbox/remit-a.835.done", f.currentPath());
            assertEquals("abc123", f.checksum());
            assertEquals(0, f.redeliveryCount());
            assertEquals(FileStatus.CONSUMED, f.status());
            assertEquals(2, f.transactionCount());
            assertEquals(1, f.isaCount());
            assertFalse(f.renameFailed());
            assertEquals(BASE, f.consumedAt());
        }
    }

    @Test
    void consumeFileRejectsACollidingElementKeyAndWritesNothing(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            List<TransactionRow> rows = List.of(tx("1", "0001", 0), tx("1", "0002", 0), tx("1", "0002", 0));
            DuplicateElementKeyException e = assertThrows(DuplicateElementKeyException.class,
                () -> s.consumeFile(file(FILE_A, "inbox", "abc123", FileStatus.CONSUMED, 3), rows));
            assertTrue(e.getMessage().startsWith(DuplicateElementKeyException.KIND + ": "), e.getMessage());
            assertEquals(0, s.count(), "no transaction row survives");
            assertEquals(0, s.fileCount(), "no files row either");
        }
    }

    @Test
    void consumeFileRollsBackOnAnErrorAndNeverCommitsPartialWork(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            // The first row is inserted, then iteration dies with an Error (as an OOM would).
            List<TransactionRow> rows = new AbstractList<>() {
                @Override
                public TransactionRow get(int i) {
                    if (i == 1) {
                        throw new OutOfMemoryError("simulated");
                    }
                    return tx("1", "000" + (i + 1), 0);
                }

                @Override
                public int size() {
                    return 2;
                }
            };
            assertThrows(OutOfMemoryError.class,
                () -> s.consumeFile(file(FILE_A, "inbox", "abc123", FileStatus.CONSUMED, 2), rows));
            assertEquals(0, s.count(), "the row inserted before the Error was rolled back, not committed");
            assertEquals(0, s.fileCount());
            assertTrue(insert(s, tx("2", "0001", 1)), "connection back in autocommit");
            assertEquals(1, s.count());
        }
    }

    @Test
    void consumeFileRollsBackEverythingWhenTheFileRowFails(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            s.insertFile(file(FILE_A, "inbox", "abc123", FileStatus.CONSUMED, 0));
            // Same file_id again → UNIQUE violation on the files insert, AFTER the
            // transaction rows were inserted inside the same SQL transaction.
            assertThrows(SQLException.class, () ->
                s.consumeFile(file(FILE_A, "inbox", "abc123", FileStatus.CONSUMED, 1), List.of(tx("9", "0009", 0))));
            assertEquals(0, s.count(), "transaction rows rolled back with the failed file row");
            assertEquals(1, s.fileCount());
            // and the connection is usable afterwards (autocommit restored)
            assertTrue(insert(s, tx("2", "0001", 1)));
        }
    }

    @Test
    void checksumLookupDrivesDuplicateDetection(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            assertTrue(s.findFileByChecksum("abc123").isEmpty());
            s.consumeFile(file(FILE_A, "inbox", "abc123", FileStatus.CONSUMED, 1), List.of(tx("1", "0001", 0)));
            Optional<FileRow> dup = s.findFileByChecksum("abc123");
            assertTrue(dup.isPresent());
            assertEquals(FileStatus.CONSUMED, dup.get().status());
            // A re-dropped copy under a new name is recorded as `duplicate` (files.status).
            s.insertFile(file("/var/lib/x12/inbox/remit-a-copy.835@0a1b2c3d4e5f", "inbox", "abc123", FileStatus.DUPLICATE, 0));
            assertEquals(1, s.fileCount(FileStatus.DUPLICATE));
            assertEquals(1, s.fileCount("inbox", FileStatus.DUPLICATE));
            assertEquals(0, s.fileCount("other", FileStatus.DUPLICATE));
        }
    }

    @Test
    void checksumLookupPrefersConsumedThenDuplicateThenNewestError(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            s.insertFile(file("/in/a.835@111111111111", "inbox", "sum", FileStatus.ERROR, 0));
            s.insertFile(file("/in/b.835@111111111111", "inbox", "sum", FileStatus.ERROR, 0));
            assertEquals("/in/b.835@111111111111", s.findFileByChecksum("sum").orElseThrow().fileId(), "newest error");
            s.insertFile(file("/in/c.835@111111111111", "inbox", "sum", FileStatus.DUPLICATE, 0));
            assertEquals("/in/c.835@111111111111", s.findFileByChecksum("sum").orElseThrow().fileId(), "duplicate beats error");
            s.consumeFile(file("/in/d.835@111111111111", "inbox", "sum", FileStatus.CONSUMED, 1),
                List.of(tx("/in/d.835@111111111111", "inbox", "1", "0001", 0, "005010X221A1", "835", SCHEMA_835, "P")));
            assertEquals("/in/d.835@111111111111", s.findFileByChecksum("sum").orElseThrow().fileId(), "consumed wins");
        }
    }

    @Test
    void deleteFileAndBumpRedelivery(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            s.insertFile(file(FILE_A, "inbox", "abc123", FileStatus.ERROR, 0));
            assertTrue(s.deleteFile(FILE_A));
            assertFalse(s.deleteFile(FILE_A), "already gone");
            assertTrue(s.fileById(FILE_A).isEmpty());

            s.consumeFile(file(FILE_A, "inbox", "abc123", FileStatus.CONSUMED, 1), List.of(tx("1", "0001", 0)));
            assertTrue(s.bumpRedelivery(FILE_A, null));
            FileRow f = s.fileById(FILE_A).orElseThrow();
            assertEquals(1, f.redeliveryCount());
            assertEquals("/var/lib/x12/inbox/remit-a.835.done", f.currentPath(), "null keeps the path");
            assertTrue(s.bumpRedelivery(FILE_A, "/var/lib/x12/inbox/remit-a.835.1.done"));
            f = s.fileById(FILE_A).orElseThrow();
            assertEquals(2, f.redeliveryCount());
            assertEquals(FileStatus.CONSUMED, f.status(), "status untouched");
            assertEquals("/var/lib/x12/inbox/remit-a.835.1.done", f.currentPath());
            assertFalse(s.bumpRedelivery("/nope@000000000000", null));
            assertEquals(1, s.count(), "transactions untouched");
        }
    }

    @Test
    void markRenameFailedResetsCurrentPathAndKeepsRows(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            s.consumeFile(file(FILE_A, "inbox", "abc123", FileStatus.CONSUMED, 1), List.of(tx("1", "0001", 0)));
            assertTrue(s.markRenameFailed(FILE_A));
            assertFalse(s.markRenameFailed("/nope"));
            FileRow f = s.fileById(FILE_A).orElseThrow();
            assertTrue(f.renameFailed());
            assertEquals("/var/lib/x12/inbox/remit-a.835", f.currentPath(), "bytes are still at the discovery path");
            assertEquals(1, s.count(), "transactions untouched");

            // A later rename that succeeds (a redelivery) must clear the flag, not just move the path.
            assertTrue(s.markRenamed(FILE_A, "/var/lib/x12/inbox/remit-a.835.done"));
            f = s.fileById(FILE_A).orElseThrow();
            assertEquals("/var/lib/x12/inbox/remit-a.835.done", f.currentPath());
            assertFalse(f.renameFailed(), "rename_failed cleared");
            assertFalse(s.markRenamed("/nope", "/x"));
        }
    }

    @Test
    void errorFileRowStandsAlone(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            s.insertFile(file("/var/lib/x12/inbox/bad.835", "inbox", "deadbeef", FileStatus.ERROR, 0));
            FileRow f = s.fileById("/var/lib/x12/inbox/bad.835").orElseThrow();
            assertEquals(FileStatus.ERROR, f.status());
            assertEquals("boom", f.errorMessage());
            assertNull(f.consumedAt());
            assertEquals(1, s.fileCount("inbox", FileStatus.ERROR));
            assertEquals(0, s.count());
        }
    }

    @Test
    void fileRowsBrowseNewestFirstWithScope(@TempDir Path dir) throws Exception {
        MutableClock clock = new MutableClock(BASE);
        try (BufferStore s = open(dir, clock)) {
            s.insertFile(new FileRow(0, FILE_A, FileRow.pathOf(FILE_A), "remit-a.835", "inbox", FileRow.pathOf(FILE_A) + ".done",
                1, "c1", BASE, BASE, BASE, FileStatus.CONSUMED, 1, 1, null, false, 0));
            s.insertFile(new FileRow(0, FILE_B, FileRow.pathOf(FILE_B), "claims-b.837", "payer-b", FileRow.pathOf(FILE_B) + ".done",
                1, "c2", BASE, BASE.plusSeconds(10), BASE.plusSeconds(10), FileStatus.CONSUMED, 1, 3, null, false, 0));
            List<FileRow> all = s.fileRows(null, 10, 0);
            assertEquals(List.of(FILE_B, FILE_A), all.stream().map(FileRow::fileId).toList());
            assertEquals(1, s.fileRows("source_name = 'payer-b'", 10, 0).size());
            assertEquals(1, s.fileCount("source_name = 'inbox'"));
            assertEquals(2, s.fileCount((String) null));
            assertEquals(1, s.fileRows(null, 1, 1).size(), "offset paging");
        }
    }

    @Test
    void searchCountDistinctAndScopes(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            insert(s, tx("1", "0001", 0));
            insert(s, tx("1", "0002", 1));
            insert(s, tx(FILE_B, "payer-b", "7", "0001", 2, "005010X222A1", "837P", SCHEMA_837P, "SUBMIT1"));

            List<TransactionRow> newest = s.search(null, 10, 0);
            assertEquals(3, newest.size());
            assertEquals("837P", newest.get(0).transactionType(), "newest first");
            assertEquals(1, s.search(null, 1, 2).size(), "offset paging");
            assertEquals(2, s.countWhere("transaction_type = '835'"));
            assertEquals(3, s.countWhere(null));

            assertEquals(List.of("835", "837P"), s.distinctValues("transaction_type"));
            assertEquals(List.of("005010X221A1", "005010X222A1"), s.distinctValues("gs08"));
            assertEquals(List.of("PAYERA", "SUBMIT1"), s.distinctValues("sender_id"));
            assertEquals(List.of("inbox", "payer-b"), s.distinctValues("source_name"));
            assertEquals(List.of(FILE_B, FILE_A), s.distinctValues("file_id"), "sorted");
            assertEquals(Map.of("005010X221A1", 2L), s.distinctCounts("gs08", "transaction_type = '835'"),
                "scoped: the guides of /by-type/<TS> with their sizes");
            assertEquals(List.of(Map.entry("PAYERA", 2L), Map.entry("SUBMIT1", 1L)),
                List.copyOf(s.distinctCounts("sender_id", null).entrySet()), "ascending, with counts");
            assertTrue(s.exists("sender_id = 'SUBMIT1'"));
            assertFalse(s.exists("sender_id = 'NOBODY'"));
            assertTrue(s.exists(null));
            assertThrows(IllegalArgumentException.class, () -> s.distinctValues("mapped_json"),
                "distinct column is allow-listed");
            assertThrows(IllegalArgumentException.class, () -> s.distinctCounts("receiver_id", null),
                "an unindexed column would scan the table");
        }
    }

    @Test
    void deleteStatementsAreBoundedToABatch(@TempDir Path dir) throws Exception {
        int n = BufferStore.DELETE_BATCH * 2 + 7;
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            TestRows.seedAcked(s, FILE_A, n, 16);
            assertEquals(BufferStore.DELETE_BATCH, s.deleteAckedOlderThanMillis(Long.MAX_VALUE, BufferStore.DELETE_BATCH),
                "one statement removes one batch, so the store's lock is free again between batches");
            assertEquals(n - BufferStore.DELETE_BATCH, s.count(Status.ACKED));
            assertEquals(n - BufferStore.DELETE_BATCH, s.purge(Duration.ZERO), "purge loops until nothing is left");
            assertEquals(0, s.count());
        }
    }

    @Test
    void recastableExcludesLeasedRowsAndUpdateMappingGuardsInFlight(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            insert(s, tx("1", "0001", 0));
            insert(s, tx("1", "0002", 1));
            Lease lease = s.takeWhere(null, 1, Duration.ofMinutes(5)); // leases 0001 (oldest)
            assertNotNull(lease.leaseId());

            List<TransactionRow> rc = s.recastable(null, 10);
            assertEquals(1, rc.size());
            assertEquals("0002", rc.get(0).stControl());
            assertEquals(1, s.recastable("transaction_type = '835'", 10).size());

            long leasedId = lease.transactions().get(0).id();
            assertFalse(s.updateMapping(leasedId, "schema:x", "{}"), "in_flight row is never rewritten");
            assertTrue(s.updateMapping(rc.get(0).id(), "schema:table:x12.005010X221A1.835", "{\"v\":2}"));
            assertEquals("{\"v\":2}", s.byElementKey(rc.get(0).elementKey()).orElseThrow().mappedJson());

            TransactionRow copy = rc.get(0).withMapping("schema:y", "{\"v\":3}");
            assertEquals("schema:y", copy.schemaId());
            assertEquals(rc.get(0).elementKey(), copy.elementKey());
        }
    }

    @Test
    void healthMetricsAndDbSize(@TempDir Path dir) throws Exception {
        MutableClock clock = new MutableClock(BASE.plusSeconds(1000));
        try (BufferStore s = open(dir, clock)) {
            assertTrue(s.lastConsumedMillis().isEmpty());
            assertTrue(s.oldestUnackedSeconds().isEmpty());
            assertTrue(s.dbSizeBytes() > 0);
            assertTrue(s.walBytes() >= 0);

            s.consumeFile(file(FILE_A, "inbox", "c1", FileStatus.CONSUMED, 2),
                List.of(tx("1", "0001", 0), tx("1", "0002", 600)));
            assertEquals(BASE.toEpochMilli(), s.lastConsumedMillis().getAsLong());
            assertEquals(1000L, s.oldestUnackedSeconds().getAsLong());

            // ack the oldest → oldestUnacked tracks the youngest remaining
            s.ack(s.takeWhere(null, 1, Duration.ofMinutes(5)).leaseId(), null);
            assertEquals(400L, s.oldestUnackedSeconds().getAsLong());
        }
    }

    /**
     * Take, backlog, the oldest-unacked probe and both retention deletes must reach their
     * rows through an index: acked rows pile up for {@code maxAge}, and a full scan per take
     * grows with them. EXPLAIN QUERY PLAN names the index; a bare "SCAN transactions" is
     * the regression.
     */
    @Test
    void hotQueriesUseIndexesNotTableScans(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("buffer.db");
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            insert(s, tx("1", "0001", 0));
        }
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db)) {
            assertEquals("transactions_unacked", indexUsed(c, LeaseManager.candidateSql(null)), "take");
            assertEquals("transactions_unacked", indexUsed(c, LeaseManager.BACKLOG_SQL), "backlog");
            assertEquals("transactions_unacked", indexUsed(c, BufferStore.OLDEST_UNACKED_SQL), "oldestUnacked");
            assertEquals("transactions_acked", indexUsed(c, BufferStore.DELETE_ACKED_OLDER_THAN_SQL), "purge / maxAge");
            assertEquals("transactions_acked", indexUsed(c, BufferStore.DELETE_OLDEST_ACKED_SQL), "maxBytes eviction");
        }
    }

    /**
     * The emergent tree's facet queries (children, their sizes, and the existence check that
     * resolves an id) read a covering index in order: never the table, never a temp sort.
     */
    @Test
    void facetQueriesReadAnIndexNeverTheTable(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("buffer.db");
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            insert(s, tx("1", "0001", 0));
        }
        String type = "transaction_type = '835'";
        String typeGuide = type + " AND gs08 = '005010X221A1'";
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db)) {
            Map<String, String> expected = new LinkedHashMap<>();
            expected.put(BufferStore.distinctSql("transaction_type"), "transactions_type");
            expected.put(BufferStore.distinctSql("gs08"), "transactions_gs08");
            expected.put(BufferStore.distinctSql("sender_id"), "transactions_sender");
            expected.put(BufferStore.distinctSql("source_name"), "transactions_source");
            expected.put(BufferStore.distinctSql("file_id"), "transactions_file");
            expected.put(BufferStore.distinctCountsSql("gs08", type), "transactions_type");
            expected.put(BufferStore.distinctCountsSql("gs08", null), "transactions_gs08");
            expected.put(BufferStore.distinctCountsSql("sender_id", null), "transactions_sender");
            expected.put(BufferStore.distinctCountsSql("source_name", null), "transactions_source");
            expected.put(BufferStore.existsSql(type), "transactions_type");
            expected.put(BufferStore.existsSql(typeGuide), "transactions_type");
            expected.put(BufferStore.existsSql("gs08 = '005010X221A1'"), "transactions_gs08");
            expected.put(BufferStore.existsSql("sender_id = 'PAYERA'"), "transactions_sender");
            expected.put(BufferStore.existsSql("source_name = 'inbox'"), "transactions_source");
            expected.put(BufferStore.countSql(typeGuide), "transactions_type");
            expected.put(BufferStore.countSql("gs08 = '005010X221A1'"), "transactions_gs08");
            expected.put(BufferStore.countSql("sender_id = 'PAYERA'"), "transactions_sender");
            expected.put(BufferStore.countSql("source_name = 'inbox'"), "transactions_source");
            for (Map.Entry<String, String> q : expected.entrySet()) {
                List<String> plan = plan(c, q.getKey());
                assertEquals(q.getValue(), indexUsed(c, q.getKey()), q.getKey() + " -> " + plan);
                for (String step : plan) {
                    assertFalse(step.matches("SCAN transactions( |$)(?!USING).*") || step.contains("TEMP B-TREE"),
                        q.getKey() + " -> " + plan);
                }
            }
        }
    }

    private static List<String> plan(Connection c, String sql) throws SQLException {
        List<String> plan = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("EXPLAIN QUERY PLAN " + sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                plan.add(rs.getString("detail"));
            }
        }
        return plan;
    }

    /** The index the plan's access to {@code transactions} goes through; fails on a full scan. */
    private static String indexUsed(Connection c, String sql) throws SQLException {
        List<String> plan = plan(c, sql);
        for (String step : plan) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:SCAN|SEARCH) transactions USING (?:COVERING )?INDEX (\\w+)").matcher(step);
            if (m.find()) {
                return m.group(1);
            }
        }
        throw new AssertionError("no index used by: " + sql + " -> " + plan);
    }

    @Test
    void builderBuildsANewUnleasedFileEnvelopeRow() {
        TransactionRow r = TransactionRow.builder().fileId("/f").elementKey("/f:9:1:2").gsControl("1").stControl("2")
            .receivedAt(BASE).gs08("x").transactionType("835").schemaId("s").rawX12(new byte[0]).mappedJson("{}")
            .envelope(null).build();
        assertEquals("/f:9:1:2", r.elementKey());
        assertEquals(0, r.id(), "the store assigns the id");
        assertEquals(Status.NEW, r.status());
        assertNull(r.leaseId());
        assertEquals(TransactionRow.ENVELOPE_FILE, r.envelope());
    }
}

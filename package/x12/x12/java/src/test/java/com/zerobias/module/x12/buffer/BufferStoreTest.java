package com.zerobias.module.x12.buffer;

import com.zerobias.module.x12.buffer.TestRows.MutableClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static com.zerobias.module.x12.buffer.TestRows.BASE;
import static com.zerobias.module.x12.buffer.TestRows.FILE_A;
import static com.zerobias.module.x12.buffer.TestRows.FILE_B;
import static com.zerobias.module.x12.buffer.TestRows.SCHEMA_835;
import static com.zerobias.module.x12.buffer.TestRows.SCHEMA_837P;
import static com.zerobias.module.x12.buffer.TestRows.file;
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
    void insertTransactionDedupsOnElementKeyAndRoundTrips(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            TransactionRow row = tx("1", "0001", 0);
            assertEquals(FILE_A + ":000000001:1:0001", row.elementKey(), "element key = <fileId>:<ISA13>:<GS06>:<ST02>");
            assertTrue(s.insertTransaction(row), "first insert");
            assertFalse(s.insertTransaction(row), "duplicate element key dropped (ON CONFLICT DO NOTHING)");
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
            int inserted = s.consumeFile(file(FILE_A, "inbox", "abc123", FileStatus.CONSUMED, 2), rows);
            assertEquals(2, inserted);
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
    void elementKeyCollisionInsideAFileRollsTheWholeFileBack(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            TransactionRow first = tx("1", "0002", 0);
            TransactionRow clash = tx("1", "0002", 0);
            java.util.Map<String, List<com.zerobias.module.x12.materializer.EntityGraph.Entity>> graphs =
                java.util.Map.of(first.elementKey(), TestRows.graph(SCHEMA_835, "0002"));
            DuplicateElementKeyException e = assertThrows(DuplicateElementKeyException.class, () ->
                s.consumeFile(file(FILE_A, "inbox", "abc123", FileStatus.CONSUMED, 3),
                    List.of(tx("1", "0001", 0), first, clash), graphs, java.util.Map.of()));
            assertTrue(e.getMessage().contains(first.elementKey()), e.getMessage());
            assertEquals(0, s.count(), "no transaction row survives");
            assertEquals(0, s.entityCount(first.elementKey()), "no graph survives");
            assertEquals(0, s.fileCount(), "no files row");
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
            assertTrue(s.insertTransaction(tx("2", "0001", 1)));
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

            assertTrue(s.updateFilePath(FILE_A, "/archive/remit-a.835"));
            assertEquals("/archive/remit-a.835", s.fileById(FILE_A).orElseThrow().currentPath());
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
            assertEquals(1, s.countFilesWhere("source_name = 'inbox'"));
            assertEquals(2, s.countFilesWhere(null));
            assertEquals(1, s.fileRows(null, 1, 1).size(), "offset paging");
        }
    }

    @Test
    void searchCountDistinctAndScopes(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            s.insertTransaction(tx("1", "0001", 0));
            s.insertTransaction(tx("1", "0002", 1));
            s.insertTransaction(tx(FILE_B, "payer-b", "7", "0001", 2, "005010X222A1", "837P", SCHEMA_837P, "SUBMIT1"));

            List<TransactionRow> newest = s.search(null, 10);
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
            assertEquals(List.of("005010X221A1"), s.distinctValues("gs08", "transaction_type = '835'"),
                "scoped distinct for /by-type/<TS>/<GS08>");
            assertThrows(IllegalArgumentException.class, () -> s.distinctValues("mapped_json"),
                "distinct column is allow-listed");
        }
    }

    @Test
    void recastableExcludesLeasedRowsAndUpdateMappingGuardsInFlight(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            s.insertTransaction(tx("1", "0001", 0));
            s.insertTransaction(tx("1", "0002", 1));
            Lease lease = s.take(null, 1, Duration.ofMinutes(5)); // leases 0001 (oldest)
            assertNotNull(lease.leaseId());

            List<TransactionRow> rc = s.recastable(null, 10);
            assertEquals(1, rc.size());
            assertEquals("0002", rc.get(0).stControl());
            assertEquals(1, s.recastable("transaction_type = '835'", 10).size());

            long leasedId = lease.transactions().get(0).id();
            assertFalse(s.updateSchemaId(leasedId, "schema:x"), "in_flight row is never rewritten");
            assertTrue(s.updateSchemaId(rc.get(0).id(), "schema:table:x12.005010X221A1.835"));
            assertEquals("schema:table:x12.005010X221A1.835",
                s.byElementKey(rc.get(0).elementKey()).orElseThrow().schemaId());

            TransactionRow copy = rc.get(0).withSchemaId("schema:y");
            assertEquals("schema:y", copy.schemaId());
            assertEquals(rc.get(0).elementKey(), copy.elementKey());
        }
    }

    @Test
    void healthMetricsAndDbSize(@TempDir Path dir) throws Exception {
        MutableClock clock = new MutableClock(BASE.plusSeconds(1000));
        try (BufferStore s = open(dir, clock)) {
            assertTrue(s.lastReceivedMillis().isEmpty());
            assertTrue(s.lastConsumedMillis().isEmpty());
            assertTrue(s.oldestUnackedSeconds().isEmpty());
            assertTrue(s.dbSizeBytes() > 0);
            assertTrue(s.walBytes() >= 0);

            s.consumeFile(file(FILE_A, "inbox", "c1", FileStatus.CONSUMED, 2),
                List.of(tx("1", "0001", 0), tx("1", "0002", 600)));
            assertEquals(BASE.plusSeconds(600).toEpochMilli(), s.lastReceivedMillis().getAsLong());
            assertEquals(BASE.toEpochMilli(), s.lastConsumedMillis().getAsLong());
            assertEquals(1000L, s.oldestUnackedSeconds().getAsLong());

            // ack the oldest → oldestUnacked tracks the youngest remaining
            s.ack(s.take(null, 1, Duration.ofMinutes(5)).leaseId(), null);
            assertEquals(400L, s.oldestUnackedSeconds().getAsLong());
        }
    }

    @Test
    void builderRequiresKeyPartsForDerivation() {
        assertThrows(IllegalStateException.class, () -> TransactionRow.builder().fileId("/f").deriveElementKey());
        assertThrows(IllegalStateException.class,
            () -> TransactionRow.builder().fileId("/f").gsControl("1").stControl("2").deriveElementKey(),
            "ISA13 is part of the key");
        TransactionRow r = TransactionRow.builder().fileId("/f").isaControl("9").gsControl("1").stControl("2")
            .deriveElementKey()
            .receivedAt(BASE).gs08("x").transactionType("835").schemaId("s").rawX12(new byte[0])
            .build();
        assertEquals("/f:9:1:2", r.elementKey());
        assertEquals(Status.NEW, r.status());
        assertEquals(TransactionRow.ENVELOPE_FILE, r.envelope());
    }
}

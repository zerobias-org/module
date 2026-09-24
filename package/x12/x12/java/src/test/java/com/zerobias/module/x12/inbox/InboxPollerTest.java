package com.zerobias.module.x12.inbox;

import com.zerobias.module.x12.ModuleRuntimeConfig;
import com.zerobias.module.x12.PollerHandle.RescanResult;
import com.zerobias.module.x12.SourceConfig;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.FileRow;
import com.zerobias.module.x12.buffer.FileStatus;
import com.zerobias.module.x12.buffer.RetentionConfig;
import com.zerobias.module.x12.buffer.RetentionSweeper;
import com.zerobias.module.x12.buffer.Status;
import com.zerobias.module.x12.buffer.TestRows;
import com.zerobias.module.x12.buffer.TestRows.MutableClock;
import com.zerobias.module.x12.buffer.TransactionRow;
import com.zerobias.module.x12.health.PollerStatus;
import com.zerobias.module.x12.materializer.StructureIndex;
import com.zerobias.module.x12.materializer.StructureResolver;
import com.zerobias.module.x12.materializer.TransactionJson;
import com.zerobias.module.x12.parser.Fixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * The poller against a real temp directory and a real SQLite buffer (DESIGN §4.2): stability
 * window, skip rules, consume → commit → {@code .done}, duplicate → {@code .done} without
 * rows, parse failure → {@code .error}, bare ST files, backpressure, rescan counts, health.
 */
class InboxPollerTest {

    private static final Instant T0 = Instant.parse("2026-09-22T12:00:00Z");

    private BufferStore buffer;
    private X12InboxPollerFactory.Handle handle;

    @AfterEach
    void tearDown() throws Exception {
        if (handle != null) {
            handle.close();
        }
        if (buffer != null) {
            buffer.close();
        }
    }

    private Path inbox(Path dir) throws Exception {
        return Files.createDirectories(dir.resolve("inbox"));
    }

    private ModuleRuntimeConfig config(Path inbox, int stableForSec, boolean allowBare) {
        return new ModuleRuntimeConfig(
            List.of(new SourceConfig("inbox", inbox.toString(), "*", 1, stableForSec)),
            ".done", ".error", false, RetentionConfig.none(), allowBare, ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES);
    }

    private X12InboxPollerFactory.Handle open(Path dir, ModuleRuntimeConfig cfg, MutableClock clock,
                                              RetentionSweeper sweeper) throws Exception {
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false, clock);
        handle = X12InboxPollerFactory.start(cfg, buffer, sweeper, clock, false);
        return handle;
    }

    private static Path drop(Path inbox, String name, byte[] bytes) throws Exception {
        return Files.write(inbox.resolve(name), bytes);
    }

    private static String key(String fileId, String isa13, String gs06, String st02) {
        return TransactionJson.elementKey(fileId, isa13, gs06, st02);
    }

    @Test
    void stableFileIsConsumedCommittedThenRenamedDone(@TempDir Path dir) throws Exception {
        Path inbox = inbox(dir);
        MutableClock clock = new MutableClock(T0);
        open(dir, config(inbox, 0, false), clock, null);
        Path f = drop(inbox, "remit.835", Fixtures.bytes(Fixtures.F835));
        String fileId = InboxFixtures.fileId(f, Fixtures.bytes(Fixtures.F835));
        assertEquals(f + "@" + InboxFixtures.sha256(Fixtures.bytes(Fixtures.F835)).substring(0, 12), fileId,
            "fileId = <absolute path at discovery>@<first 12 hex of sha256>");

        RescanResult r = handle.rescan(null);
        assertEquals(new RescanResult(1, 1, 1, 0), r);
        assertFalse(Files.exists(f), "original renamed");
        assertTrue(Files.exists(inbox.resolve("remit.835.done")), ".done after commit");

        FileRow file = buffer.fileById(fileId).orElseThrow();
        assertEquals(FileStatus.CONSUMED, file.status());
        assertEquals(f.toString(), file.filePath());
        assertEquals(f + ".done", file.currentPath());
        assertEquals(0, file.redeliveryCount());
        assertEquals("remit.835", file.fileName());
        assertEquals("inbox", file.sourceName());
        assertEquals(1, file.isaCount());
        assertEquals(1, file.transactionCount());
        assertEquals(InboxFixtures.sha256(Fixtures.bytes(Fixtures.F835)), file.checksum());
        assertEquals(T0, file.discoveredAt());
        assertEquals(T0, file.consumedAt());
        assertFalse(file.renameFailed());

        assertEquals(1, buffer.count());
        TransactionRow row = buffer.byElementKey(key(fileId, "000000101", "101", "0001")).orElseThrow();
        assertEquals(Status.NEW, row.status());
        assertEquals("005010X221A1", row.gs08());
        assertEquals("835", row.transactionType());
        assertEquals("schema:table:x12.005010X221A1.835", row.schemaId());
        assertEquals("EXAMPLEPAYER", row.senderId());
        assertEquals("EXAMPLEPROV", row.receiverId());
        assertEquals("000000101", row.isaControl());
        assertEquals("101", row.gsControl());
        assertEquals("0001", row.stControl());
        assertEquals(T0, row.receivedAt());
        assertEquals(Instant.parse("2026-09-22T12:00:00Z"), row.interchangeAt());
        assertEquals(TransactionRow.ENVELOPE_FILE, row.envelope());
        assertEquals(0, row.parserErrorCount());
        String raw = new String(row.rawX12(), StandardCharsets.UTF_8);
        assertTrue(raw.startsWith("ISA*00*") && raw.contains("ST*835*0001~") && raw.endsWith("IEA*1*000000101~\n"), raw);
        String json = row.mappedJson();
        assertTrue(json.contains("\"elementKey\":\"" + key(fileId, "000000101", "101", "0001") + "\""), json);
        assertTrue(json.contains("\"transactionType\":\"835\""), json);
        assertTrue(json.contains("\"envelope\":\"file\""), json);
        assertTrue(json.contains("\"parserErrorCount\":0"), json);
        assertTrue(json.contains("\"interchangeDate\":\"2026-09-22T12:00:00Z\""), json);
        assertTrue(json.contains("\"clp04\":220.00"), json);
        assertTrue(json.contains("\"clp02\":\"1\""), json);

        // Health view.
        assertEquals(T0, handle.lastScan().orElseThrow());
        assertEquals(T0, handle.lastConsumed().orElseThrow());
        assertFalse(handle.backpressure());
        PollerStatus.SourceStatus s = handle.sources().get(0);
        assertEquals("inbox", s.name());
        assertEquals(inbox.toString(), s.path());
        assertTrue(s.writable());
        assertEquals(0, s.pending());
        assertEquals(0, s.errored());
        assertEquals(1, s.pollIntervalSec());
        assertEquals(T0, s.lastScanStarted());
        assertEquals(T0, s.lastScanCompleted());
        assertEquals(0, s.consecutiveFailures());

        // Nothing new: the .done is skipped, counts are zero.
        assertEquals(new RescanResult(0, 0, 0, 0), handle.rescan("inbox"));
    }

    @Test
    void growingFileWaitsForTheStabilityWindow(@TempDir Path dir) throws Exception {
        Path inbox = inbox(dir);
        MutableClock clock = new MutableClock(T0);
        open(dir, config(inbox, 60, false), clock, null);
        Path f = drop(inbox, "partial.835", Fixtures.text(Fixtures.F835).substring(0, 300).getBytes(StandardCharsets.UTF_8));

        assertEquals(new RescanResult(1, 1, 0, 0), handle.rescan(null), "first sighting starts the clock");
        assertTrue(Files.exists(f));
        assertEquals(1, handle.sources().get(0).pending());
        assertTrue(handle.lastConsumed().isEmpty());

        // Still being written: size changes → clock restarts.
        clock.advance(Duration.ofSeconds(61));
        Files.write(f, Fixtures.text(Fixtures.F835).substring(300).getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
        assertEquals(new RescanResult(1, 0, 0, 0), handle.rescan(null), "changed since last poll: not consumed");
        assertTrue(Files.exists(f));

        clock.advance(Duration.ofSeconds(30));
        assertEquals(new RescanResult(1, 0, 0, 0), handle.rescan(null), "unchanged for 30s < 60s");

        clock.advance(Duration.ofSeconds(31));
        assertEquals(new RescanResult(1, 0, 1, 0), handle.rescan(null), "unchanged for 61s >= 60s");
        assertTrue(Files.exists(inbox.resolve("partial.835.done")));
        assertEquals(1, buffer.count());
        assertEquals(T0, buffer.fileById(InboxFixtures.fileId(f, Fixtures.bytes(Fixtures.F835))).orElseThrow().discoveredAt(),
            "discoveredAt = first sighting");
        assertEquals(0, handle.sources().get(0).pending());
    }

    @Test
    void skipRulesTmpPartDotfilesAndSuffixes(@TempDir Path dir) throws Exception {
        Path inbox = inbox(dir);
        open(dir, config(inbox, 0, false), new MutableClock(T0), null);
        byte[] ok = Fixtures.bytes(Fixtures.F835);
        drop(inbox, "a.tmp", ok);
        drop(inbox, "b.part", ok);
        drop(inbox, "c.partial", ok);
        drop(inbox, "d.835.done", ok);
        drop(inbox, "e.835.error", ok);
        drop(inbox, "f.835.DONE", ok);
        drop(inbox, ".hidden.835", ok);
        Files.createDirectories(inbox.resolve("subdir.835"));
        assertEquals(new RescanResult(0, 0, 0, 0), handle.rescan(null));
        assertEquals(0, buffer.fileCount());
        assertEquals(8, Files.list(inbox).count(), "nothing renamed");
    }

    @Test
    void patternIsCaseInsensitiveGlob(@TempDir Path dir) throws Exception {
        Path inbox = inbox(dir);
        ModuleRuntimeConfig cfg = new ModuleRuntimeConfig(
            List.of(new SourceConfig("inbox", inbox.toString(), "*.{x12,835}", 1, 0)),
            ".done", ".error", false, RetentionConfig.none(), false, ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES);
        open(dir, cfg, new MutableClock(T0), null);
        drop(inbox, "REMIT.X12", Fixtures.bytes(Fixtures.F835));
        drop(inbox, "notes.txt", "hello".getBytes(StandardCharsets.UTF_8));
        assertEquals(new RescanResult(1, 1, 1, 0), handle.rescan(null));
        assertTrue(Files.exists(inbox.resolve("REMIT.X12.done")));
        assertTrue(Files.exists(inbox.resolve("notes.txt")), "non-matching file untouched");
    }

    @Test
    void duplicateChecksumIsAcknowledgedWithoutRows(@TempDir Path dir) throws Exception {
        Path inbox = inbox(dir);
        open(dir, config(inbox, 0, false), new MutableClock(T0), null);
        drop(inbox, "first.835", Fixtures.bytes(Fixtures.F835));
        assertEquals(new RescanResult(1, 1, 1, 0), handle.rescan(null));
        Path dup = drop(inbox, "redelivered.835", Fixtures.bytes(Fixtures.F835));
        assertEquals(new RescanResult(1, 1, 1, 0), handle.rescan(null), "a duplicate is acknowledged (counted as consumed)");
        assertTrue(Files.exists(inbox.resolve("redelivered.835.done")), "duplicate renamed .done");
        FileRow row = buffer.fileById(InboxFixtures.fileId(dup, Fixtures.bytes(Fixtures.F835))).orElseThrow();
        assertEquals(FileStatus.DUPLICATE, row.status());
        assertEquals(dup.toString(), row.filePath());
        assertEquals(0, row.transactionCount());
        assertTrue(row.errorMessage().startsWith("duplicate of "), row.errorMessage());
        assertEquals(1, buffer.count(), "no transactions re-ingested");
        assertEquals(2, buffer.fileCount());
        assertEquals(1, buffer.fileCount(FileStatus.DUPLICATE));
    }

    @Test
    void malformedFilesGoToErrorWithTheParserMessage(@TempDir Path dir) throws Exception {
        Path inbox = inbox(dir);
        open(dir, config(inbox, 0, false), new MutableClock(T0), null);
        Path unknown = drop(inbox, "unknown.835", Fixtures.malformed("unknown-guide-gs08.x12"));
        Path truncated = drop(inbox, "truncated.835", Fixtures.malformed("truncated-no-iea.x12"));
        Path empty = drop(inbox, "empty.835", Fixtures.malformed("empty.x12"));
        Path badSeps = drop(inbox, "badseps.835", Fixtures.malformed("bad-separators.x12"));
        assertEquals(new RescanResult(4, 4, 0, 4), handle.rescan(null));
        for (Path p : List.of(unknown, truncated, empty, badSeps)) {
            assertFalse(Files.exists(p), p + " renamed");
            assertTrue(Files.exists(Path.of(p + ".error")), p + ".error exists");
        }
        assertEquals(0, buffer.count(), "no transaction rows");
        assertEquals(4, buffer.fileCount(FileStatus.ERROR));

        FileRow u = buffer.fileById(InboxFixtures.fileId(unknown, Fixtures.malformed("unknown-guide-gs08.x12"))).orElseThrow();
        assertEquals(FileStatus.ERROR, u.status());
        assertEquals("unsupported-guide: GS08 '005010X999' is not a supported implementation guide", u.errorMessage());
        assertEquals(unknown + ".error", u.currentPath());
        assertEquals(1317, u.sizeBytes());
        assertNotNull(u.checksum());

        FileRow t = buffer.fileById(InboxFixtures.fileId(truncated, Fixtures.malformed("truncated-no-iea.x12"))).orElseThrow();
        assertTrue(t.errorMessage().startsWith("fatal:"), t.errorMessage());
        assertTrue(t.errorMessage().contains("Unable to find end of transaction"), "imsweb fatal errors included: " + t.errorMessage());

        FileRow e = buffer.fileById(InboxFixtures.fileId(empty, Fixtures.malformed("empty.x12"))).orElseThrow();
        assertTrue(e.errorMessage().startsWith("empty-file"), e.errorMessage());
        assertEquals(0, e.sizeBytes());

        assertEquals(4, handle.sources().get(0).errored());
        assertTrue(handle.lastConsumed().isEmpty());
        // Errors are never retried: the .error files are skipped by suffix next scan.
        assertEquals(new RescanResult(0, 0, 0, 0), handle.rescan(null));
    }

    @Test
    void bareTransactionSetIsConsumedOnlyWhenAllowed(@TempDir Path dir) throws Exception {
        Path inbox = inbox(dir);
        byte[] bare = Fixtures.bare835WithSt03().getBytes(StandardCharsets.UTF_8);
        open(dir, config(inbox, 0, false), new MutableClock(T0), null);
        Path refused = drop(inbox, "bare.835", bare);
        assertEquals(new RescanResult(1, 1, 0, 1), handle.rescan(null));
        assertTrue(Files.exists(Path.of(refused + ".error")));
        FileRow row = buffer.fileById(InboxFixtures.fileId(refused, bare)).orElseThrow();
        assertTrue(row.errorMessage().startsWith("bare-transaction-set"), row.errorMessage());
        handle.close();
        buffer.close();

        Path dir2 = Files.createDirectories(dir.resolve("allowed"));
        Path inbox2 = inbox(dir2);
        open(dir2, config(inbox2, 0, true), new MutableClock(T0), null);
        Path accepted = drop(inbox2, "bare.835", bare);
        assertEquals(new RescanResult(1, 1, 1, 0), handle.rescan(null));
        assertTrue(Files.exists(Path.of(accepted + ".done")));
        String fileId = InboxFixtures.fileId(accepted, bare);
        TransactionRow tx = buffer.byElementKey(key(fileId, "000000001", "1", "0001")).orElseThrow();
        assertEquals(TransactionRow.ENVELOPE_SYNTHETIC, tx.envelope());
        assertEquals("SYNTHETIC", tx.senderId());
        assertEquals("000000001", tx.isaControl());
        assertEquals("005010X221A1", tx.gs08());
        assertTrue(tx.mappedJson().contains("\"envelope\":\"synthetic\""));
        assertTrue(tx.mappedJson().contains("\"clp04\":220.00"));
    }

    @Test
    void backpressureLeavesFilesUntouched(@TempDir Path dir) throws Exception {
        Path inbox = inbox(dir);
        MutableClock clock = new MutableClock(T0);
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false, clock);
        // maxBytes=1: any SQLite file is over capacity and the sweeper has nothing acked to free.
        RetentionSweeper sweeper = new RetentionSweeper(buffer, new RetentionConfig(null, 1L), clock);
        assertTrue(sweeper.overCapacity());
        handle = X12InboxPollerFactory.start(config(inbox, 0, false), buffer, sweeper, clock, false);
        Path f = drop(inbox, "remit.835", Fixtures.bytes(Fixtures.F835));
        assertEquals(new RescanResult(1, 1, 0, 0), handle.rescan(null));
        assertTrue(Files.exists(f), "not renamed");
        assertEquals(0, buffer.fileCount(), "no files row");
        assertEquals(0, buffer.count());
        assertTrue(handle.backpressure());
        assertEquals(1, handle.sources().get(0).pending());
    }

    @Test
    void pollerSweepsBeforeDeclaringBackpressure(@TempDir Path dir) throws Exception {
        // Over the ceiling only because of acked rows retention may evict: the scheduled sweep
        // could be minutes away, so the poller makes the room itself instead of stalling.
        Path inbox = inbox(dir);
        MutableClock clock = new MutableClock(T0);
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false, clock);
        TestRows.seedAcked(buffer, TestRows.FILE_A, 256, 4096);
        RetentionSweeper sweeper = new RetentionSweeper(buffer, new RetentionConfig(null, 256L * 1024), clock);
        assertTrue(sweeper.overCapacity());
        handle = X12InboxPollerFactory.start(config(inbox, 0, false), buffer, sweeper, clock, false);
        Path f = drop(inbox, "remit.835", Fixtures.bytes(Fixtures.F835));

        assertEquals(new RescanResult(1, 1, 1, 0), handle.rescan(null));
        assertTrue(Files.exists(Path.of(f + ".done")));
        assertFalse(handle.backpressure());
        assertEquals(0, buffer.count(Status.ACKED), "evicted to make room");
    }

    @Test
    void sameNameNewContentIsConsumedAgainWithATimestampedDone(@TempDir Path dir) throws Exception {
        // DESIGN §4.2: nothing is skipped by path. fileId = <path>@<hash>, so a reused name with new
        // bytes is a new file; its .done target already exists, so the discovery time is interposed.
        Path inbox = inbox(dir);
        MutableClock clock = new MutableClock(T0);
        open(dir, config(inbox, 0, false), clock, null);
        Path f = drop(inbox, "remit.835", Fixtures.bytes(Fixtures.F835));
        String first = InboxFixtures.fileId(f, Fixtures.bytes(Fixtures.F835));
        assertEquals(new RescanResult(1, 1, 1, 0), handle.rescan(null));

        clock.advance(Duration.ofSeconds(5));
        Path again = drop(inbox, "remit.835", Fixtures.bytes(Fixtures.F837P));
        String second = InboxFixtures.fileId(again, Fixtures.bytes(Fixtures.F837P));
        assertNotEquals(first, second);
        assertEquals(new RescanResult(1, 1, 1, 0), handle.rescan(null));
        assertFalse(Files.exists(again), "renamed");
        Path stamped = inbox.resolve("remit.835." + T0.plusSeconds(5).toEpochMilli() + ".done");
        assertTrue(Files.exists(inbox.resolve("remit.835.done")), "first .done untouched");
        assertTrue(Files.exists(stamped), "second .done carries the discovery time: " + stamped);
        assertEquals(2, Files.list(inbox).count());

        assertEquals(2, buffer.fileCount());
        assertEquals(2, buffer.fileCount(FileStatus.CONSUMED));
        FileRow row = buffer.fileById(second).orElseThrow();
        assertEquals(stamped.toString(), row.currentPath(), "current_path = the actual rename target");
        assertEquals(again.toString(), row.filePath());
        assertEquals(buffer.fileById(first).orElseThrow().filePath(), row.filePath(), "same discovery path, two ids");
        assertTrue(buffer.byElementKey(key(first, "000000101", "101", "0001")).isPresent(), "transactions from the first");
        assertTrue(buffer.byElementKey(key(second, "000000102", "102", "0001")).isPresent(), "transactions from the second");
        assertEquals(List.of(first, second), buffer.distinctValues("file_id"));
    }

    @Test
    void sameNameSameContentIsARedeliveryNotNewRows(@TempDir Path dir) throws Exception {
        Path inbox = inbox(dir);
        MutableClock clock = new MutableClock(T0);
        open(dir, config(inbox, 0, false), clock, null);
        Path f = drop(inbox, "remit.835", Fixtures.bytes(Fixtures.F835));
        String fileId = InboxFixtures.fileId(f, Fixtures.bytes(Fixtures.F835));
        assertEquals(new RescanResult(1, 1, 1, 0), handle.rescan(null));
        assertEquals(1, buffer.count());

        clock.advance(Duration.ofSeconds(7));
        drop(inbox, "remit.835", Fixtures.bytes(Fixtures.F835));
        assertEquals(new RescanResult(1, 1, 1, 0), handle.rescan(null), "a redelivery is acknowledged (counted as consumed)");
        Path stamped = inbox.resolve("remit.835." + T0.plusSeconds(7).toEpochMilli() + ".done");
        assertTrue(Files.exists(stamped), "redelivered copy renamed .done with the discovery time");
        assertTrue(Files.exists(inbox.resolve("remit.835.done")));
        assertFalse(Files.exists(f));

        assertEquals(1, buffer.fileCount(), "same id: the row is updated, not duplicated");
        FileRow row = buffer.fileById(fileId).orElseThrow();
        assertEquals(FileStatus.CONSUMED, row.status(), "status stays");
        assertEquals(1, row.redeliveryCount());
        assertEquals(stamped.toString(), row.currentPath());
        assertEquals(1, buffer.count(), "no transactions re-ingested");

        // and a third landing counts again
        clock.advance(Duration.ofSeconds(1));
        drop(inbox, "remit.835", Fixtures.bytes(Fixtures.F835));
        assertEquals(new RescanResult(1, 1, 1, 0), handle.rescan(null));
        assertEquals(2, buffer.fileById(fileId).orElseThrow().redeliveryCount());
        assertEquals(1, buffer.fileCount());
        assertEquals(1, buffer.count());
    }

    @Test
    void errorFileFixedByTheOperatorIsConsumed(@TempDir Path dir) throws Exception {
        Path inbox = inbox(dir);
        open(dir, config(inbox, 0, false), new MutableClock(T0), null);
        byte[] broken = Fixtures.malformed("truncated-no-iea.x12");
        Path f = drop(inbox, "fix.835", broken);
        String brokenId = InboxFixtures.fileId(f, broken);
        assertEquals(new RescanResult(1, 1, 0, 1), handle.rescan(null));
        Path error = inbox.resolve("fix.835.error");
        assertTrue(Files.exists(error));

        // Operator: rename it back and fix the content.
        Files.move(error, f);
        Files.write(f, Fixtures.bytes(Fixtures.F835));
        String fixedId = InboxFixtures.fileId(f, Fixtures.bytes(Fixtures.F835));
        assertEquals(new RescanResult(1, 1, 1, 0), handle.rescan(null));
        assertTrue(Files.exists(inbox.resolve("fix.835.done")));
        assertFalse(Files.exists(error));
        assertEquals(1, buffer.count());
        assertTrue(buffer.byElementKey(key(fixedId, "000000101", "101", "0001")).isPresent());
        assertEquals(FileStatus.CONSUMED, buffer.fileById(fixedId).orElseThrow().status());
        assertEquals(FileStatus.ERROR, buffer.fileById(brokenId).orElseThrow().status(), "the failed bytes keep their audit row");
        assertEquals(2, buffer.fileCount());
    }

    @Test
    void errorFileRenamedBackUnchangedFailsAgainAndReplacesItsRow(@TempDir Path dir) throws Exception {
        Path inbox = inbox(dir);
        open(dir, config(inbox, 0, false), new MutableClock(T0), null);
        byte[] broken = Fixtures.malformed("truncated-no-iea.x12");
        Path f = drop(inbox, "same.835", broken);
        String fileId = InboxFixtures.fileId(f, broken);
        assertEquals(new RescanResult(1, 1, 0, 1), handle.rescan(null));
        long firstRowId = buffer.fileById(fileId).orElseThrow().id();
        Path error = inbox.resolve("same.835.error");

        Files.move(error, f);   // operator retries without fixing anything
        assertEquals(new RescanResult(1, 1, 0, 1), handle.rescan(null), "same checksum + error status = retry; it fails again");
        assertTrue(Files.exists(error), "renamed .error again");
        assertFalse(Files.exists(f));
        assertEquals(1, buffer.fileCount(), "the error row is replaced, not duplicated");
        FileRow row = buffer.fileById(fileId).orElseThrow();
        assertEquals(FileStatus.ERROR, row.status());
        assertNotEquals(firstRowId, row.id(), "a fresh row");
        assertEquals(error.toString(), row.currentPath());
        assertEquals(0, buffer.count());
    }

    @Test
    void renameFailedFileIsNotRehashedByThisProcess(@TempDir Path dir) throws Exception {
        // The one in-memory guard: a consumed file still at its path (rename failed) is keyed by
        // (path, size, mtime) and skipped until it leaves the listing. Simulated by making the
        // inbox read-only after the drop, so the post-commit rename fails.
        Path inbox = inbox(dir);
        open(dir, config(inbox, 0, false), new MutableClock(T0), null);
        Path f = drop(inbox, "stuck.835", Fixtures.bytes(Fixtures.F835));
        String fileId = InboxFixtures.fileId(f, Fixtures.bytes(Fixtures.F835));
        java.util.Set<java.nio.file.attribute.PosixFilePermission> perms = Files.getPosixFilePermissions(inbox);
        Files.setPosixFilePermissions(inbox, java.util.Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
            java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE));
        try {
            assumeFalse(Files.isWritable(inbox), "running as root: permissions are not enforced");
            assertEquals(new RescanResult(1, 1, 1, 0), handle.rescan(null));
            assertTrue(Files.exists(f), "rename failed: still at its path");
            FileRow row = buffer.fileById(fileId).orElseThrow();
            assertTrue(row.renameFailed());
            assertEquals(f.toString(), row.currentPath());
            assertEquals(1, buffer.count());
            assertEquals(new RescanResult(0, 0, 0, 0), handle.rescan(null), "guarded: not re-hashed");
            assertEquals(0, buffer.fileById(fileId).orElseThrow().redeliveryCount());
        } finally {
            Files.setPosixFilePermissions(inbox, perms);
        }
        // A new process (new poller) re-hashes it, finds the row and retries the rename as a redelivery.
        handle.close();
        handle = X12InboxPollerFactory.start(config(inbox, 0, false), buffer, null, new MutableClock(T0), false);
        assertEquals(new RescanResult(1, 1, 1, 0), handle.rescan(null));
        assertTrue(Files.exists(inbox.resolve("stuck.835.done")));
        FileRow row = buffer.fileById(fileId).orElseThrow();
        assertEquals(1, row.redeliveryCount());
        assertEquals(inbox.resolve("stuck.835.done").toString(), row.currentPath());
        assertEquals(1, buffer.fileCount());
        assertEquals(1, buffer.count());
    }

    @Test
    void rescanTargetsOneSourceOrAllAndRejectsUnknownNames(@TempDir Path dir) throws Exception {
        Path a = Files.createDirectories(dir.resolve("a"));
        Path b = Files.createDirectories(dir.resolve("b"));
        ModuleRuntimeConfig cfg = new ModuleRuntimeConfig(
            List.of(new SourceConfig("payer-a", a.toString(), "*", 1, 0), new SourceConfig("payer-b", b.toString(), "*", 1, 0)),
            ".done", ".error", false, RetentionConfig.none(), false, ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES);
        open(dir, cfg, new MutableClock(T0), null);
        drop(a, "one.835", Fixtures.bytes(Fixtures.F835));
        drop(b, "two.837", Fixtures.bytes(Fixtures.F837P));
        drop(b, "bad.837", Fixtures.malformed("empty.x12"));
        assertThrows(IllegalArgumentException.class, () -> handle.rescan("nope"));
        assertEquals(new RescanResult(1, 1, 1, 0), handle.rescan("payer-a"));
        assertEquals(new RescanResult(2, 2, 1, 1), handle.rescan(null), "remaining source only");
        assertEquals(List.of("payer-a", "payer-b"), handle.sources().stream().map(PollerStatus.SourceStatus::name).toList());
        assertEquals(0, handle.sources().get(0).errored());
        assertEquals(1, handle.sources().get(1).errored());
        String twoId = InboxFixtures.fileId(b.resolve("two.837"), Fixtures.bytes(Fixtures.F837P));
        assertEquals("payer-b", buffer.byElementKey(key(twoId, "000000102", "102", "0001")).orElseThrow().sourceName());
        assertEquals(List.of("payer-a", "payer-b"), buffer.distinctValues("source_name"));
    }

    @Test
    void scheduledPollerReportsUpUntilClosed(@TempDir Path dir) throws Exception {
        Path inbox = inbox(dir);
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false);
        drop(inbox, "remit.835", Fixtures.bytes(Fixtures.F835));
        handle = X12InboxPollerFactory.start(config(inbox, 0, false), buffer, null, Clock.systemUTC(), true);
        assertTrue(handle.up());
        long deadline = System.currentTimeMillis() + 10_000;
        while (buffer.count() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertEquals(1, buffer.count(), "scheduled scan consumed the file");
        assertTrue(handle.lastScan().isPresent());
        assertTrue(Files.exists(inbox.resolve("remit.835.done")));
        handle.close();
        assertFalse(handle.up());
    }

    @Test
    void envelopeOnlyDegradeWhenNoIndexExists(@TempDir Path dir) throws Exception {
        // A resolver that never finds an index → rows carry the shared envelope schema and only envelope fields.
        Path inbox = inbox(dir);
        MutableClock clock = new MutableClock(T0);
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false, clock);
        StructureResolver none = new StructureResolver(gs08 -> Optional.empty());
        FileConsumer consumer = new FileConsumer(buffer, null, config(inbox, 0, false), none, clock);
        Path f = drop(inbox, "remit.835", Fixtures.bytes(Fixtures.F835));
        FileConsumer.Result r = InboxFixtures.consume(consumer, new SourceConfig("inbox", inbox.toString(), "*", 1, 0), f, T0);
        assertEquals(FileConsumer.Outcome.CONSUMED, r.outcome());
        TransactionRow row = buffer.byElementKey(key(r.fileId(), "000000101", "101", "0001")).orElseThrow();
        assertEquals(StructureResolver.ENVELOPE_SCHEMA, row.schemaId());
        assertTrue(row.mappedJson().contains("\"parserErrorCount\":0"));
        assertFalse(row.mappedJson().contains("\"header\""), row.mappedJson());
    }

    @Test
    void twoInterchangesReusingGs06AndSt02AreBothStored(@TempDir Path dir) throws Exception {
        // Senders restart GS06/ST02 per interchange; only ISA13 tells the two apart.
        Path inbox = inbox(dir);
        open(dir, config(inbox, 0, false), new MutableClock(T0), null);
        String first = Fixtures.text(Fixtures.F835);
        byte[] bytes = (first + first.replace("000000101", "000000102")).getBytes(StandardCharsets.UTF_8);
        Path f = drop(inbox, "two-isa.835", bytes);
        String fileId = InboxFixtures.fileId(f, bytes);

        assertEquals(new RescanResult(1, 1, 1, 0), handle.rescan(null));
        assertEquals(2, buffer.count(), "both transaction sets stored");
        assertTrue(buffer.byElementKey(key(fileId, "000000101", "101", "0001")).isPresent());
        assertTrue(buffer.byElementKey(key(fileId, "000000102", "101", "0001")).isPresent());
        FileRow row = buffer.fileById(fileId).orElseThrow();
        assertEquals(2, row.isaCount());
        assertEquals(2, row.transactionCount());
    }

    @Test
    void collidingElementKeysSendTheFileToErrorInsteadOfDroppingASet(@TempDir Path dir) throws Exception {
        // The same interchange twice in one file: every key collides, so nothing may be acknowledged.
        Path inbox = inbox(dir);
        open(dir, config(inbox, 0, false), new MutableClock(T0), null);
        String once = Fixtures.text(Fixtures.F835);
        byte[] bytes = (once + once).getBytes(StandardCharsets.UTF_8);
        Path f = drop(inbox, "twice.835", bytes);

        assertEquals(new RescanResult(1, 1, 0, 1), handle.rescan(null));
        assertTrue(Files.exists(Path.of(f + ".error")));
        assertEquals(0, buffer.count(), "rolled back: no transaction row");
        FileRow row = buffer.fileById(InboxFixtures.fileId(f, bytes)).orElseThrow();
        assertEquals(FileStatus.ERROR, row.status());
        assertTrue(row.errorMessage().startsWith("duplicate-element-key: "), row.errorMessage());
    }

    @Test
    void fileOverMaxFileBytesIsHashedAndErroredWithoutBeingParsed(@TempDir Path dir) throws Exception {
        Path inbox = inbox(dir);
        byte[] ok = Fixtures.bytes(Fixtures.F835);
        byte[] big = (Fixtures.text(Fixtures.F835) + "\n".repeat(16)).getBytes(StandardCharsets.UTF_8);
        ModuleRuntimeConfig cfg = new ModuleRuntimeConfig(
            List.of(new SourceConfig("inbox", inbox.toString(), "*", 1, 0)),
            ".done", ".error", false, RetentionConfig.none(), false, ok.length);
        open(dir, cfg, new MutableClock(T0), null);
        Path bigFile = drop(inbox, "a-big.835", big);
        Path okFile = drop(inbox, "b-ok.835", ok);

        assertEquals(new RescanResult(2, 2, 1, 1), handle.rescan(null), "the oversized file does not stop the next");
        assertTrue(Files.exists(Path.of(bigFile + ".error")));
        assertTrue(Files.exists(Path.of(okFile + ".done")), "a file of exactly maxFileBytes is consumed");
        FileRow row = buffer.fileById(InboxFixtures.fileId(bigFile, big)).orElseThrow();
        assertEquals(FileStatus.ERROR, row.status());
        assertEquals("too-large: " + big.length + " bytes exceeds maxFileBytes " + ok.length, row.errorMessage());
        assertEquals(InboxFixtures.sha256(big), row.checksum(), "streamed hash of the whole file");
        assertEquals(big.length, row.sizeBytes());
        assertEquals(1, buffer.count(), "only the in-limit file's transaction");
    }

    @Test
    void aFileThatExhaustsTheHeapIsErroredAndTheScanGoesOn(@TempDir Path dir) throws Exception {
        Path inbox = inbox(dir);
        MutableClock clock = new MutableClock(T0);
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false, clock);
        ModuleRuntimeConfig cfg = config(inbox, 0, false);
        StructureResolver heapHog = new StructureResolver(gs08 -> {
            if (gs08.contains("X222")) {
                throw new OutOfMemoryError("simulated");
            }
            return StructureIndex.fromClasspath(gs08);
        });
        InboxPoller poller = new InboxPoller(cfg.sources().get(0), new FileConsumer(buffer, null, cfg, heapHog, clock),
            buffer, clock, ".done", ".error");
        Path claims = drop(inbox, "a-claims.837", Fixtures.bytes(Fixtures.F837P));
        Path remit = drop(inbox, "b-remit.835", Fixtures.bytes(Fixtures.F835));

        assertEquals(new RescanResult(2, 2, 1, 1), poller.scan());
        assertTrue(Files.exists(Path.of(claims + ".error")), "the file is this file's problem, not the source's");
        assertTrue(Files.exists(Path.of(remit + ".done")), "the next file is still consumed");
        FileRow row = buffer.fileById(InboxFixtures.fileId(claims, Fixtures.bytes(Fixtures.F837P))).orElseThrow();
        assertTrue(row.errorMessage().startsWith("internal: java.lang.OutOfMemoryError"), row.errorMessage());
        assertEquals(1, buffer.count());
        assertEquals(0, poller.status().consecutiveFailures(), "a file's error is not a failed scan");
    }

    @Test
    void unexpectedFailuresAreIsolatedPerFileAndCountedPerScan(@TempDir Path dir) throws Exception {
        Path inbox = inbox(dir);
        open(dir, config(inbox, 0, false), new MutableClock(T0), null);
        drop(inbox, "one.835", Fixtures.bytes(Fixtures.F835));
        drop(inbox, "two.837", Fixtures.bytes(Fixtures.F837P));
        buffer.close();   // every file now fails with an SQLException outside the parser

        for (int scan = 1; scan <= 3; scan++) {
            assertEquals(new RescanResult(2, scan == 1 ? 2 : 0, 0, 0), handle.rescan(null),
                "both files are tried and the scan completes");
            assertEquals(scan, handle.sources().get(0).consecutiveFailures());
        }
        PollerStatus.SourceStatus s = handle.sources().get(0);
        assertTrue(s.lastError().contains("SQLException"), s.lastError());
        assertEquals(2, s.pending());
        assertTrue(Files.exists(inbox.resolve("one.835")), "left in place for a later scan");
    }

    @Test
    void symlinksAreSkippedNeverFollowed(@TempDir Path dir) throws Exception {
        Path inbox = inbox(dir);
        Path outside = Files.createDirectories(dir.resolve("elsewhere"));
        Path target = drop(outside, "secret.835", Fixtures.bytes(Fixtures.F835));
        Path link = Files.createSymbolicLink(inbox.resolve("link.835"), target);
        open(dir, config(inbox, 0, false), new MutableClock(T0), null);

        assertEquals(new RescanResult(0, 0, 0, 0), handle.rescan(null));
        assertEquals(new RescanResult(0, 0, 0, 0), handle.rescan(null));
        assertTrue(Files.isSymbolicLink(link), "the link is not renamed");
        assertTrue(Files.exists(target), "the target is not touched");
        assertEquals(0, buffer.fileCount());
        assertEquals(0, buffer.count());

        SourceConfig source = config(inbox, 0, false).sources().get(0);
        FileConsumer consumer = new FileConsumer(buffer, null, config(inbox, 0, false), new StructureResolver(),
            Clock.systemUTC());
        assertThrows(IllegalArgumentException.class, () -> InboxFixtures.consume(consumer, source, target, T0),
            "a path outside the source directory is refused");
        assertThrows(IllegalArgumentException.class,
            () -> InboxFixtures.consume(consumer, source, inbox.resolve("../elsewhere/secret.835"), T0));
    }

    @Test
    void fileChangedSinceItsStableSightingIsLeftForTheNextScan(@TempDir Path dir) throws Exception {
        Path inbox = inbox(dir);
        MutableClock clock = new MutableClock(T0);
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false, clock);
        ModuleRuntimeConfig cfg = config(inbox, 0, false);
        FileConsumer consumer = new FileConsumer(buffer, null, cfg, new StructureResolver(), clock);
        Path f = drop(inbox, "moving.835", Fixtures.bytes(Fixtures.F835));
        Instant mtime = Files.getLastModifiedTime(f).toInstant();
        long size = Files.size(f);
        SourceConfig source = cfg.sources().get(0);

        FileConsumer.Result grew = consumer.consume(source, f, new FileStability.Sighting(size - 1, mtime, T0, T0));
        assertEquals(FileConsumer.Outcome.CHANGED, grew.outcome());
        FileConsumer.Result touched = consumer.consume(source, f,
            new FileStability.Sighting(size, mtime.minusSeconds(5), T0, T0));
        assertEquals(FileConsumer.Outcome.CHANGED, touched.outcome());
        assertTrue(Files.exists(f), "not renamed");
        assertEquals(0, buffer.fileCount(), "no row");
        assertEquals(0, buffer.count());

        assertEquals(FileConsumer.Outcome.CONSUMED,
            consumer.consume(source, f, new FileStability.Sighting(size, mtime, T0, T0)).outcome());
    }

    @Test
    void unreadableFileIsRecordedOnceAfterRepeatedFailuresAndClearedWhenRead(@TempDir Path dir) throws Exception {
        Path inbox = inbox(dir);
        open(dir, config(inbox, 0, false), new MutableClock(T0), null);
        Path f = drop(inbox, "locked.835", Fixtures.bytes(Fixtures.F835));
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(f);
        Files.setPosixFilePermissions(f, PosixFilePermissions.fromString("---------"));
        String unreadableId = FileRow.unreadableId(f.toString());
        try {
            assumeFalse(Files.isReadable(f), "running as root: permissions are not enforced");
            for (int attempt = 1; attempt < InboxPoller.UNREADABLE_ESCALATION; attempt++) {
                handle.rescan(null);
                assertTrue(buffer.fileById(unreadableId).isEmpty(), "no row before the threshold (attempt " + attempt + ")");
            }
            handle.rescan(null);
            FileRow row = buffer.fileById(unreadableId).orElseThrow();
            assertEquals(FileStatus.ERROR, row.status());
            assertTrue(row.errorMessage().startsWith("io: "), row.errorMessage());
            assertEquals(f.toString(), row.currentPath(), "not renamed: still retried");
            assertEquals(1, handle.sources().get(0).errored());
            handle.rescan(null);
            assertEquals(1, buffer.fileCount(), "recorded once");
            assertTrue(Files.exists(f));
        } finally {
            Files.setPosixFilePermissions(f, perms);
        }
        assertEquals(new RescanResult(1, 0, 1, 0), handle.rescan(null), "readable again: consumed");
        assertTrue(buffer.fileById(unreadableId).isEmpty(), "the unreadable row is resolved");
        assertEquals(FileStatus.CONSUMED,
            buffer.fileById(InboxFixtures.fileId(f, Fixtures.bytes(Fixtures.F835))).orElseThrow().status());
    }

    @Test
    void aRowSqliteRefusesSendsItsFileToErrorInsteadOfRetryingItEveryScan(@TempDir Path dir) throws Exception {
        // limit_length stands in for SQLite's 1e9-byte value limit: the 835's typed JSON (~3 KB) is over it.
        Path inbox = inbox(dir);
        MutableClock clock = new MutableClock(T0);
        buffer = new BufferStore(dir.resolve("buffer.db") + "?limit_length=2000", false, clock);
        handle = X12InboxPollerFactory.start(config(inbox, 0, false), buffer, null, clock, false);
        Path f = drop(inbox, "remit.835", Fixtures.bytes(Fixtures.F835));

        assertEquals(new RescanResult(1, 1, 0, 1), handle.rescan(null));
        assertTrue(Files.exists(Path.of(f + ".error")));
        FileRow row = buffer.fileById(InboxFixtures.fileId(f, Fixtures.bytes(Fixtures.F835))).orElseThrow();
        assertEquals(FileStatus.ERROR, row.status());
        assertTrue(row.errorMessage().startsWith("buffer-rejected: [SQLITE_TOOBIG]"), row.errorMessage());
        assertEquals(0, buffer.count(), "rolled back");
        assertEquals(0, handle.sources().get(0).consecutiveFailures(), "the file's error, not a failed scan");
        assertEquals(new RescanResult(0, 0, 0, 0), handle.rescan(null), "not retried");
    }

    @Test
    void storeWideSqlErrorsAreNotBlamedOnTheFile() {
        assertTrue(FileConsumer.rejectsThisFile(new java.sql.SQLException("too big", null, 18)));
        assertTrue(FileConsumer.rejectsThisFile(new java.sql.SQLException("constraint", null, 19 | (5 << 8))),
            "an extended code counts by its primary code");
        for (int storeWide : new int[] {5, 10, 11, 13, 21}) {   // BUSY, IOERR, CORRUPT, FULL, MISUSE
            assertFalse(FileConsumer.rejectsThisFile(new java.sql.SQLException("x", null, storeWide)), "code " + storeWide);
        }
    }

    @Test
    void closeStopsAScanAtTheNextFileInsteadOfWaitingForTheBacklog(@TempDir Path dir) throws Exception {
        Path inbox = inbox(dir);
        MutableClock clock = new MutableClock(T0);
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false, clock);
        ModuleRuntimeConfig cfg = config(inbox, 0, false);
        java.util.concurrent.CountDownLatch inFirstFile = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        // Holds the scan inside its first file (a slow parse, a hung mount) until interrupted or released.
        StructureResolver slow = new StructureResolver(gs08 -> {
            inFirstFile.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return StructureIndex.fromClasspath(gs08);
        });
        InboxPoller poller = new InboxPoller(cfg.sources().get(0), new FileConsumer(buffer, null, cfg, slow, clock),
            buffer, clock, ".done", ".error");
        Path a = drop(inbox, "a.835", Fixtures.bytes(Fixtures.F835));
        Path b = drop(inbox, "b.835", Fixtures.text(Fixtures.F835).replace("000000101", "000000102")
            .getBytes(StandardCharsets.UTF_8));
        Path c = drop(inbox, "c.835", Fixtures.text(Fixtures.F835).replace("000000101", "000000103")
            .getBytes(StandardCharsets.UTF_8));
        poller.start();
        Thread closer = new Thread(poller::close);
        try {
            assertTrue(inFirstFile.await(10, java.util.concurrent.TimeUnit.SECONDS), "the scan reached the first file");
            closer.start();
            closer.join(InboxPoller.CLOSE_WAIT.toMillis() + 2_000);
            assertFalse(closer.isAlive(), "close() does not queue behind the running scan");
        } finally {
            release.countDown();
            closer.join();
        }
        assertFalse(poller.up());
        assertTrue(Files.exists(Path.of(a + ".done")), "the file in hand is finished");
        assertTrue(Files.exists(b) && Files.exists(c), "the rest wait for the next start");
        assertEquals(1, buffer.fileCount());
        assertEquals(2, poller.status().pending(), "reported as pending");
    }

    @Test
    void renameNeverReplacesAnExistingTarget(@TempDir Path dir) throws Exception {
        Path inbox = inbox(dir);
        Path from = drop(inbox, "x.835", "new".getBytes(StandardCharsets.UTF_8));
        Path taken = drop(inbox, "x.835.done", "earlier".getBytes(StandardCharsets.UTF_8));

        Path moved = FileConsumer.moveNoClobber(from, taken, ".done", T0);
        assertEquals(inbox.resolve("x.835." + T0.toEpochMilli() + ".done"), moved);
        assertEquals("earlier", Files.readString(taken), "the existing .done is intact");
        assertEquals("new", Files.readString(moved));
        assertFalse(Files.exists(from));
    }

    @Test
    void identicalBytesInTwoSourcesAtOnceAreIngestedOnce(@TempDir Path dir) throws Exception {
        // The checksum lookup and the insert must not interleave across pollers.
        Path a = Files.createDirectories(dir.resolve("a"));
        Path b = Files.createDirectories(dir.resolve("b"));
        MutableClock clock = new MutableClock(T0);
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false, clock);
        ModuleRuntimeConfig cfg = new ModuleRuntimeConfig(
            List.of(new SourceConfig("a", a.toString(), "*", 1, 0), new SourceConfig("b", b.toString(), "*", 1, 0)),
            ".done", ".error", false, RetentionConfig.none(), false, ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES);
        FileConsumer consumer = new FileConsumer(buffer, null, cfg, new StructureResolver(), clock);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 10; round++) {
                String text = Fixtures.text(Fixtures.F835).replace("000000101", String.format("%09d", 500 + round));
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                Path fa = drop(a, "r" + round + ".835", bytes);
                Path fb = drop(b, "r" + round + ".835", bytes);
                CyclicBarrier start = new CyclicBarrier(2);
                List<Future<FileConsumer.Result>> results = new ArrayList<>();
                for (Object[] job : new Object[][] {{cfg.sources().get(0), fa}, {cfg.sources().get(1), fb}}) {
                    results.add(pool.submit(() -> {
                        start.await();
                        return InboxFixtures.consume(consumer, (SourceConfig) job[0], (Path) job[1], T0);
                    }));
                }
                List<FileConsumer.Outcome> outcomes = new ArrayList<>();
                for (Future<FileConsumer.Result> r : results) {
                    outcomes.add(r.get().outcome());
                }
                assertEquals(1, outcomes.stream().filter(o -> o == FileConsumer.Outcome.CONSUMED).count(),
                    "round " + round + ": " + outcomes);
                assertEquals(1, outcomes.stream().filter(o -> o == FileConsumer.Outcome.DUPLICATE).count(),
                    "round " + round + ": " + outcomes);
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(10, buffer.count(), "one transaction per distinct content");
    }
}

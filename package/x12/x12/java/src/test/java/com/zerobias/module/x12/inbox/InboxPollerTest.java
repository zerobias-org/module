package com.zerobias.module.x12.inbox;

import com.zerobias.module.x12.InboxPollerFactory;
import com.zerobias.module.x12.ModuleRuntimeConfig;
import com.zerobias.module.x12.PollerHandle.RescanResult;
import com.zerobias.module.x12.SourceConfig;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.FileRow;
import com.zerobias.module.x12.buffer.FileStatus;
import com.zerobias.module.x12.buffer.RetentionConfig;
import com.zerobias.module.x12.buffer.RetentionSweeper;
import com.zerobias.module.x12.buffer.Status;
import com.zerobias.module.x12.buffer.TestRows.MutableClock;
import com.zerobias.module.x12.buffer.TransactionRow;
import com.zerobias.module.x12.health.PollerStatus;
import com.zerobias.module.x12.materializer.StructureResolver;
import com.zerobias.module.x12.parser.Fixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            ".done", ".error", false, RetentionConfig.none(), allowBare, false);
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

    @Test
    void stableFileIsConsumedCommittedThenRenamedDone(@TempDir Path dir) throws Exception {
        Path inbox = inbox(dir);
        MutableClock clock = new MutableClock(T0);
        open(dir, config(inbox, 0, false), clock, null);
        Path f = drop(inbox, "remit.835", Fixtures.bytes(Fixtures.F835));
        String fileId = FileConsumer.fileId(f, Fixtures.bytes(Fixtures.F835));
        assertEquals(f + "@" + FileConsumer.sha256(Fixtures.bytes(Fixtures.F835)).substring(0, 12), fileId,
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
        assertEquals(FileConsumer.sha256(Fixtures.bytes(Fixtures.F835)), file.checksum());
        assertEquals(T0, file.discoveredAt());
        assertEquals(T0, file.consumedAt());
        assertFalse(file.renameFailed());

        assertEquals(1, buffer.count());
        TransactionRow row = buffer.byElementKey(fileId + ":000000101:101:0001").orElseThrow();
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
        // The document is the object graph reassembled, with the envelope overlaid at read
        // time by the facade (DESIGN §8.4) — there is no stored JSON column to inspect.
        String json = new com.google.gson.Gson().toJson(
            com.zerobias.module.x12.producer.X12ProducerFacade.toElement(row,
                buffer.documentFor(row.elementKey())));
        assertTrue(json.contains("\"elementKey\":\"" + fileId + ":000000101:101:0001\""), json);
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
        assertEquals(List.of("inbox", inbox.toString(), true, 0, 0),
            List.of(s.name(), s.path(), s.writable(), s.pending(), s.errored()));
        assertEquals(1, s.pollIntervalSec());
        assertEquals(T0, s.lastScan());
        assertFalse(s.failing());
        assertEquals(null, s.lastError());

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
        assertEquals(T0, buffer.fileById(FileConsumer.fileId(f, Fixtures.bytes(Fixtures.F835))).orElseThrow().discoveredAt(),
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
            ".done", ".error", false, RetentionConfig.none(), false, false);
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
        FileRow row = buffer.fileById(FileConsumer.fileId(dup, Fixtures.bytes(Fixtures.F835))).orElseThrow();
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

        FileRow u = buffer.fileById(FileConsumer.fileId(unknown, Fixtures.malformed("unknown-guide-gs08.x12"))).orElseThrow();
        assertEquals(FileStatus.ERROR, u.status());
        assertEquals("unsupported-guide: GS08 '005010X999' is not a supported implementation guide", u.errorMessage());
        assertEquals(unknown + ".error", u.currentPath());
        assertEquals(1317, u.sizeBytes());
        assertNotNull(u.checksum());

        FileRow t = buffer.fileById(FileConsumer.fileId(truncated, Fixtures.malformed("truncated-no-iea.x12"))).orElseThrow();
        assertTrue(t.errorMessage().startsWith("fatal:"), t.errorMessage());
        assertTrue(t.errorMessage().contains("Unable to find end of transaction"), "imsweb fatal errors included: " + t.errorMessage());

        FileRow e = buffer.fileById(FileConsumer.fileId(empty, Fixtures.malformed("empty.x12"))).orElseThrow();
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
        FileRow row = buffer.fileById(FileConsumer.fileId(refused, bare)).orElseThrow();
        assertTrue(row.errorMessage().startsWith("bare-transaction-set"), row.errorMessage());
        handle.close();
        buffer.close();

        Path dir2 = Files.createDirectories(dir.resolve("allowed"));
        Path inbox2 = inbox(dir2);
        open(dir2, config(inbox2, 0, true), new MutableClock(T0), null);
        Path accepted = drop(inbox2, "bare.835", bare);
        assertEquals(new RescanResult(1, 1, 1, 0), handle.rescan(null));
        assertTrue(Files.exists(Path.of(accepted + ".done")));
        String fileId = FileConsumer.fileId(accepted, bare);
        TransactionRow tx = buffer.byElementKey(fileId + ":000000001:1:0001").orElseThrow();
        assertEquals(TransactionRow.ENVELOPE_SYNTHETIC, tx.envelope());
        assertEquals("SYNTHETIC", tx.senderId());
        assertEquals("000000001", tx.isaControl());
        assertEquals("005010X221A1", tx.gs08());
        assertEquals("synthetic", tx.envelope(), "the envelope is a row column, not body content");
        String body = new com.google.gson.Gson().toJson(buffer.documentFor(tx.elementKey()));
        assertTrue(body.contains("\"clp04\":220.00"), body);
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
    void sameNameNewContentIsConsumedAgainWithATimestampedDone(@TempDir Path dir) throws Exception {
        // DESIGN §4.2: nothing is skipped by path. fileId = <path>@<hash>, so a reused name with new
        // bytes is a new file; its .done target already exists, so the discovery time is interposed.
        Path inbox = inbox(dir);
        MutableClock clock = new MutableClock(T0);
        open(dir, config(inbox, 0, false), clock, null);
        Path f = drop(inbox, "remit.835", Fixtures.bytes(Fixtures.F835));
        String first = FileConsumer.fileId(f, Fixtures.bytes(Fixtures.F835));
        assertEquals(new RescanResult(1, 1, 1, 0), handle.rescan(null));

        clock.advance(Duration.ofSeconds(5));
        Path again = drop(inbox, "remit.835", Fixtures.bytes(Fixtures.F837P));
        String second = FileConsumer.fileId(again, Fixtures.bytes(Fixtures.F837P));
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
        assertTrue(buffer.byElementKey(first + ":000000101:101:0001").isPresent(), "transactions from the first");
        assertTrue(buffer.byElementKey(second + ":000000102:102:0001").isPresent(), "transactions from the second");
        assertEquals(List.of(first, second), buffer.distinctValues("file_id"));
    }

    @Test
    void sameNameSameContentIsARedeliveryNotNewRows(@TempDir Path dir) throws Exception {
        Path inbox = inbox(dir);
        MutableClock clock = new MutableClock(T0);
        open(dir, config(inbox, 0, false), clock, null);
        Path f = drop(inbox, "remit.835", Fixtures.bytes(Fixtures.F835));
        String fileId = FileConsumer.fileId(f, Fixtures.bytes(Fixtures.F835));
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
        String brokenId = FileConsumer.fileId(f, broken);
        assertEquals(new RescanResult(1, 1, 0, 1), handle.rescan(null));
        Path error = inbox.resolve("fix.835.error");
        assertTrue(Files.exists(error));

        // Operator: rename it back and fix the content.
        Files.move(error, f);
        Files.write(f, Fixtures.bytes(Fixtures.F835));
        String fixedId = FileConsumer.fileId(f, Fixtures.bytes(Fixtures.F835));
        assertEquals(new RescanResult(1, 1, 1, 0), handle.rescan(null));
        assertTrue(Files.exists(inbox.resolve("fix.835.done")));
        assertFalse(Files.exists(error));
        assertEquals(1, buffer.count());
        assertTrue(buffer.byElementKey(fixedId + ":000000101:101:0001").isPresent());
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
        String fileId = FileConsumer.fileId(f, broken);
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
        String fileId = FileConsumer.fileId(f, Fixtures.bytes(Fixtures.F835));
        java.util.Set<java.nio.file.attribute.PosixFilePermission> perms = Files.getPosixFilePermissions(inbox);
        Files.setPosixFilePermissions(inbox, java.util.Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
            java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE));
        try {
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
            ".done", ".error", false, RetentionConfig.none(), false, false);
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
        assertEquals("payer-b", buffer.byElementKey(FileConsumer.fileId(b.resolve("two.837"), Fixtures.bytes(Fixtures.F837P)) + ":000000102:102:0001")
            .orElseThrow().sourceName());
        assertEquals(List.of("payer-a", "payer-b"), buffer.distinctValues("source_name"));
    }

    @Test
    void scheduledPollerReportsUpUntilClosed(@TempDir Path dir) throws Exception {
        Path inbox = inbox(dir);
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false);
        drop(inbox, "remit.835", Fixtures.bytes(Fixtures.F835));
        handle = X12InboxPollerFactory.start(config(inbox, 0, false), buffer, null, null, true);
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
    void factoryIsDiscoverableThroughServiceLoader() {
        InboxPollerFactory found = null;
        for (InboxPollerFactory f : ServiceLoader.load(InboxPollerFactory.class)) {
            found = f;
        }
        assertNotNull(found, "META-INF/services registration");
        assertEquals(X12InboxPollerFactory.class, found.getClass());
    }

    @Test
    void envelopeOnlyDegradeWhenNoIndexExists(@TempDir Path dir) throws Exception {
        // A resolver that never finds an index → rows carry the shared envelope schema and only envelope fields.
        Path inbox = inbox(dir);
        MutableClock clock = new MutableClock(T0);
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false, clock);
        StructureResolver none = StructureResolver.none();
        FileConsumer consumer = new FileConsumer(buffer, null, config(inbox, 0, false), none, clock);
        Path f = drop(inbox, "remit.835", Fixtures.bytes(Fixtures.F835));
        FileConsumer.Result r = consumer.consume(new SourceConfig("inbox", inbox.toString(), "*", 1, 0), f, T0);
        assertEquals(FileConsumer.Outcome.CONSUMED, r.outcome());
        TransactionRow row = buffer.byElementKey(r.fileId() + ":000000101:101:0001").orElseThrow();
        assertEquals(StructureResolver.ENVELOPE_SCHEMA, row.schemaId());
        assertEquals(0, row.parserErrorCount());
        // No structure index for an unbundled guide: nothing to flatten, so no graph at all.
        String unbundled = new com.google.gson.Gson().toJson(buffer.documentFor(row.elementKey()));
        assertFalse(unbundled.contains("\"header\""), unbundled);
    }

    // ---- element key: <fileId>:<ISA13>:<GS06>:<ST02> ---------------------------------------

    /** The 835 fixture re-numbered as interchange {@code isa13}; GS06 and ST02 are unchanged. */
    private static String interchange835(String isa13) {
        return Fixtures.text(Fixtures.F835).strip()
            .replace("*000000101*0*T*", "*" + isa13 + "*0*T*")
            .replace("IEA*1*000000101~", "IEA*1*" + isa13 + "~");
    }

    @Test
    void twoInterchangesReusingGroupAndSetNumbersAreBothKept(@TempDir Path dir) throws Exception {
        // Each interchange numbers its own groups and sets: GS06=101/ST02=0001 twice in one file
        // is ordinary. Without ISA13 in the key the second set was silently dropped.
        Path inbox = inbox(dir);
        open(dir, config(inbox, 0, false), new MutableClock(T0), null);
        byte[] bytes = (interchange835("000000101") + "\n" + interchange835("000000201") + "\n")
            .getBytes(StandardCharsets.UTF_8);
        Path f = drop(inbox, "two-isa.835", bytes);
        String fileId = FileConsumer.fileId(f, bytes);

        assertEquals(new RescanResult(1, 1, 1, 0), handle.rescan(null));
        assertEquals(2, buffer.count(), "one row per transaction set");
        assertTrue(buffer.byElementKey(fileId + ":000000101:101:0001").isPresent());
        assertTrue(buffer.byElementKey(fileId + ":000000201:101:0001").isPresent());
        assertEquals(2, buffer.fileById(fileId).orElseThrow().transactionCount());
        assertTrue(Files.exists(inbox.resolve("two-isa.835.done")));
    }

    @Test
    void elementKeyCollisionInsideAFileSendsTheWholeFileToError(@TempDir Path dir) throws Exception {
        // Two sets in one group both numbered ST02=0001: the key cannot tell them apart. The file
        // must not be acknowledged .done with one of them missing (or wearing the other's graph).
        Path inbox = inbox(dir);
        open(dir, config(inbox, 0, false), new MutableClock(T0), null);
        String x = Fixtures.text(Fixtures.F835).strip();
        String set = x.substring(x.indexOf("ST*835*0001~"), x.indexOf("GE*1*101~"));
        String doubled = x.replace("GE*1*101~", set + "GE*2*101~");
        byte[] bytes = doubled.getBytes(StandardCharsets.UTF_8);
        Path f = drop(inbox, "dup-st.835", bytes);
        String fileId = FileConsumer.fileId(f, bytes);

        assertEquals(new RescanResult(1, 1, 0, 1), handle.rescan(null));
        assertEquals(0, buffer.count(), "rolled back: no transaction rows");
        assertEquals(0, buffer.entityCount(fileId + ":000000101:101:0001"), "rolled back: no graph");
        FileRow row = buffer.fileById(fileId).orElseThrow();
        assertEquals(FileStatus.ERROR, row.status());
        assertTrue(row.errorMessage().startsWith("duplicate-element-key"), row.errorMessage());
        assertTrue(Files.exists(inbox.resolve("dup-st.835.error")));
        assertFalse(Files.exists(inbox.resolve("dup-st.835.done")));
    }
}

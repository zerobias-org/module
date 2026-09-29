package com.zerobias.module.x12.inbox;

import com.zerobias.module.x12.ModuleRuntimeConfig;
import com.zerobias.module.x12.SourceConfig;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.FileRow;
import com.zerobias.module.x12.buffer.FileStatus;
import com.zerobias.module.x12.buffer.RetentionConfig;
import com.zerobias.module.x12.buffer.TestRows.MutableClock;
import com.zerobias.module.x12.materializer.StructureIndex;
import com.zerobias.module.x12.materializer.StructureResolver;
import com.zerobias.module.x12.parser.Fixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Per-file isolation in the consume path (DESIGN §4.2): size is checked before reading, one
 * file's failure never ends the scan, a buffer failure does, links are not followed, the file
 * read is the file the stability window saw, and a rename never replaces an existing file.
 */
class FileIsolationTest {

    private static final Instant T0 = Instant.parse("2026-09-22T12:00:00Z");

    private BufferStore buffer;

    @AfterEach
    void tearDown() throws Exception {
        if (buffer != null) {
            buffer.close();
        }
    }

    private static ModuleRuntimeConfig config(Path inbox, long maxFileBytes) {
        return new ModuleRuntimeConfig(List.of(new SourceConfig("inbox", inbox.toString(), "*", 1, 0)),
            ".done", ".error", false, RetentionConfig.none(), false, false, maxFileBytes);
    }

    private InboxPoller poller(Path dir, ModuleRuntimeConfig cfg, StructureResolver resolver) throws Exception {
        MutableClock clock = new MutableClock(T0);
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false, clock);
        FileConsumer consumer = new FileConsumer(buffer, null, cfg, resolver, clock);
        return new InboxPoller(cfg.sources().get(0), consumer, buffer, clock, ".done", ".error");
    }

    private static Path inbox(Path dir) throws Exception {
        return Files.createDirectories(dir.resolve("inbox"));
    }

    /** The 835 fixture re-numbered as interchange {@code isa13}: same guide, different bytes. */
    private static byte[] another835(String isa13) {
        return Fixtures.text(Fixtures.F835)
            .replace("*000000101*0*T*", "*" + isa13 + "*0*T*")
            .replace("IEA*1*000000101~", "IEA*1*" + isa13 + "~")
            .getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void anOversizedFileIsSentToErrorFromItsStat(@TempDir Path dir) throws Exception {
        Path inbox = inbox(dir);
        byte[] bytes = Fixtures.bytes(Fixtures.F835);
        InboxPoller p = poller(dir, config(inbox, bytes.length - 1), new StructureResolver());
        Path f = Files.write(inbox.resolve("big.835"), bytes);

        p.scan();
        String fileId = FileConsumer.fileId(f, bytes);
        FileRow row = buffer.fileById(fileId).orElseThrow(() -> new AssertionError("error row keyed by the streamed hash"));
        assertEquals(FileStatus.ERROR, row.status());
        assertTrue(row.errorMessage().startsWith("too-large: " + bytes.length + " bytes exceeds maxFileBytes"),
            row.errorMessage());
        assertEquals(FileConsumer.sha256(bytes), row.checksum(), "hashed as a stream, whole");
        assertEquals(0, buffer.count());
        assertTrue(Files.exists(inbox.resolve("big.835.error")));
    }

    @Test
    void maxFileBytesDefaultsAndIsCapped() {
        assertEquals(ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES, ModuleRuntimeConfig.defaults().maxFileBytes());
        assertEquals(64L * 1024 * 1024, ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES);
        assertEquals(ModuleRuntimeConfig.MAX_MAX_FILE_BYTES,
            ModuleRuntimeConfig.parse("{\"maxFileBytes\":" + Long.MAX_VALUE + "}").maxFileBytes());
        assertEquals(1000L, ModuleRuntimeConfig.parse("{\"maxFileBytes\":1000}").maxFileBytes());
        assertEquals(ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES,
            ModuleRuntimeConfig.parse("{\"maxFileBytes\":0}").maxFileBytes());
    }

    @Test
    void anOutOfMemoryParsingOneFileDoesNotStopTheScan(@TempDir Path dir) throws Exception {
        // The 835 guide "exhausts the heap"; the 837P after it in the listing must still be consumed.
        Path inbox = inbox(dir);
        StructureResolver resolver = new StructureResolver(gs08 -> {
            if (gs08.startsWith("005010X221")) {
                throw new OutOfMemoryError("simulated: parser tree for this file");
            }
            return StructureIndex.fromClasspath(gs08);
        });
        InboxPoller p = poller(dir, config(inbox, ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES), resolver);
        Path a = Files.write(inbox.resolve("a.835"), Fixtures.bytes(Fixtures.F835));
        Files.write(inbox.resolve("b.837"), Fixtures.bytes(Fixtures.F837P));

        p.scan();
        FileRow ra = buffer.fileById(FileConsumer.fileId(a, Fixtures.bytes(Fixtures.F835))).orElseThrow();
        assertEquals(FileStatus.ERROR, ra.status(), "the file that blew up is that file's error");
        assertTrue(ra.errorMessage().contains("OutOfMemoryError"), ra.errorMessage());
        assertTrue(Files.exists(inbox.resolve("a.835.error")));
        assertTrue(Files.exists(inbox.resolve("b.837.done")), "the next file was still consumed");
        assertEquals(1, buffer.fileCount(FileStatus.CONSUMED));
    }

    @Test
    void aUniqueClashOnOneFileDoesNotStopTheScan(@TempDir Path dir) throws Exception {
        // A UNIQUE violation other than the element key (here an index an operator could add, or
        // any per-row key) is SQLite refusing THIS file's rows: .error it and move on.
        Path inbox = inbox(dir);
        InboxPoller p = poller(dir, config(inbox, ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES), new StructureResolver());
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("buffer.db"));
             Statement st = c.createStatement()) {
            st.execute("CREATE UNIQUE INDEX one_per_guide ON transactions(gs08)");
        }
        Files.write(inbox.resolve("a.835"), another835("000000101"));
        Path b = Files.write(inbox.resolve("b.835"), another835("000000201"));
        Files.write(inbox.resolve("c.837"), Fixtures.bytes(Fixtures.F837P));

        p.scan();
        assertTrue(Files.exists(inbox.resolve("a.835.done")));
        FileRow rb = buffer.fileById(FileConsumer.fileId(b, another835("000000201"))).orElseThrow();
        assertEquals(FileStatus.ERROR, rb.status());
        assertTrue(rb.errorMessage().startsWith("buffer-rejected"), rb.errorMessage());
        assertTrue(Files.exists(inbox.resolve("b.835.error")));
        assertTrue(Files.exists(inbox.resolve("c.837.done")), "the scan went on past the refused file");
        assertEquals(2, buffer.count());
    }

    @Test
    void aBufferFailureEndsTheScanAndLeavesFilesInPlace(@TempDir Path dir) throws Exception {
        // NOT NULL / trigger aborts mean the table no longer accepts its INSERTs (the mapped_json
        // upgrade bug): not the file's fault, so no .error — surface it and leave the files.
        Path inbox = inbox(dir);
        InboxPoller p = poller(dir, config(inbox, ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES), new StructureResolver());
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("buffer.db"));
             Statement st = c.createStatement()) {
            st.execute("CREATE TRIGGER broken BEFORE INSERT ON transactions BEGIN SELECT RAISE(ABORT, 'x'); END");
        }
        Path a = Files.write(inbox.resolve("a.835"), Fixtures.bytes(Fixtures.F835));
        Path b = Files.write(inbox.resolve("b.837"), Fixtures.bytes(Fixtures.F837P));

        assertThrows(SQLException.class, p::scan);
        assertTrue(Files.exists(a) && Files.exists(b), "nothing renamed");
        assertEquals(0, buffer.fileCount(), "no error rows blaming the files");
    }

    @Test
    void symlinksAreNeverFollowed(@TempDir Path dir) throws Exception {
        Path inbox = inbox(dir);
        Path outside = Files.write(dir.resolve("elsewhere.835"), Fixtures.bytes(Fixtures.F835));
        Path link = Files.createSymbolicLink(inbox.resolve("link.835"), outside);
        InboxPoller p = poller(dir, config(inbox, ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES), new StructureResolver());

        assertEquals(0, p.scan().scanned(), "a link is not a candidate");
        assertTrue(Files.isSymbolicLink(link), "the link is left alone");
        assertTrue(Files.exists(outside), "its target is untouched");
        assertEquals(0, buffer.fileCount());
        assertEquals(0, buffer.count());
    }

    @Test
    void aFileThatChangedAfterItsWindowIsLeftForTheNextScan(@TempDir Path dir) throws Exception {
        Path inbox = inbox(dir);
        MutableClock clock = new MutableClock(T0);
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false, clock);
        ModuleRuntimeConfig cfg = config(inbox, ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES);
        FileConsumer consumer = new FileConsumer(buffer, null, cfg, new StructureResolver(), clock);
        Path f = Files.write(inbox.resolve("late.835"), Fixtures.bytes(Fixtures.F835));
        Instant seenMtime = Files.getLastModifiedTime(f).toInstant();
        // the window saw it at this (size, mtime) ...
        FileStability.Sighting seen = new FileStability.Sighting(Files.size(f), seenMtime, T0, T0);
        // ... and then it was touched again before the read
        Files.setLastModifiedTime(f, FileTime.from(seenMtime.plusSeconds(5)));

        FileConsumer.Result r = consumer.consume(cfg.sources().get(0), f, seen);
        assertEquals(FileConsumer.Outcome.CHANGED, r.outcome());
        assertTrue(Files.exists(f), "left in place");
        assertEquals(0, buffer.fileCount());
    }

    @Test
    void aRenameNeverReplacesAnExistingFile(@TempDir Path dir) throws Exception {
        Path inbox = inbox(dir);
        Path f = Files.writeString(inbox.resolve("x.835"), "new bytes");
        Path taken = Files.writeString(inbox.resolve("x.835.done"), "an earlier delivery's .done");
        // The planned target was free when chosen and taken before the move (another writer).
        Path actual = FileConsumer.moveNoClobber(f, taken, ".done", T0);
        assertEquals("an earlier delivery's .done", Files.readString(taken), "never overwritten");
        assertEquals(inbox.resolve("x.835." + T0.toEpochMilli() + ".done"), actual);
        assertEquals("new bytes", Files.readString(actual));
        assertFalse(Files.exists(f));
    }

    @Test
    void theRejectsThisFileClassificationSparesSchemaFailures() {
        assertTrue(FileConsumer.rejectsThisFile(new com.zerobias.module.x12.buffer.DuplicateElementKeyException("k")));
        assertFalse(FileConsumer.rejectsThisFile(new SQLException("disk I/O error", null, 10)));
        assertTrue(FileConsumer.rejectsThisFile(new SQLException("too big", null, 18)));
    }

    @Test
    void closeLetsTheFileInHandFinishAndLeavesTheRest(@TempDir Path dir) throws Exception {
        Path inbox = inbox(dir);
        java.util.concurrent.CountDownLatch inScan = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        StructureResolver resolver = new StructureResolver(gs08 -> {
            if (gs08.startsWith("005010X221")) {
                inScan.countDown();
                boolean interrupted = false;
                while (true) {
                    try {
                        release.await();
                        break;
                    } catch (InterruptedException e) {
                        interrupted = true;   // close() interrupts the scan thread; finish the file anyway
                    }
                }
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            return StructureIndex.fromClasspath(gs08);
        });
        ModuleRuntimeConfig cfg = config(inbox, ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES);
        InboxPoller p = poller(dir, cfg, resolver);
        Files.write(inbox.resolve("a.835"), Fixtures.bytes(Fixtures.F835));
        Path b = Files.write(inbox.resolve("b.837"), Fixtures.bytes(Fixtures.F837P));
        p.start();
        assertTrue(inScan.await(10, java.util.concurrent.TimeUnit.SECONDS), "scan reached the first file");

        Thread closer = new Thread(p::close);
        closer.start();
        Thread.sleep(200);
        release.countDown();
        closer.join(10_000);
        assertFalse(closer.isAlive(), "close returned");
        assertTrue(Files.exists(inbox.resolve("a.835.done")), "the file in hand was finished and acknowledged");
        assertTrue(Files.exists(b), "the rest waits for the next start");
        assertEquals(1, buffer.fileCount());
        assertFalse(p.up());
    }
}

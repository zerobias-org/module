package com.zerobias.module.x12.inbox;

import com.zerobias.module.x12.ModuleRuntimeConfig;
import com.zerobias.module.x12.PollerHandle.RescanResult;
import com.zerobias.module.x12.SourceConfig;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.FileRow;
import com.zerobias.module.x12.buffer.FileStatus;
import com.zerobias.module.x12.buffer.RetentionConfig;
import com.zerobias.module.x12.buffer.TestRows.MutableClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A file within {@code maxFileBytes} whose bytes the heap cannot hold goes to {@code .error}
 * instead of failing every scan. The allocation must fail for real, so this class runs alone in
 * a JVM whose heap is smaller than the file (the {@code small-heap} surefire execution in
 * pom.xml); in any larger heap it skips itself.
 */
class FileConsumerSmallHeapTest {

    private static final long FILE_BYTES = 112L * 1024 * 1024;

    @Test
    void aFileTheHeapCannotHoldIsErroredWithAStreamedIdentity(@TempDir Path dir) throws Exception {
        assumeTrue(Runtime.getRuntime().maxMemory() < FILE_BYTES,
            "needs a heap smaller than the file: runs in the small-heap surefire execution");
        Path inbox = Files.createDirectories(dir.resolve("inbox"));
        Path big = inbox.resolve("big.835");
        try (RandomAccessFile f = new RandomAccessFile(big.toFile(), "rw")) {
            f.setLength(FILE_BYTES);   // sparse: no disk, but a real FILE_BYTES-long read
        }
        String checksum = streamedSha256(big);
        ModuleRuntimeConfig cfg = new ModuleRuntimeConfig(
            List.of(new SourceConfig("inbox", inbox.toString(), "*", 1, 0)),
            ".done", ".error", false, RetentionConfig.none(), false, ModuleRuntimeConfig.MAX_MAX_FILE_BYTES);
        MutableClock clock = new MutableClock(Instant.parse("2026-09-22T12:00:00Z"));
        try (BufferStore buffer = new BufferStore(dir.resolve("buffer.db").toString(), false, clock)) {
            X12InboxPollerFactory.Handle handle = X12InboxPollerFactory.start(cfg, buffer, null, clock, false);
            try {
                assertEquals(new RescanResult(1, 1, 0, 1), handle.rescan(null));
                assertTrue(Files.exists(Path.of(big + ".error")), "sent to .error, not left to fail again");
                assertFalse(Files.exists(big));
                FileRow row = buffer.fileById(FileRow.fileId(big.toString(), checksum)).orElseThrow();
                assertEquals(FileStatus.ERROR, row.status());
                assertTrue(row.errorMessage().startsWith("too-large-for-heap: " + FILE_BYTES + " bytes"), row.errorMessage());
                assertEquals(checksum, row.checksum(), "hashed as a stream");
                assertEquals(0, handle.sources().get(0).consecutiveFailures(), "the file's error, not a failed scan");
                assertEquals(new RescanResult(0, 0, 0, 0), handle.rescan(null), "nothing left to retry");
            } finally {
                handle.close();
            }
        }
    }

    private static String streamedSha256(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new DigestInputStream(Files.newInputStream(file), md)) {
            in.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(md.digest());
    }
}

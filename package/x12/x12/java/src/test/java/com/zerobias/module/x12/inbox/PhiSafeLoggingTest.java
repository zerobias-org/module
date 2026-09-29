package com.zerobias.module.x12.inbox;

import com.zerobias.module.x12.ModuleRuntimeConfig;
import com.zerobias.module.x12.SourceConfig;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.FileRow;
import com.zerobias.module.x12.buffer.FileStatus;
import com.zerobias.module.x12.buffer.RetentionConfig;
import com.zerobias.module.x12.buffer.TestRows.MutableClock;
import com.zerobias.module.x12.materializer.StructureResolver;
import com.zerobias.module.x12.parser.Fixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * X12 here is PHI, and parser messages quote it: {@code bad-gs} carries the raw GS segment,
 * a non-fatal scan error the element it choked on. The consumer logs the kind of a failure,
 * the file id and counts — never the message past its kind. The full message stays on the
 * {@code files} row, served only through the API.
 */
class PhiSafeLoggingTest {

    private static final Instant T0 = Instant.parse("2026-09-22T12:00:00Z");
    private static final String PHI = "DOEJOHNQPATIENT";

    private BufferStore buffer;

    @AfterEach
    void tearDown() throws Exception {
        if (buffer != null) {
            buffer.close();
        }
    }

    private InboxPoller poller(Path dir, Path inbox) throws Exception {
        MutableClock clock = new MutableClock(T0);
        ModuleRuntimeConfig cfg = new ModuleRuntimeConfig(List.of(new SourceConfig("inbox", inbox.toString(), "*", 1, 0)),
            ".done", ".error", false, RetentionConfig.none(), false, false);
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false, clock);
        return new InboxPoller(cfg.sources().get(0), new FileConsumer(buffer, null, cfg, new StructureResolver(), clock),
            buffer, clock, ".done", ".error");
    }

    @Test
    void aParseErrorIsLoggedByKindWithoutTheSegmentItQuotes(@TempDir Path dir) throws Exception {
        Path inbox = Files.createDirectories(dir.resolve("inbox"));
        InboxPoller p = poller(dir, inbox);
        // A GS without GS08: the parser's message quotes the whole GS segment, GS02 included.
        byte[] bytes = Fixtures.text(Fixtures.F835)
            .replace("GS*HP*EXAMPLEPAYER*EXAMPLEPROV*20260922*1200*101*X*005010X221A1~",
                "GS*HP*" + PHI + "*EXAMPLEPROV*20260922*1200*101*X*~")
            .getBytes(StandardCharsets.UTF_8);
        Path f = Files.write(inbox.resolve("bad.835"), bytes);
        String fileId = InboxFixture.fileId(f, bytes);

        String log = LogCapture.of(p::scan);
        FileRow row = buffer.fileById(fileId).orElseThrow();
        assertEquals(FileStatus.ERROR, row.status());
        assertTrue(row.errorMessage().startsWith("bad-gs") && row.errorMessage().contains(PHI),
            "the stored message keeps the detail: " + row.errorMessage());
        assertTrue(log.contains(fileId) && log.contains("bad-gs"), "the log names the file and the kind:\n" + log);
        assertFalse(log.contains(PHI), "segment content reached the log:\n" + log);

        // The operator renames it back: the retry line quoted the stored message too.
        Files.move(inbox.resolve("bad.835.error"), f);
        String retry = LogCapture.of(p::scan);
        assertTrue(retry.contains("retry: " + fileId), retry);
        assertFalse(retry.contains(PHI), "segment content reached the log on retry:\n" + retry);
    }

    @Test
    void nonFatalParserErrorsAreLoggedAsACount(@TempDir Path dir) throws Exception {
        Path inbox = Files.createDirectories(dir.resolve("inbox"));
        InboxPoller p = poller(dir, inbox);
        // SE02 that disagrees with ST02 is non-fatal; the scan's error text quotes SE02.
        String text = Fixtures.text(Fixtures.F835);
        int se = text.lastIndexOf("SE*");
        int end = text.indexOf('~', se);
        String seSegment = text.substring(se, end);
        byte[] bytes = text.replace(seSegment, seSegment.substring(0, seSegment.lastIndexOf('*') + 1) + PHI)
            .getBytes(StandardCharsets.UTF_8);
        Path f = Files.write(inbox.resolve("odd.835"), bytes);

        String log = LogCapture.of(p::scan);
        FileRow row = buffer.fileById(InboxFixture.fileId(f, bytes)).orElseThrow();
        assertEquals(FileStatus.CONSUMED, row.status(), "non-fatal: " + row.errorMessage());
        assertTrue(log.contains("non-fatal parser error"), "the count is still logged:\n" + log);
        assertFalse(log.contains(PHI), "an element value reached the log:\n" + log);
    }

    @Test
    void kindIsTheHeadOfTheMessage() {
        assertEquals("bad-gs", FileConsumer.kind("bad-gs: GS segment has no GS08 (GS*HP*" + PHI + ")"));
        assertEquals("too-large", FileConsumer.kind("too-large: 9 bytes exceeds maxFileBytes 8"));
        assertEquals("internal: java.lang.IllegalStateException",
            FileConsumer.kind("internal: java.lang.IllegalStateException: CLP*" + PHI));
        assertEquals("unknown", FileConsumer.kind(null));
    }
}

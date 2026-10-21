package com.zerobias.module.ocsf.inbox;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zerobias.module.ocsf.ModuleRuntimeConfig;
import com.zerobias.module.ocsf.PollerHandle.RescanResult;
import com.zerobias.module.ocsf.SourceConfig;
import com.zerobias.module.ocsf.TestSupport;
import com.zerobias.module.ocsf.TestSupport.MutableClock;
import com.zerobias.module.ocsf.buffer.BufferStore;
import com.zerobias.module.ocsf.buffer.FileRow;
import com.zerobias.module.ocsf.buffer.FileStatus;
import com.zerobias.module.ocsf.buffer.RetentionConfig;
import com.zerobias.module.ocsf.producer.ObjectTree;
import com.zerobias.module.ocsf.producer.OperationRouter;
import com.zerobias.module.ocsf.producer.ProducerFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static com.zerobias.module.ocsf.TestSupport.AUTHENTICATION;
import static com.zerobias.module.ocsf.TestSupport.event;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The folder-drop path end to end, without a container or a Hub: a file written into the inbox
 * is consumed by the real poller once the stability window passes, renamed {@code .done} or
 * {@code .error}, and its events are then served by the OCSF operations exactly as written.
 * The clock is a {@link MutableClock}, so the 60s window passes instantly.
 */
class InboxIngestTest {

    private static final Gson GSON = new Gson();
    private static final int FILE_ACTIVITY = 1001;
    private static final long MAX_FILE_BYTES = 4096;

    @TempDir
    Path dir;

    private Path inbox;
    private MutableClock clock;
    private BufferStore buffer;
    private InboxPollerFactory.Handle pollers;
    private ProducerFacade facade;

    @BeforeEach
    void setUp() throws Exception {
        inbox = Files.createDirectories(dir.resolve("inbox"));
        clock = new MutableClock(TestSupport.BASE);
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false, clock);
        ModuleRuntimeConfig cfg = new ModuleRuntimeConfig(
            List.of(new SourceConfig("inbox", inbox.toString(), "*.{json,ndjson,jsonl,gz}", 30, 60)),
            ".done", ".error", false, RetentionConfig.none(), false, MAX_FILE_BYTES, 1024);
        pollers = InboxPollerFactory.start(cfg, TestSupport.classes(), buffer, null, clock, false);
        ObjectTree tree = new ObjectTree(buffer, pollers, ".done", cfg.sources(), ".error");
        facade = new ProducerFacade(buffer, tree, TestSupport.classes(), false);
    }

    @AfterEach
    void tearDown() throws Exception {
        pollers.close();
        buffer.close();
    }

    /** One scan now, then one after the stability window: what a dropped file sees. */
    private RescanResult dropSettles() throws Exception {
        pollers.rescan(null);
        clock.advance(Duration.ofSeconds(61));
        return pollers.rescan(null);
    }

    private JsonObject call(String method, Map<String, Object> argMap) throws Exception {
        return GSON.fromJson(OperationRouter.executeOperation(facade, method, new HashMap<>(argMap)), JsonObject.class);
    }

    private FileRow onlyFile() throws Exception {
        List<FileRow> rows = buffer.fileRows(null, 10, 0);
        assertEquals(1, rows.size(), rows.toString());
        return rows.get(0);
    }

    @Test
    void aDroppedFileIsConsumedOnceStableThenServedVerbatim() throws Exception {
        String older = event(AUTHENTICATION, "auth-1", 1_000);
        // Key order, spacing and an attribute the interface does not declare survive as written.
        String newer = "{ \"time\": 2000, \"class_uid\": 3002, \"metadata\": {\"uid\": \"auth-2\"}, \"x_vendor\": [1, 2.50] }";
        Files.writeString(inbox.resolve("auth.ndjson"), older + "\n" + newer + "\n");

        assertEquals(new RescanResult(1, 1, 0, 0), pollers.rescan(null), "first sighting starts the window");
        assertTrue(Files.exists(inbox.resolve("auth.ndjson")), "not read while it may still be growing");
        clock.advance(Duration.ofSeconds(61));
        assertEquals(new RescanResult(1, 0, 1, 0), pollers.rescan(null));

        assertFalse(Files.exists(inbox.resolve("auth.ndjson")));
        assertTrue(Files.exists(inbox.resolve("auth.ndjson.done")), "the rename is the acknowledgement");
        FileRow f = onlyFile();
        assertEquals(FileStatus.CONSUMED, f.status());
        assertEquals(2, f.eventCount());
        assertEquals(0, f.rejectedCount());

        JsonObject page = call("IamApi.listAuthentication", Map.of());
        assertEquals(2, page.get("count").getAsInt());
        JsonArray items = page.getAsJsonArray("items");
        assertEquals("auth-2", items.get(0).getAsJsonObject().getAsJsonObject("metadata").get("uid").getAsString(),
            "newest first");
        String raw = OperationRouter.executeOperation(facade, "IamApi.getAuthentication", new HashMap<>(Map.of("uid", "auth-2")));
        assertEquals(newer, raw, "returned byte for byte, never re-serialized");

        JsonObject window = call("IamApi.listAuthentication", Map.of("startTime", 1_000, "endTime", 2_000));
        assertEquals(1, window.get("count").getAsInt(), "startTime inclusive, endTime exclusive");

        JsonArray classes = GSON.fromJson(OperationRouter.executeOperation(facade, "ClassesApi.listClasses", new HashMap<>()),
            JsonArray.class);
        assertEquals(1, classes.size());
        assertEquals(AUTHENTICATION, classes.get(0).getAsJsonObject().get("uid").getAsInt());
        assertEquals("iam", classes.get(0).getAsJsonObject().get("category").getAsString());
    }

    @Test
    void aFileStillBeingWrittenWaitsForTheWindowToRestart() throws Exception {
        Path f = inbox.resolve("auth.ndjson");
        Files.writeString(f, event(AUTHENTICATION, "a", 1) + "\n");
        pollers.rescan(null);
        clock.advance(Duration.ofSeconds(30));
        Files.writeString(f, event(AUTHENTICATION, "b", 2) + "\n", java.nio.file.StandardOpenOption.APPEND);
        pollers.rescan(null);   // the size changed: the window restarts here
        clock.advance(Duration.ofSeconds(40));
        assertEquals(0, pollers.rescan(null).consumed(), "only 40s unchanged since the append");
        clock.advance(Duration.ofSeconds(30));
        assertEquals(1, pollers.rescan(null).consumed());
        assertEquals(2, onlyFile().eventCount(), "both writes, read once");
    }

    @Test
    void badEventsAreRejectedOneByOneAndTheRestStored() throws Exception {
        Files.writeString(inbox.resolve("mixed.ndjson"), String.join("\n",
            event(AUTHENTICATION, "ok", 1),
            event(99_999, "unknown", 1),
            "{not json",
            "{\"time\":1}",
            "[1,2]",
            event(0, "base-event", 1)) + "\n");
        assertEquals(1, dropSettles().consumed());

        assertTrue(Files.exists(inbox.resolve("mixed.ndjson.done")), "a partial file is still acknowledged");
        FileRow f = onlyFile();
        assertEquals(FileStatus.PARTIAL, f.status());
        assertEquals(1, f.eventCount());
        assertEquals(5, f.rejectedCount());
        String why = f.errorMessage();
        assertTrue(why.contains("line 2: unknown-class"), why);
        assertTrue(why.contains("line 3: invalid-json"), why);
        assertTrue(why.contains("line 4: no-class_uid"), why);
        assertTrue(why.contains("line 5: not-an-object"), why);
        assertTrue(why.contains("line 6: unknown-class: class_uid 0"), "base_event has no operations, so nothing could read it");
    }

    @Test
    void aFileWithNothingUsableGoesToError() throws Exception {
        Files.writeString(inbox.resolve("empty.ndjson"), "\n\n");
        Files.writeString(inbox.resolve("broken.json"), "{\"class_uid\": 3002,");
        Files.writeString(inbox.resolve("rejected.jsonl"), event(99_999, "x", 1) + "\n");
        assertEquals(3, dropSettles().errored());

        for (String name : List.of("empty.ndjson", "broken.json", "rejected.jsonl")) {
            assertTrue(Files.exists(inbox.resolve(name + ".error")), name);
        }
        Map<String, String> why = new HashMap<>();
        for (FileRow f : buffer.fileRows(null, 10, 0)) {
            assertEquals(FileStatus.ERROR, f.status(), f.fileName());
            why.put(f.fileName(), f.errorMessage());
        }
        assertTrue(why.get("empty.ndjson").startsWith("empty"), why.toString());
        assertTrue(why.get("broken.json").startsWith("invalid-json"), why.toString());
        assertTrue(why.get("rejected.jsonl").startsWith("all-rejected"), why.toString());
        assertEquals(0, buffer.eventCount());
    }

    @Test
    void aRedeliveredEventIsCountedNotStoredAndAConflictIsKeptUnderItsOwnKey() throws Exception {
        String original = event(AUTHENTICATION, "same-uid", 1);
        Files.writeString(inbox.resolve("a.ndjson"), original + "\n");
        dropSettles();

        Files.writeString(inbox.resolve("copy.ndjson"), original + "\n");   // the whole file again
        Files.writeString(inbox.resolve("b.ndjson"), original + "\n" + event(AUTHENTICATION, "new-uid", 3) + "\n");
        Files.writeString(inbox.resolve("c.ndjson"), event(AUTHENTICATION, "same-uid", 2) + "\n");   // same uid, new bytes
        assertEquals(3, dropSettles().consumed());

        Map<String, FileRow> byName = new HashMap<>();
        for (FileRow f : buffer.fileRows(null, 10, 0)) {
            byName.put(f.fileName(), f);
        }
        assertEquals(FileStatus.DUPLICATE, byName.get("copy.ndjson").status(), "same checksum: caught before splitting");
        assertEquals(1, byName.get("b.ndjson").eventCount());
        assertEquals(1, byName.get("b.ndjson").duplicateCount(), "same uid and bytes inside a new file");
        assertEquals(1, byName.get("c.ndjson").eventCount(), "never an overwrite");

        assertEquals(3, call("IamApi.listAuthentication", Map.of()).get("count").getAsInt());
        assertEquals(original, OperationRouter.executeOperation(facade, "IamApi.getAuthentication",
            new HashMap<>(Map.of("uid", "same-uid"))), "the first copy keeps the plain key");
    }

    @Test
    void gzipIsInflatedButNeverPastMaxFileBytes() throws Exception {
        Files.write(inbox.resolve("files.ndjson.gz"), gzip(event(FILE_ACTIVITY, "f1", 1) + "\n"));
        Files.write(inbox.resolve("bomb.ndjson.gz"), gzip(" ".repeat((int) MAX_FILE_BYTES * 4)));
        RescanResult r = dropSettles();
        assertEquals(1, r.consumed());
        assertEquals(1, r.errored());

        assertTrue(Files.exists(inbox.resolve("bomb.ndjson.gz.error")));
        assertEquals(1, call("SystemApi.listFileActivity", Map.of()).get("count").getAsInt());
    }

    @Test
    void dotfilesAndOtherNamesAreLeftAlone() throws Exception {
        Files.writeString(inbox.resolve(".auth.ndjson.part"), event(AUTHENTICATION, "p", 1) + "\n");
        Files.writeString(inbox.resolve("notes.txt"), "hello");
        Files.createDirectories(inbox.resolve("sub")).resolve("auth.ndjson").toFile().createNewFile();
        RescanResult r = dropSettles();
        assertEquals(0, r.consumed() + r.errored(), r.toString());
        assertTrue(Files.exists(inbox.resolve(".auth.ndjson.part")), "an upload in progress is not touched");
        assertTrue(Files.exists(inbox.resolve("notes.txt")));
        assertTrue(Files.exists(inbox.resolve("sub/auth.ndjson")), "sources are scanned flat");
        assertEquals(0, buffer.fileCount());
    }

    private static byte[] gzip(String text) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }
}

package com.zerobias.module.ocsf.producer;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zerobias.module.ocsf.SourceConfig;
import com.zerobias.module.ocsf.buffer.BufferStore;
import com.zerobias.module.ocsf.TestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DESIGN §2.9: the live {@code /inbox} branch and the file-management write surface.
 *
 * <p>Two properties this class exists to pin down:
 * <ul>
 *   <li><b>Browse is never cached.</b> Listings come from a readdir, not from the buffer,
 *       so a file that has never been ingested is visible and a file deleted behind our
 *       back disappears — no invalidation step anywhere.</li>
 *   <li><b>The gate is the config flag.</b> With {@code allowFileManagement=false} every
 *       write is an {@code err.unsupported.operation}; reads are unaffected either way.</li>
 * </ul>
 */
class InboxFilesTest {

    private static final Gson GSON = new Gson();
    private static final String R = ObjectTree.RECEIVER;
    private static final String INBOX = R + "/inbox";
    private static final byte[] EVENT = "{\"class_uid\":3002,\"time\":1}\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path dir;
    private Path inboxDir;
    private BufferStore buffer;
    private ObjectTree tree;
    private ProducerFacade readOnly;
    private ProducerFacade writable;

    @BeforeEach
    void setUp() throws Exception {
        inboxDir = Files.createDirectories(dir.resolve("inbox"));
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false,
            new TestSupport.MutableClock(TestSupport.BASE));
        SourceConfig source = new SourceConfig("inbox", inboxDir.toString(), "*.{json,ndjson}", 1, 0);
        TestSupport.StubPoller poller = new TestSupport.StubPoller(inboxDir);
        tree = new ObjectTree(buffer, poller, ".done", List.of(source), ".error");
        readOnly = new ProducerFacade(buffer, tree, TestSupport.classes(), false);
        writable = new ProducerFacade(buffer, tree, TestSupport.classes(), true);
    }

    @AfterEach
    void close() throws Exception {
        buffer.close();
    }

    // --- live browse --------------------------------------------------------

    @Test
    void browseReflectsTheVolumeOnEveryCallWithNoBufferRows() throws Exception {
        assertEquals(0, buffer.fileCount(), "nothing ingested: /files would be empty");

        JsonObject sources = page(readOnly.getChildren(INBOX, 100, 1));
        assertEquals(1, sources.get("count").getAsInt());
        assertEquals("inbox", item(sources, 0).get("name").getAsString());
        String sourceId = item(sources, 0).get("id").getAsString();
        assertEquals(INBOX + "/inbox", sourceId);

        assertEquals(0, page(readOnly.getChildren(sourceId, 100, 1)).get("count").getAsInt());

        // Land a file the way a feed would — no API involved, no poller running.
        Files.write(inboxDir.resolve("auth.ndjson"), EVENT);
        JsonObject after = page(readOnly.getChildren(sourceId, 100, 1));
        assertEquals(1, after.get("count").getAsInt(), "a fresh readdir, not a cached listing");
        JsonObject file = item(after, 0);
        assertEquals("auth.ndjson", file.get("name").getAsString());
        assertEquals(List.of("binary"), classes(file));
        assertEquals(EVENT.length, file.get("size").getAsLong());
        assertEquals("watched", file.get("ingest").getAsString());
        assertEquals(0, buffer.fileCount(), "still nothing in the buffer — this view is the volume");

        // ...and it goes away again the moment the bytes do.
        Files.delete(inboxDir.resolve("auth.ndjson"));
        assertEquals(0, page(readOnly.getChildren(sourceId, 100, 1)).get("count").getAsInt());
    }

    @Test
    void ingestFieldSaysWhatThePollerWillDo() throws Exception {
        Files.write(inboxDir.resolve("good.ndjson"), EVENT);
        Files.write(inboxDir.resolve("notes.txt"), EVENT);
        Files.write(inboxDir.resolve("old.ndjson.done"), EVENT);
        Path sub = Files.createDirectory(inboxDir.resolve("siem-a"));
        Files.write(sub.resolve("nested.ndjson"), EVENT);

        assertEquals("watched", ingest(INBOX + "/inbox/good.ndjson"));
        assertEquals("ignored:pattern", ingest(INBOX + "/inbox/notes.txt"), "*.{json,ndjson} does not match");
        assertEquals("ignored:suffix", ingest(INBOX + "/inbox/old.ndjson.done"), "already consumed");
        assertEquals("ignored:subdirectory", ingest(INBOX + "/inbox/siem-a/nested.ndjson"),
            "the poller scans each source flat");
    }

    @Test
    void listingsHideDotfilesAndPutDirectoriesFirst() throws Exception {
        Files.createDirectory(inboxDir.resolve("zzz-dir"));
        Files.write(inboxDir.resolve("aaa.ndjson"), EVENT);
        Files.write(inboxDir.resolve(".hidden.ndjson"), EVENT);

        assertEquals(List.of("zzz-dir", "aaa.ndjson"),
            names(page(readOnly.getChildren(INBOX + "/inbox", 100, 1))),
            "directories first, then files; dotfiles invisible exactly as the poller sees them");
    }

    @Test
    void traversalAndUnknownSourcesAre404() {
        for (String id : List.of(INBOX + "/inbox/../../etc", INBOX + "/inbox/%2E%2E/x",
                INBOX + "/nope", INBOX + "/inbox/missing.ndjson")) {
            assertEquals(404, assertThrows(ProducerException.class,
                () -> readOnly.getObject(id)).httpStatus(), id);
        }
    }

    @Test
    void downloadServesBytesThatWereNeverIngested() throws Exception {
        Files.write(inboxDir.resolve("raw.ndjson"), EVENT);
        BinaryContent bin = readOnly.downloadBinary(INBOX + "/inbox/raw.ndjson");
        assertEquals("raw.ndjson", bin.fileName());
        try (java.io.InputStream in = bin.open()) {
            assertEquals(new String(EVENT, StandardCharsets.UTF_8), new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertEquals(EVENT.length, bin.size());
    }

    @Test
    void filesystemFailuresDoNotLeakContainerPaths() throws Exception {
        Path locked = Files.createDirectories(inboxDir.resolve("locked"));
        assertTrue(locked.toFile().setWritable(false, false));
        try {
            org.junit.jupiter.api.Assumptions.assumeFalse(Files.isWritable(locked), "running as root");
            ProducerException e = assertThrows(ProducerException.class,
                () -> writable.createChildObject(INBOX + "/inbox/locked", "sub", List.of("container")));
            assertEquals(500, e.httpStatus());
            assertEquals("err.unexpected", e.key());
            String wire = GSON.toJson(e.toBody());
            assertFalse(wire.contains(dir.toString()), wire);
            assertFalse(wire.contains("AccessDenied"), wire);
        } finally {
            locked.toFile().setWritable(true, false);
        }
    }

    @Test
    void symlinksCannotEscapeTheSourceRoot() throws Exception {
        Path outside = Files.createDirectories(dir.resolve("outside"));
        Files.write(outside.resolve("secret.ndjson"), EVENT);
        Files.createSymbolicLink(inboxDir.resolve("escape"), outside);               // a directory link
        Files.createSymbolicLink(inboxDir.resolve("leak.ndjson"), outside.resolve("secret.ndjson")); // a leaf link
        Files.createDirectories(inboxDir.resolve("real"));
        Files.createSymbolicLink(inboxDir.resolve("real").resolve("up"), outside);  // a link below a real dir
        Files.write(inboxDir.resolve("ok.ndjson"), EVENT);

        for (String id : List.of(INBOX + "/inbox/escape", INBOX + "/inbox/escape/secret.ndjson",
                INBOX + "/inbox/leak.ndjson", INBOX + "/inbox/real/up/secret.ndjson")) {
            assertEquals(404, assertThrows(ProducerException.class, () -> readOnly.getObject(id)).httpStatus(), id);
            assertEquals(404, assertThrows(ProducerException.class, () -> readOnly.downloadBinary(id)).httpStatus(), id);
        }
        assertEquals(404, assertThrows(ProducerException.class,
            () -> readOnly.getChildren(INBOX + "/inbox/escape", 100, 1)).httpStatus());
        // writes through a link are refused and nothing lands outside
        assertEquals(404, assertThrows(ProducerException.class,
            () -> writable.uploadBinary(INBOX + "/inbox/escape", "planted.ndjson", EVENT)).httpStatus());
        assertEquals(404, assertThrows(ProducerException.class,
            () -> writable.createChildObject(INBOX + "/inbox/real/up", "d", List.of("container"))).httpStatus());
        assertEquals(404, assertThrows(ProducerException.class,
            () -> writable.deleteObject(INBOX + "/inbox/escape/secret.ndjson")).httpStatus());
        assertFalse(Files.exists(outside.resolve("planted.ndjson")));
        assertTrue(Files.exists(outside.resolve("secret.ndjson")));
        // the listing still works and simply does not show links
        assertEquals(List.of("real", "ok.ndjson"), names(page(readOnly.getChildren(INBOX + "/inbox", 100, 1))));
        assertEquals(List.of(), names(page(readOnly.getChildren(INBOX + "/inbox/real", 100, 1))));
    }

    // --- the gate -----------------------------------------------------------

    @Test
    void everyWriteIsRefusedWhileTheFlagIsOff() throws Exception {
        Files.write(inboxDir.resolve("auth.ndjson"), EVENT);
        assertFalse(readOnly.fileManagementEnabled());

        ProducerException upload = assertThrows(ProducerException.class,
            () -> readOnly.uploadBinary(INBOX + "/inbox", "new.ndjson", EVENT));
        assertEquals(400, upload.httpStatus());
        assertEquals("err.unsupported.operation", upload.key());
        assertTrue(upload.getMessage().contains("allowFileManagement"), "the message names the flag");

        assertEquals("err.unsupported.operation", assertThrows(ProducerException.class,
            () -> readOnly.createChildObject(INBOX + "/inbox", "siem-a", List.of("container"))).key());
        assertEquals("err.unsupported.operation", assertThrows(ProducerException.class,
            () -> readOnly.deleteObject(INBOX + "/inbox/auth.ndjson")).key());

        assertTrue(Files.exists(inboxDir.resolve("auth.ndjson")), "a refused delete touches nothing");
        // Reading the same branch stays available with the flag off.
        assertEquals(1, page(readOnly.getChildren(INBOX + "/inbox", 100, 1)).get("count").getAsInt());
    }

    // --- upload -------------------------------------------------------------

    @Test
    void uploadLandsAtomicallyAndIsImmediatelyBrowsable() throws Exception {
        JsonObject created = GSON.fromJson(
            writable.uploadBinary(INBOX + "/inbox", "auth.ndjson", EVENT), JsonObject.class);

        assertEquals(INBOX + "/inbox/auth.ndjson", created.get("id").getAsString());
        assertEquals(List.of("binary"), classes(created));
        assertEquals("watched", created.get("ingest").getAsString(), "it will be picked up");
        assertArrayEqualsBytes(EVENT, Files.readAllBytes(inboxDir.resolve("auth.ndjson")));

        try (var entries = Files.list(inboxDir)) {
            List<String> left = new ArrayList<>();
            entries.forEach(p -> left.add(p.getFileName().toString()));
            assertEquals(List.of("auth.ndjson"), left, "no .part-* temporary left behind");
        }
        assertEquals(1, page(readOnly.getChildren(INBOX + "/inbox", 100, 1)).get("count").getAsInt());
        assertEquals(0, buffer.fileCount(), "upload does not fabricate a consumed-file row");
    }

    @Test
    void uploadNeverReplacesAndValidatesTheName() throws Exception {
        writable.uploadBinary(INBOX + "/inbox", "auth.ndjson", EVENT);

        assertEquals("err.illegal.argument", assertThrows(ProducerException.class,
            () -> writable.uploadBinary(INBOX + "/inbox", "auth.ndjson", EVENT)).key(),
            "replacing bytes at a path would fork its fileId");

        for (String bad : List.of("../escape.ndjson", "sub/child.ndjson", ".hidden.ndjson", "  ")) {
            assertEquals(400, assertThrows(ProducerException.class,
                () -> writable.uploadBinary(INBOX + "/inbox", bad, EVENT)).httpStatus(), bad);
        }
        assertEquals(400, assertThrows(ProducerException.class,
            () -> writable.uploadBinary(INBOX, "auth.ndjson", EVENT)).httpStatus(),
            "the branch root is not a directory");
    }

    @Test
    void uploadTargetsAnyDirectoryUnderTheSource() throws Exception {
        writable.createChildObject(INBOX + "/inbox", "siem-a", List.of("container"));
        JsonObject created = GSON.fromJson(
            writable.uploadBinary(INBOX + "/inbox/siem-a", "nested.ndjson", EVENT), JsonObject.class);

        assertEquals(INBOX + "/inbox/siem-a/nested.ndjson", created.get("id").getAsString());
        assertEquals("ignored:subdirectory", created.get("ingest").getAsString(),
            "visible and downloadable, but the flat poller will not consume it");
        assertTrue(Files.isRegularFile(inboxDir.resolve("siem-a").resolve("nested.ndjson")));
    }

    @Test
    void base64UploadThroughTheJsonEnvelopeIsEquivalent() throws Exception {
        // The shape OcsfApiServer.upload() decodes for a JSON-only invoker.
        byte[] decoded = Base64.getDecoder().decode(Base64.getEncoder().encodeToString(EVENT));
        writable.uploadBinary(INBOX + "/inbox", "encoded.ndjson", decoded);
        assertArrayEqualsBytes(EVENT, Files.readAllBytes(inboxDir.resolve("encoded.ndjson")));
    }

    // --- mkdir / delete -----------------------------------------------------

    @Test
    void mkdirAndDeleteRoundTrip() throws Exception {
        JsonObject made = GSON.fromJson(
            writable.createChildObject(INBOX + "/inbox", "siem-a", List.of("container")), JsonObject.class);
        assertEquals(INBOX + "/inbox/siem-a", made.get("id").getAsString());
        assertEquals(List.of("container"), classes(made));
        assertTrue(Files.isDirectory(inboxDir.resolve("siem-a")));

        assertEquals("err.illegal.argument", assertThrows(ProducerException.class,
            () -> writable.createChildObject(INBOX + "/inbox", "siem-a", List.of("container"))).key(),
            "creating it twice");
        assertEquals("err.illegal.argument", assertThrows(ProducerException.class,
            () -> writable.createChildObject(INBOX + "/inbox", "siem-b", List.of("collection"))).key(),
            "only containers can be created");

        JsonObject deleted = GSON.fromJson(writable.deleteObject(INBOX + "/inbox/siem-a"), JsonObject.class);
        assertEquals("deleted", deleted.get("status").getAsString());
        assertFalse(Files.exists(inboxDir.resolve("siem-a")));
    }

    @Test
    void deleteRefusesNonEmptyDirectoriesAndMountPoints() throws Exception {
        writable.createChildObject(INBOX + "/inbox", "siem-a", List.of("container"));
        writable.uploadBinary(INBOX + "/inbox/siem-a", "nested.ndjson", EVENT);

        assertEquals("err.illegal.argument", assertThrows(ProducerException.class,
            () -> writable.deleteObject(INBOX + "/inbox/siem-a")).key(),
            "a recursive delete over a live feed directory loses un-ingested events");
        assertEquals("err.unsupported.operation", assertThrows(ProducerException.class,
            () -> writable.deleteObject(INBOX + "/inbox")).key(), "a configured source is a mount point");
        assertEquals("err.unsupported.operation", assertThrows(ProducerException.class,
            () -> writable.deleteObject(INBOX)).key(), "the branch root");
        assertEquals(404, assertThrows(ProducerException.class,
            () -> writable.deleteObject(INBOX + "/inbox/gone.ndjson")).httpStatus());

        // Child first, then the directory — the order the API forces.
        writable.deleteObject(INBOX + "/inbox/siem-a/nested.ndjson");
        writable.deleteObject(INBOX + "/inbox/siem-a");
        assertFalse(Files.exists(inboxDir.resolve("siem-a")));
    }

    @Test
    void writesRejectTheEmergentBranches() {
        for (String id : List.of(R + "/files", R + "/stats", R)) {
            assertEquals("err.unsupported.operation", assertThrows(ProducerException.class,
                () -> writable.uploadBinary(id, "x.ndjson", EVENT)).key(), id);
            assertEquals("err.unsupported.operation", assertThrows(ProducerException.class,
                () -> writable.createChildObject(id, "sub", List.of("container"))).key(), id);
            assertEquals("err.unsupported.operation", assertThrows(ProducerException.class,
                () -> writable.deleteObject(id)).key(), id);
        }
    }

    // --- helpers ------------------------------------------------------------

    private String ingest(String id) throws Exception {
        return GSON.fromJson(readOnly.getObject(id), JsonObject.class).get("ingest").getAsString();
    }

    private static void assertArrayEqualsBytes(byte[] expected, byte[] actual) {
        assertEquals(new String(expected, StandardCharsets.UTF_8), new String(actual, StandardCharsets.UTF_8));
    }

    private static JsonObject page(String json) {
        return GSON.fromJson(json, JsonObject.class);
    }

    private static JsonObject item(JsonObject page, int i) {
        return page.getAsJsonArray("items").get(i).getAsJsonObject();
    }

    private static List<String> names(JsonObject page) {
        List<String> out = new ArrayList<>();
        JsonArray items = page.getAsJsonArray("items");
        for (int i = 0; i < items.size(); i++) {
            out.add(items.get(i).getAsJsonObject().get("name").getAsString());
        }
        return out;
    }

    private static List<String> classes(JsonObject o) {
        List<String> out = new ArrayList<>();
        for (var el : o.getAsJsonArray("objectClass")) {
            out.add(el.getAsString());
        }
        return out;
    }
}

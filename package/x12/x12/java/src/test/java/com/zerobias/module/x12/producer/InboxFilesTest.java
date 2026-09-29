package com.zerobias.module.x12.producer;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zerobias.module.x12.SourceConfig;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.TestRows;
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
    private static final String R = ObjectTreeApi.RECEIVER;
    private static final String INBOX = R + "/inbox";
    private static final byte[] EDI = "ISA*00*          *00*          *ZZ*SUB~".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path dir;
    private Path inboxDir;
    private BufferStore buffer;
    private ObjectTree tree;
    private X12ProducerFacade readOnly;
    private X12ProducerFacade writable;

    @BeforeEach
    void setUp() throws Exception {
        inboxDir = Files.createDirectories(dir.resolve("inbox"));
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false,
            new TestRows.MutableClock(TestRows.BASE));
        SourceConfig source = new SourceConfig("inbox", inboxDir.toString(), "*.{x12,835}", 1, 0);
        ProducerFixture.StubPoller poller = new ProducerFixture.StubPoller(inboxDir);
        tree = new ObjectTree(buffer, SchemaRegistryApi.EMPTY, () -> poller, ".done",
            List.of(source), ".error");
        readOnly = new X12ProducerFacade(buffer, tree, SchemaRegistryApi.EMPTY, OperationsApi.NONE, false);
        writable = new X12ProducerFacade(buffer, tree, SchemaRegistryApi.EMPTY, OperationsApi.NONE, true);
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
        Files.write(inboxDir.resolve("remit.835"), EDI);
        JsonObject after = page(readOnly.getChildren(sourceId, 100, 1));
        assertEquals(1, after.get("count").getAsInt(), "a fresh readdir, not a cached listing");
        JsonObject file = item(after, 0);
        assertEquals("remit.835", file.get("name").getAsString());
        assertEquals(List.of("binary"), classes(file));
        assertEquals(EDI.length, file.get("size").getAsLong());
        assertEquals("watched", file.get("ingest").getAsString());
        assertEquals(0, buffer.fileCount(), "still nothing in the buffer — this view is the volume");

        // ...and it goes away again the moment the bytes do.
        Files.delete(inboxDir.resolve("remit.835"));
        assertEquals(0, page(readOnly.getChildren(sourceId, 100, 1)).get("count").getAsInt());
    }

    @Test
    void ingestFieldSaysWhatThePollerWillDo() throws Exception {
        Files.write(inboxDir.resolve("good.835"), EDI);
        Files.write(inboxDir.resolve("notes.txt"), EDI);
        Files.write(inboxDir.resolve("old.835.done"), EDI);
        Path sub = Files.createDirectory(inboxDir.resolve("payer-a"));
        Files.write(sub.resolve("nested.835"), EDI);

        assertEquals("watched", ingest(INBOX + "/inbox/good.835"));
        assertEquals("ignored:pattern", ingest(INBOX + "/inbox/notes.txt"), "*.{x12,835} does not match");
        assertEquals("ignored:suffix", ingest(INBOX + "/inbox/old.835.done"), "already consumed");
        assertEquals("ignored:subdirectory", ingest(INBOX + "/inbox/payer-a/nested.835"),
            "the poller scans each source flat");
    }

    @Test
    void listingsHideDotfilesAndPutDirectoriesFirst() throws Exception {
        Files.createDirectory(inboxDir.resolve("zzz-dir"));
        Files.write(inboxDir.resolve("aaa.835"), EDI);
        Files.write(inboxDir.resolve(".hidden.835"), EDI);

        assertEquals(List.of("zzz-dir", "aaa.835"),
            names(page(readOnly.getChildren(INBOX + "/inbox", 100, 1))),
            "directories first, then files; dotfiles invisible exactly as the poller sees them");
    }

    @Test
    void traversalAndUnknownSourcesAre404() {
        for (String id : List.of(INBOX + "/inbox/../../etc", INBOX + "/inbox/%2E%2E/x",
                INBOX + "/nope", INBOX + "/inbox/missing.835")) {
            assertEquals(404, assertThrows(ProducerException.class,
                () -> readOnly.getObject(id)).httpStatus(), id);
        }
    }

    @Test
    void downloadServesBytesThatWereNeverIngested() throws Exception {
        Files.write(inboxDir.resolve("raw.835"), EDI);
        BinaryContent bin = readOnly.downloadBinary(INBOX + "/inbox/raw.835");
        assertEquals("raw.835", bin.fileName());
        try (java.io.InputStream in = bin.open()) {
            assertEquals(new String(EDI, StandardCharsets.UTF_8), new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertEquals(EDI.length, bin.size());
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
        Files.write(outside.resolve("secret.835"), EDI);
        Files.createSymbolicLink(inboxDir.resolve("escape"), outside);               // a directory link
        Files.createSymbolicLink(inboxDir.resolve("leak.835"), outside.resolve("secret.835")); // a leaf link
        Files.createDirectories(inboxDir.resolve("real"));
        Files.createSymbolicLink(inboxDir.resolve("real").resolve("up"), outside);  // a link below a real dir
        Files.write(inboxDir.resolve("ok.835"), EDI);

        for (String id : List.of(INBOX + "/inbox/escape", INBOX + "/inbox/escape/secret.835",
                INBOX + "/inbox/leak.835", INBOX + "/inbox/real/up/secret.835")) {
            assertEquals(404, assertThrows(ProducerException.class, () -> readOnly.getObject(id)).httpStatus(), id);
            assertEquals(404, assertThrows(ProducerException.class, () -> readOnly.downloadBinary(id)).httpStatus(), id);
        }
        assertEquals(404, assertThrows(ProducerException.class,
            () -> readOnly.getChildren(INBOX + "/inbox/escape", 100, 1)).httpStatus());
        // writes through a link are refused and nothing lands outside
        assertEquals(404, assertThrows(ProducerException.class,
            () -> writable.uploadBinary(INBOX + "/inbox/escape", "planted.835", EDI)).httpStatus());
        assertEquals(404, assertThrows(ProducerException.class,
            () -> writable.createChildObject(INBOX + "/inbox/real/up", "d", List.of("container"))).httpStatus());
        assertEquals(404, assertThrows(ProducerException.class,
            () -> writable.deleteObject(INBOX + "/inbox/escape/secret.835")).httpStatus());
        assertFalse(Files.exists(outside.resolve("planted.835")));
        assertTrue(Files.exists(outside.resolve("secret.835")));
        // the listing still works and simply does not show links
        assertEquals(List.of("real", "ok.835"), names(page(readOnly.getChildren(INBOX + "/inbox", 100, 1))));
        assertEquals(List.of(), names(page(readOnly.getChildren(INBOX + "/inbox/real", 100, 1))));
    }

    // --- the gate -----------------------------------------------------------

    @Test
    void everyWriteIsRefusedWhileTheFlagIsOff() throws Exception {
        Files.write(inboxDir.resolve("remit.835"), EDI);
        assertFalse(readOnly.fileManagementEnabled());

        ProducerException upload = assertThrows(ProducerException.class,
            () -> readOnly.uploadBinary(INBOX + "/inbox", "new.835", EDI));
        assertEquals(400, upload.httpStatus());
        assertEquals("err.unsupported.operation", upload.key());
        assertTrue(upload.getMessage().contains("allowFileManagement"), "the message names the flag");

        assertEquals("err.unsupported.operation", assertThrows(ProducerException.class,
            () -> readOnly.createChildObject(INBOX + "/inbox", "payer-a", List.of("container"))).key());
        assertEquals("err.unsupported.operation", assertThrows(ProducerException.class,
            () -> readOnly.deleteObject(INBOX + "/inbox/remit.835")).key());

        assertTrue(Files.exists(inboxDir.resolve("remit.835")), "a refused delete touches nothing");
        // Reading the same branch stays available with the flag off.
        assertEquals(1, page(readOnly.getChildren(INBOX + "/inbox", 100, 1)).get("count").getAsInt());
    }

    // --- upload -------------------------------------------------------------

    @Test
    void uploadLandsAtomicallyAndIsImmediatelyBrowsable() throws Exception {
        JsonObject created = GSON.fromJson(
            writable.uploadBinary(INBOX + "/inbox", "remit.835", EDI), JsonObject.class);

        assertEquals(INBOX + "/inbox/remit.835", created.get("id").getAsString());
        assertEquals(List.of("binary"), classes(created));
        assertEquals("watched", created.get("ingest").getAsString(), "it will be picked up");
        assertArrayEqualsBytes(EDI, Files.readAllBytes(inboxDir.resolve("remit.835")));

        try (var entries = Files.list(inboxDir)) {
            List<String> left = new ArrayList<>();
            entries.forEach(p -> left.add(p.getFileName().toString()));
            assertEquals(List.of("remit.835"), left, "no .part-* temporary left behind");
        }
        assertEquals(1, page(readOnly.getChildren(INBOX + "/inbox", 100, 1)).get("count").getAsInt());
        assertEquals(0, buffer.fileCount(), "upload does not fabricate a consumed-file row");
    }

    @Test
    void uploadNeverReplacesAndValidatesTheName() throws Exception {
        writable.uploadBinary(INBOX + "/inbox", "remit.835", EDI);

        assertEquals("err.illegal.argument", assertThrows(ProducerException.class,
            () -> writable.uploadBinary(INBOX + "/inbox", "remit.835", EDI)).key(),
            "replacing bytes at a path would fork its fileId");

        for (String bad : List.of("../escape.835", "sub/child.835", ".hidden.835", "  ")) {
            assertEquals(400, assertThrows(ProducerException.class,
                () -> writable.uploadBinary(INBOX + "/inbox", bad, EDI)).httpStatus(), bad);
        }
        assertEquals(400, assertThrows(ProducerException.class,
            () -> writable.uploadBinary(INBOX, "remit.835", EDI)).httpStatus(),
            "the branch root is not a directory");
    }

    @Test
    void uploadTargetsAnyDirectoryUnderTheSource() throws Exception {
        writable.createChildObject(INBOX + "/inbox", "payer-a", List.of("container"));
        JsonObject created = GSON.fromJson(
            writable.uploadBinary(INBOX + "/inbox/payer-a", "nested.835", EDI), JsonObject.class);

        assertEquals(INBOX + "/inbox/payer-a/nested.835", created.get("id").getAsString());
        assertEquals("ignored:subdirectory", created.get("ingest").getAsString(),
            "visible and downloadable, but the flat poller will not consume it");
        assertTrue(Files.isRegularFile(inboxDir.resolve("payer-a").resolve("nested.835")));
    }

    @Test
    void base64UploadThroughTheJsonEnvelopeIsEquivalent() throws Exception {
        // The shape X12ApiServer.upload() decodes for a JSON-only invoker.
        byte[] decoded = Base64.getDecoder().decode(Base64.getEncoder().encodeToString(EDI));
        writable.uploadBinary(INBOX + "/inbox", "encoded.835", decoded);
        assertArrayEqualsBytes(EDI, Files.readAllBytes(inboxDir.resolve("encoded.835")));
    }

    // --- mkdir / delete -----------------------------------------------------

    @Test
    void mkdirAndDeleteRoundTrip() throws Exception {
        JsonObject made = GSON.fromJson(
            writable.createChildObject(INBOX + "/inbox", "payer-a", List.of("container")), JsonObject.class);
        assertEquals(INBOX + "/inbox/payer-a", made.get("id").getAsString());
        assertEquals(List.of("container"), classes(made));
        assertTrue(Files.isDirectory(inboxDir.resolve("payer-a")));

        assertEquals("err.illegal.argument", assertThrows(ProducerException.class,
            () -> writable.createChildObject(INBOX + "/inbox", "payer-a", List.of("container"))).key(),
            "creating it twice");
        assertEquals("err.illegal.argument", assertThrows(ProducerException.class,
            () -> writable.createChildObject(INBOX + "/inbox", "payer-b", List.of("collection"))).key(),
            "only containers can be created");

        JsonObject deleted = GSON.fromJson(writable.deleteObject(INBOX + "/inbox/payer-a"), JsonObject.class);
        assertEquals("deleted", deleted.get("status").getAsString());
        assertFalse(Files.exists(inboxDir.resolve("payer-a")));
    }

    @Test
    void deleteRefusesNonEmptyDirectoriesAndMountPoints() throws Exception {
        writable.createChildObject(INBOX + "/inbox", "payer-a", List.of("container"));
        writable.uploadBinary(INBOX + "/inbox/payer-a", "nested.835", EDI);

        assertEquals("err.illegal.argument", assertThrows(ProducerException.class,
            () -> writable.deleteObject(INBOX + "/inbox/payer-a")).key(),
            "a recursive delete over a live feed directory loses un-ingested claims");
        assertEquals("err.unsupported.operation", assertThrows(ProducerException.class,
            () -> writable.deleteObject(INBOX + "/inbox")).key(), "a configured source is a mount point");
        assertEquals("err.unsupported.operation", assertThrows(ProducerException.class,
            () -> writable.deleteObject(INBOX)).key(), "the branch root");
        assertEquals(404, assertThrows(ProducerException.class,
            () -> writable.deleteObject(INBOX + "/inbox/gone.835")).httpStatus());

        // Child first, then the directory — the order the API forces.
        writable.deleteObject(INBOX + "/inbox/payer-a/nested.835");
        writable.deleteObject(INBOX + "/inbox/payer-a");
        assertFalse(Files.exists(inboxDir.resolve("payer-a")));
    }

    @Test
    void writesRejectTheEmergentBranches() {
        for (String id : List.of(R + "/files", R + "/transactions", R + "/stats", R)) {
            assertEquals("err.unsupported.operation", assertThrows(ProducerException.class,
                () -> writable.uploadBinary(id, "x.835", EDI)).key(), id);
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

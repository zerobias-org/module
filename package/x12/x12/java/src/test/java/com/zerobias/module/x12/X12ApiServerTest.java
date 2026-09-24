package com.zerobias.module.x12;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.RetentionConfig;
import com.zerobias.module.x12.buffer.TestRows;
import com.zerobias.module.x12.health.HealthCheck;
import com.zerobias.module.x12.health.PollerStatus;
import com.zerobias.module.x12.inbox.FileConsumer;
import com.zerobias.module.x12.inbox.InboxFixtures;
import com.zerobias.module.x12.materializer.StructureResolver;
import com.zerobias.module.x12.parser.Fixtures;
import com.zerobias.module.x12.producer.ObjectTree;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The operations HTTP surface over a real buffer: streaming download, error bodies, isSupported, shutdown order. */
class X12ApiServerTest {

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @TempDir
    Path dir;
    private BufferStore buffer;
    private RecordingPollers pollers;
    private X12ApiServer server;
    private Javalin app;
    private String fileObjectId;
    private byte[] fileBytes;

    @BeforeEach
    void boot() throws Exception {
        Path inbox = Files.createDirectories(dir.resolve("inbox"));
        ModuleRuntimeConfig cfg = new ModuleRuntimeConfig(
            List.of(new SourceConfig("inbox", inbox.toString(), "*", 1, 0)),
            ".done", ".error", false, RetentionConfig.none(), false, ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES);
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false, new TestRows.MutableClock(TestRows.BASE));
        fileBytes = Fixtures.bytes(Fixtures.F835);
        Path drop = Files.write(inbox.resolve("remit \"q1\".835"), fileBytes);
        String fileId = InboxFixtures.consume(new FileConsumer(buffer, null, cfg, new StructureResolver(), buffer.clock()),
            cfg.sources().get(0), drop, TestRows.BASE).fileId();
        fileObjectId = ObjectTree.RECEIVER + "/files/" + ObjectTree.encodeSegment(fileId);

        pollers = new RecordingPollers();
        server = new X12ApiServer(buffer, null, pollers, new HealthCheck(buffer, pollers),
            X12ApiServer.buildFacade(buffer, cfg, pollers));
        app = server.app().start(0);
        pollers.app = app;
        assertEquals(200, post("/connections", "{\"connectionId\":\"c1\"}").statusCode());
    }

    @AfterEach
    void stop() throws Exception {
        app.stop();
        buffer.close();
    }

    @Test
    void downloadStreamsTheFileWithItsLengthAndASafeDisposition() throws Exception {
        HttpResponse<byte[]> r = HTTP.send(HttpRequest.newBuilder(uri("/connections/c1/BinaryApi.downloadBinary"))
                .header("Accept-Encoding", "gzip")
                .POST(HttpRequest.BodyPublishers.ofString(argMap(Map.of("objectId", fileObjectId)))).build(),
            HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, r.statusCode(), new String(r.body()));
        assertArrayEquals(fileBytes, r.body());
        assertEquals(String.valueOf(fileBytes.length), r.headers().firstValue("Content-Length").orElse(null));
        assertFalse(r.headers().firstValue("Content-Encoding").isPresent(), "never gzipped under a Content-Length");
        assertEquals("application/EDI-X12", r.headers().firstValue("Content-Type").orElse(""));
        assertEquals("attachment; filename=\"remit _q1_.835\"; filename*=UTF-8''remit%20%22q1%22.835",
            r.headers().firstValue("Content-Disposition").orElse(null));

        try (Stream<Path> files = Files.list(dir.resolve("inbox"))) {
            Files.delete(files.filter(p -> p.toString().endsWith(".done")).findFirst().orElseThrow());
        }
        HttpResponse<String> gone = post("/connections/c1/BinaryApi.downloadBinary", argMap(Map.of("objectId", fileObjectId)));
        assertEquals(404, gone.statusCode());
        assertEquals("gone", json(gone).get("reason").getAsString());
    }

    @Test
    void contentDispositionCannotBeBrokenByTheFileName() {
        assertEquals("attachment; filename=\"a_b__.835\"; filename*=UTF-8''a%22b%0D%0A.835",
            X12ApiServer.contentDisposition("a\"b\r\n.835"));
        assertEquals("attachment; filename=\"r_sum_.835\"; filename*=UTF-8''r%C3%A9sum%C3%A9.835",
            X12ApiServer.contentDisposition("résumé.835"));
        assertEquals("attachment; filename=\"a_b.835\"; filename*=UTF-8''a%5Cb.835", X12ApiServer.contentDisposition("a\\b.835"));
        assertEquals("attachment", X12ApiServer.contentDisposition(null));
    }

    @Test
    void unexpectedFailuresAnswerGenericallyAndLogTheCause() throws Exception {
        buffer.close();   // every buffer read now fails with an SQLite error naming the connection state
        // slf4j-simple writes to whatever System.err is at the time of each call
        PrintStream stderr = System.err;
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        HttpResponse<String> r;
        System.setErr(new PrintStream(log, true, StandardCharsets.UTF_8));
        try {
            r = post("/connections/c1/ObjectsApi.getObject", argMap(Map.of("objectId", ObjectTree.RECEIVER + "/transactions")));
        } finally {
            System.setErr(stderr);
        }
        String logged = log.toString(StandardCharsets.UTF_8);
        assertTrue(logged.contains("Unexpected error serving POST /connections/c1/ObjectsApi.getObject"), logged);
        assertTrue(logged.contains("java.sql.SQLException"), "the cause, with its stack trace: " + logged);
        assertEquals(500, r.statusCode());
        JsonObject body = json(r);
        assertEquals("err.unexpected", body.get("key").getAsString());
        assertEquals(500, body.get("statusCode").getAsInt());
        assertEquals("Unexpected error", body.get("msg").getAsString());
        assertTrue(body.has("timestamp") && body.has("template"), "errorModelBase");
        assertFalse(r.body().toLowerCase().contains("sql"), r.body());
        assertFalse(r.body().contains(dir.toString()), r.body());
    }

    @Test
    void malformedRequestsAre400NotServerErrors() throws Exception {
        assertEquals(400, post("/connections", "{not json").statusCode());
        assertEquals(400, post("/connections", "[1]").statusCode());
        assertEquals(400, post("/connections", "{}").statusCode(), "connectionId is required");
        assertEquals(400, post("/connections/c1/ObjectsApi.getRootObject", "{bad").statusCode());
        assertEquals(400, post("/connections/c1/ObjectsApi.getRootObject", "{\"argMap\":[1]}").statusCode());
        HttpResponse<String> r = post("/connections/c1/FunctionsApi.invokeFunction",
            argMap(Map.of("objectId", ObjectTree.RECEIVER + "/ops/purge", "requestBody", Map.of("olderthan", "P30D"))));
        assertEquals(400, r.statusCode());
        assertEquals("err.illegal.argument", json(r).get("key").getAsString());
        assertEquals(200, post("/connections/c1/ObjectsApi.getRootObject", "").statusCode(), "no body = no args");
    }

    @Test
    void isSupportedAndMetadataReflectWhatIsServed() throws Exception {
        assertTrue(json(get("/connections/c1/isSupported/getChildren")).get("supported").getAsBoolean());
        assertTrue(json(get("/connections/c1/isSupported/BinaryApi.downloadBinary")).get("supported").getAsBoolean());
        assertFalse(json(get("/connections/c1/isSupported/objectSearch")).get("supported").getAsBoolean());
        assertFalse(json(get("/connections/c1/isSupported/createChildObject")).get("supported").getAsBoolean());

        JsonObject md = json(get("/connections/c1/metadata"));
        assertEquals(1, md.get("bufferDepth").getAsLong(), "the one un-acked 835");
    }

    @Test
    void fileManagementIsRefusedByDefaultAndServedOnceTheConfigFlagIsSet() throws Exception {
        String inboxSource = ObjectTree.RECEIVER + "/inbox/inbox";
        String upload = "/connections/c1/BinaryApi.uploadBinaryContent?objectId="
            + java.net.URLEncoder.encode(inboxSource, StandardCharsets.UTF_8) + "&fileName=up.835";

        // default config: receive-only — refused as an unsupported operation, nothing written
        HttpResponse<String> refused = post(upload, "ISA*raw~");
        assertEquals(400, refused.statusCode(), refused.body());
        assertEquals("err.unsupported.operation", json(refused).get("key").getAsString());
        assertFalse(Files.exists(dir.resolve("inbox").resolve("up.835")));
        for (String op : List.of("uploadBinaryContent", "BinaryApi.uploadBinaryContent", "createChildObject",
                "ObjectsApi.deleteObject", "BinaryApi.uploadBinary")) {
            assertFalse(json(get("/connections/c1/isSupported/" + op)).get("supported").getAsBoolean(), op);
        }

        // config.allowFileManagement=true: raw and base64-envelope uploads land, isSupported says so
        app.stop();
        ModuleRuntimeConfig open = new ModuleRuntimeConfig(
            List.of(new SourceConfig("inbox", dir.resolve("inbox").toString(), "*", 1, 0)),
            ".done", ".error", false, RetentionConfig.none(), false, ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES, true);
        server = new X12ApiServer(buffer, null, pollers, new HealthCheck(buffer, pollers),
            X12ApiServer.buildFacade(buffer, open, pollers));
        app = server.app().start(0);
        assertEquals(200, post("/connections", "{\"connectionId\":\"c1\"}").statusCode());
        for (String op : List.of("uploadBinaryContent", "BinaryApi.uploadBinaryContent", "ObjectsApi.createChildObject",
                "deleteObject")) {
            assertTrue(json(get("/connections/c1/isSupported/" + op)).get("supported").getAsBoolean(), op);
        }
        assertFalse(json(get("/connections/c1/isSupported/BinaryApi.uploadBinary")).get("supported").getAsBoolean(),
            "no aliases");

        HttpResponse<String> created = post(upload, "ISA*raw~");
        assertEquals(201, created.statusCode(), created.body());
        assertEquals(inboxSource + "/up.835", json(created).get("id").getAsString());
        assertEquals("ISA*raw~", Files.readString(dir.resolve("inbox").resolve("up.835")));

        HttpResponse<String> enveloped = post("/connections/c1/BinaryApi.uploadBinaryContent", argMap(Map.of(
            "objectId", inboxSource, "fileName", "b64.835",
            "contentBase64", java.util.Base64.getEncoder().encodeToString("ISA*b64~".getBytes(StandardCharsets.UTF_8)))));
        assertEquals(201, enveloped.statusCode(), enveloped.body());
        assertEquals("ISA*b64~", Files.readString(dir.resolve("inbox").resolve("b64.835")));

        HttpResponse<byte[]> live = HTTP.send(HttpRequest.newBuilder(uri("/connections/c1/BinaryApi.downloadBinary"))
                .POST(HttpRequest.BodyPublishers.ofString(argMap(Map.of("objectId", inboxSource + "/up.835")))).build(),
            HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, live.statusCode());
        assertEquals("ISA*raw~", new String(live.body(), StandardCharsets.UTF_8), "live /inbox bytes are streamed");
        assertEquals("8", live.headers().firstValue("Content-Length").orElse(null));

        HttpResponse<String> deleted = post("/connections/c1/ObjectsApi.deleteObject",
            argMap(Map.of("objectId", inboxSource + "/b64.835", "recursive", false)));
        assertEquals(200, deleted.statusCode(), deleted.body());
        assertFalse(Files.exists(dir.resolve("inbox").resolve("b64.835")));
        HttpResponse<String> recursive = post("/connections/c1/ObjectsApi.deleteObject",
            argMap(Map.of("objectId", inboxSource + "/up.835", "recursive", true)));
        assertEquals(400, recursive.statusCode());
        assertEquals("err.unsupported.operation", json(recursive).get("key").getAsString());
    }

    @Test
    void shutdownStopsServingBeforeClosingPollersAndBuffer() {
        server.shutdown(app);
        assertEquals(List.of("server stopped", "buffer open"), pollers.atClose,
            "pollers close after the server stops and before the buffer does");
        assertTrue(app.jettyServer().server().isStopped());
    }

    // --- helpers -------------------------------------------------------------

    private URI uri(String path) {
        return URI.create("http://localhost:" + app.port() + path);
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(uri(path)).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String argMap(Map<String, Object> args) {
        return GSON.toJson(Map.of("argMap", args));
    }

    private static JsonObject json(HttpResponse<String> r) {
        return GSON.fromJson(r.body(), JsonObject.class);
    }

    /** Pollers that record, at close, whether the server had stopped and the buffer was still open. */
    private final class RecordingPollers implements PollerHandle {
        volatile Javalin app;
        final List<String> atClose = new ArrayList<>();

        @Override
        public RescanResult rescan(String source) {
            return new RescanResult(0, 0, 0, 0);
        }

        @Override
        public void close() {
            atClose.add(app.jettyServer().server().isStopped() ? "server stopped" : "server running");
            try {
                buffer.count();
                atClose.add("buffer open");
            } catch (Exception e) {
                atClose.add("buffer closed");
            }
        }

        @Override
        public boolean up() {
            return true;
        }

        @Override
        public Optional<Instant> lastScan() {
            return Optional.empty();
        }

        @Override
        public Optional<Instant> lastConsumed() {
            return Optional.empty();
        }

        @Override
        public boolean backpressure() {
            return false;
        }

        @Override
        public List<PollerStatus.SourceStatus> sources() {
            return List.of();
        }
    }
}

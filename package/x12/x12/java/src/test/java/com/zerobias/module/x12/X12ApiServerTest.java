package com.zerobias.module.x12;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The HTTP layer's error mapping: every body is errorModelBase, and a 500 never echoes its cause. */
class X12ApiServerTest {

    private static final Gson GSON = new Gson();
    private static final String SECRET = "/data/x12-buffer/buffer.db is locked";

    private Javalin app;
    private String base;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void start() {
        app = Javalin.create(cfg -> cfg.showJavalinBanner = false);
        X12ApiServer.registerExceptionHandlers(app);
        new X12ApiServer().registerRoutes(app);
        app.get("/boom", ctx -> {
            throw new IllegalStateException(SECRET);
        });
        app.get("/bad-arg", ctx -> {
            throw new IllegalArgumentException("sortDir must be asc or desc, got 'sideways'");
        });
        app.get("/download", ctx -> X12ApiServer.streamBinary(ctx, new com.zerobias.module.x12.producer.BinaryContent(
            "/x", java.nio.file.Path.of(ctx.queryParam("path")), Long.parseLong(ctx.queryParam("size")),
            com.zerobias.module.x12.producer.BinaryContent.MIME_X12, ctx.queryParam("name"))));
        app.start(0);
        base = "http://localhost:" + app.port();
    }

    @AfterEach
    void stop() {
        app.stop();
    }

    @Test
    void unexpectedErrorsAreGenericAndCarryNoCause() throws Exception {
        HttpResponse<String> r = get("/boom");
        assertEquals(500, r.statusCode());
        assertFalse(r.body().contains("buffer.db"), r.body());
        JsonObject body = GSON.fromJson(r.body(), JsonObject.class);
        assertEquals("err.unexpected", body.get("key").getAsString());
        assertEquals(500, body.get("statusCode").getAsInt());
        assertTrue(body.has("template") && body.has("timestamp") && body.has("msg"), r.body());
    }

    @Test
    void illegalArgumentStaysA400WithItsMessage() throws Exception {
        HttpResponse<String> r = get("/bad-arg");
        assertEquals(400, r.statusCode());
        JsonObject body = GSON.fromJson(r.body(), JsonObject.class);
        assertEquals("err.illegal.argument", body.get("key").getAsString());
        assertTrue(body.get("msg").getAsString().contains("sideways"));
    }

    @Test
    void malformedJsonBodiesAre400() throws Exception {
        HttpResponse<String> connect = post("/connections", "{\"connectionId\":");
        assertEquals(400, connect.statusCode(), connect.body());
        assertEquals("err.illegal.argument", GSON.fromJson(connect.body(), JsonObject.class).get("key").getAsString());
        assertEquals(400, post("/connections", "[1,2]").statusCode(), "not an object");

        assertEquals(200, post("/connections", "{\"connectionId\":\"c1\"}").statusCode());
        HttpResponse<String> op = post("/connections/c1/ObjectsApi.getRootObject", "{argMap:");
        assertEquals(400, op.statusCode(), op.body());
        assertEquals(400, post("/connections/c1/ObjectsApi.getRootObject", "{\"argMap\":[1]}").statusCode(),
            "argMap must be an object");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + path))
            .header("content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void downloadsStreamWithLengthAndASafeDisposition(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws Exception {
        byte[] bytes = new byte[256 * 1024];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ('A' + i % 26);
        }
        java.nio.file.Path f = java.nio.file.Files.write(dir.resolve("big.835"), bytes);
        String evil = "x\"; filename=evil.exe\r\nSet-Cookie: a=b é.835";
        HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(URI.create(base + "/download?path="
                + java.net.URLEncoder.encode(f.toString(), java.nio.charset.StandardCharsets.UTF_8)
                + "&size=" + bytes.length + "&name="
                + java.net.URLEncoder.encode(evil, java.nio.charset.StandardCharsets.UTF_8)))
            .header("Accept-Encoding", "gzip").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, r.statusCode());
        org.junit.jupiter.api.Assertions.assertArrayEquals(bytes, r.body(), "verbatim, not compressed");
        assertEquals(String.valueOf(bytes.length), r.headers().firstValue("Content-Length").orElse(null));
        assertTrue(r.headers().firstValue("Content-Encoding").isEmpty(), "compression off");
        String cd = r.headers().firstValue("Content-Disposition").orElseThrow();
        assertEquals("attachment; filename=\"x_; filename=evil.exe__Set-Cookie: a=b _.835\"; "
            + "filename*=UTF-8''x%22%3B%20filename%3Devil.exe%0D%0ASet-Cookie%3A%20a%3Db%20%C3%A9.835", cd);
        assertTrue(r.headers().firstValue("Set-Cookie").isEmpty());

        // gone between resolve and open: 404 reason gone, not a 500
        java.nio.file.Files.delete(f);
        HttpResponse<String> gone = get("/download?path="
            + java.net.URLEncoder.encode(f.toString(), java.nio.charset.StandardCharsets.UTF_8) + "&size=1&name=a");
        assertEquals(404, gone.statusCode());
        assertEquals("gone", GSON.fromJson(gone.body(), JsonObject.class).get("reason").getAsString());
    }

    @Test
    void contentDispositionIsPlainForPlainNames() {
        assertEquals("attachment; filename=\"remit.835.done\"; filename*=UTF-8''remit.835.done",
            X12ApiServer.contentDisposition("remit.835.done"));
        assertEquals("attachment", X12ApiServer.contentDisposition(null));
    }

    @Test
    void oversizedBodiesAre413InThePlatformEnvelope() throws Exception {
        Javalin small = Javalin.create(cfg -> {
            cfg.showJavalinBanner = false;
            cfg.http.maxRequestSize = 1024;
        });
        X12ApiServer.registerExceptionHandlers(small);
        small.post("/echo", ctx -> ctx.result(String.valueOf(ctx.bodyAsBytes().length)));
        small.start(0);
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(
                    URI.create("http://localhost:" + small.port() + "/echo"))
                .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[4096])).build(),
                HttpResponse.BodyHandlers.ofString());
            assertEquals(413, r.statusCode(), r.body());
            JsonObject body = GSON.fromJson(r.body(), JsonObject.class);
            assertEquals(413, body.get("statusCode").getAsInt());
            assertTrue(body.has("key") && body.has("msg"), r.body());
        } finally {
            small.stop();
        }
    }

    @Test
    void requestLimitMatchesBothCommittedNginxConfs() throws Exception {
        assertEquals(64L * 1024 * 1024, X12ApiServer.MAX_REQUEST_BYTES);
        for (String conf : java.util.List.of("../nginx.conf", "../nginx-insecure.conf")) {
            String text = java.nio.file.Files.readString(java.nio.file.Path.of(conf));
            assertTrue(text.contains("client_max_body_size 64m;"), conf + " must allow what the Java side accepts");
        }
    }
}

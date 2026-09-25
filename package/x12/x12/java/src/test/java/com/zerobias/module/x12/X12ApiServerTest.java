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
}

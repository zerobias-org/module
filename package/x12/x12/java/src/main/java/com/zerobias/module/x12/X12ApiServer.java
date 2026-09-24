package com.zerobias.module.x12;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.RetentionSweeper;
import com.zerobias.module.x12.buffer.Status;
import com.zerobias.module.x12.health.HealthCheck;
import com.zerobias.module.x12.inbox.X12InboxPollerFactory;
import com.zerobias.module.x12.materializer.StructureResolver;
import com.zerobias.module.x12.producer.BinaryContent;
import com.zerobias.module.x12.producer.MaterializerRecastHook;
import com.zerobias.module.x12.producer.ObjectTree;
import com.zerobias.module.x12.producer.OperationRouter;
import com.zerobias.module.x12.producer.ProducerException;
import com.zerobias.module.x12.producer.SchemaRegistry;
import com.zerobias.module.x12.producer.X12Operations;
import com.zerobias.module.x12.producer.X12ProducerFacade;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entry point for the X12 EDI file receiver module (DESIGN §3).
 *
 * <p>Boots the always-on daemon — SQLite buffer, retention sweeper, one inbox poller
 * per configured source — and the DataProducer operations HTTP server on
 * {@code INTERNAL_PORT} (nginx fronts it on 8888). The HTTP surface mirrors the SQL
 * generic module's RPC contract so the Hub Node's java-http invoker drives it unchanged:
 * <ul>
 *   <li>{@code GET  /} — readiness + non-sensitive profile fields</li>
 *   <li>{@code POST /connections} — connect (handshake; the daemon is already running)</li>
 *   <li>{@code PUT  /connections/{id}/disconnect} — release the session</li>
 *   <li>{@code GET  /connections/{id}/metadata} — connection metadata</li>
 *   <li>{@code GET  /connections/{id}/isSupported/{operationId}}</li>
 *   <li>{@code POST /connections/{id}/{method}} — dispatch via {@link OperationRouter};
 *       {@code BinaryApi.downloadBinary} streams the file bytes (DESIGN §2.8)</li>
 *   <li>{@code GET  /healthz} — daemon health probe (DESIGN §9)</li>
 * </ul>
 *
 * <p>Boot order: env config → module config (MODULE_CONFIG / runtimeConfig.yml) →
 * source validation (fatal) → buffer → sweeper → pollers → producer facade → Javalin.
 * No listener ports: the inbox is a volume, not a socket.
 */
public final class X12ApiServer {

    private static final Logger LOG = LoggerFactory.getLogger(X12ApiServer.class);
    private static final Gson GSON = new Gson();

    /** Profile fields safe to log/display (the profile is informational; the daemon never reads it). */
    private static final Set<String> NONSENSITIVE_PROFILE_FIELDS = Set.of("ackDurability");

    /** Data-write operations this receiver never supports: transactions arrive as inbox files. */
    private static final Set<String> NEVER_SUPPORTED = Set.of(
        "updateObject", "addCollectionElement", "updateCollectionElement",
        "deleteCollectionElement", "executeBulkOperations", "updateDocumentData", "updateDocument");

    /** File-management operations, supported only when {@code config.allowFileManagement} is set. */
    private static final Set<String> FILE_MANAGEMENT = Set.of(
        "uploadBinaryContent", "uploadBinary", "createChildObject", "deleteObject");

    private final Map<String, String> connections = new ConcurrentHashMap<>();
    private final BufferStore buffer;
    private final RetentionSweeper retentionSweeper;
    private final PollerHandle pollers;
    private final HealthCheck health;
    private final X12ProducerFacade facade;

    /** @param retentionSweeper null when retention is unbounded */
    X12ApiServer(BufferStore buffer, RetentionSweeper retentionSweeper, PollerHandle pollers, HealthCheck health,
                 X12ProducerFacade facade) {
        this.buffer = Objects.requireNonNull(buffer, "buffer");
        this.retentionSweeper = retentionSweeper;
        this.pollers = Objects.requireNonNull(pollers, "pollers");
        this.health = Objects.requireNonNull(health, "health");
        this.facade = Objects.requireNonNull(facade, "facade");
    }

    public static void main(String[] args) {
        try {
            ModuleConfig config = ModuleConfig.fromEnv();
            ModuleRuntimeConfig mc = ModuleRuntimeConfig.fromEnv(config);
            start(config, mc);
        } catch (Exception fatal) {
            // DESIGN §3: a daemon that cannot mark files consumed must not run.
            LOG.error("X12 receiver failed to start: {}", fatal.getMessage(), fatal);
            System.exit(1);
        }
    }

    static void start(ModuleConfig config, ModuleRuntimeConfig mc) throws Exception {
        LOG.info("X12 receiver starting: ops port {}, {} source(s) {}, buffer {}",
            config.internalPort(), mc.sources().size(), mc.sources(), config.bufferDbPath());

        // --- boot validation (DESIGN §3): every source must exist, be a directory, be renameable ---
        List<String> problems = mc.validateSources();
        if (!problems.isEmpty()) {
            for (String p : problems) {
                LOG.error("source validation: {}", p);
            }
            throw new IllegalStateException("Refusing to start: " + problems.size()
                + " source configuration problem(s): " + problems);
        }

        // --- daemon: buffer + sweeper + pollers (DESIGN §4, §8) ---
        BufferStore buffer = new BufferStore(config.bufferDbPath(), mc.fullDurability());
        LOG.info("Buffer open at {} (ackDurability={})", config.bufferDbPath(),
            mc.fullDurability() ? "full" : "normal");

        RetentionSweeper sweeper = null;
        if (mc.retention().isBounded()) {
            sweeper = new RetentionSweeper(buffer, mc.retention(), Clock.systemUTC());
            sweeper.start(Duration.ofMinutes(10));
            LOG.info("Retention sweeper started: maxAge={}, maxBytes={}",
                mc.retention().maxAge(), mc.retention().maxBytes());
        } else {
            LOG.info("Retention: unbounded (no maxAge/maxBytes in module config)");
        }

        PollerHandle pollers = X12InboxPollerFactory.start(mc, buffer, sweeper);
        LOG.info("Inbox pollers started for {} source(s)", pollers.sources().size());

        X12ApiServer server = new X12ApiServer(buffer, sweeper, pollers, new HealthCheck(buffer, pollers),
            buildFacade(buffer, mc, pollers));
        Javalin app = server.app();
        app.start(config.internalPort());
        LOG.info("Operations server listening on {}", config.internalPort());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.shutdown(app)));
    }

    /** The operations HTTP server, routes and error mapping registered, not yet started. */
    Javalin app() {
        Javalin app = Javalin.create(cfg -> {
            cfg.http.defaultContentType = "application/json";
            cfg.showJavalinBanner = false;
        });
        registerExceptionHandlers(app);
        registerRoutes(app);
        return app;
    }

    /**
     * Stop taking requests first, so no in-flight operation meets a stopped poller or a
     * closed buffer; then the pollers (no new files), the sweeper, and the buffer last.
     */
    void shutdown(Javalin app) {
        app.stop();
        try {
            pollers.close();
        } catch (Exception e) {
            LOG.warn("poller shutdown", e);
        }
        if (retentionSweeper != null) {
            retentionSweeper.stop();
        }
        try {
            buffer.close();
        } catch (Exception e) {
            LOG.warn("buffer shutdown", e);
        }
    }

    /**
     * The producer surface: the classpath {@link SchemaRegistry}, the emergent
     * {@link ObjectTree} over the buffer (with the live poller status feeding {@code /stats}),
     * and the {@link X12Operations} functions with the {@link MaterializerRecastHook} behind
     * {@code recast}/{@code validate} (re-parse the stored raw under the classpath structure
     * indexes).
     */
    static X12ProducerFacade buildFacade(BufferStore buffer, ModuleRuntimeConfig mc, PollerHandle pollers) {
        SchemaRegistry schemas = SchemaRegistry.fromClasspath();
        ObjectTree tree = new ObjectTree(buffer, schemas, pollers, mc);
        X12Operations ops = new X12Operations(buffer, pollers, schemas,
            new MaterializerRecastHook(new StructureResolver(), Clock.systemUTC()));
        LOG.info("Producer: {} schema(s)", schemas.size());
        return new X12ProducerFacade(buffer, tree, schemas, ops);
    }

    private void registerRoutes(Javalin app) {
        app.get("/", ctx -> {
            JsonObject body = new JsonObject();
            body.add("nonsensitiveProfileFields", GSON.toJsonTree(NONSENSITIVE_PROFILE_FIELDS));
            ctx.result(body.toString());
        });

        app.post("/connections", ctx -> {
            Map<String, Object> requestBody = parseBody(ctx);
            Object connectionId = requestBody.get("connectionId");
            if (connectionId == null || connectionId.toString().isBlank()) {
                throw ProducerException.illegalArgument("connectionId is required");
            }
            connections.put(connectionId.toString(), connectionId.toString());
            ctx.result("{\"status\":\"connected\"}");
        });

        app.put("/connections/{connectionId}/disconnect", ctx -> {
            requireConnection(ctx.pathParam("connectionId"));
            connections.remove(ctx.pathParam("connectionId"));
            ctx.result("{\"status\":\"disconnected\"}");
        });

        app.get("/connections/{connectionId}/metadata", ctx -> {
            requireConnection(ctx.pathParam("connectionId"));
            JsonObject md = new JsonObject();
            md.addProperty("status", health.healthy() ? "On" : "Error");
            md.addProperty("bufferDepth", buffer.count(Status.NEW) + buffer.count(Status.IN_FLIGHT));
            ctx.result(md.toString());
        });

        app.get("/connections/{connectionId}/isSupported/{operationId}", ctx -> {
            requireConnection(ctx.pathParam("connectionId"));
            JsonObject body = new JsonObject();
            body.addProperty("supported", OperationRouter.isSupported(ctx.pathParam("operationId")));
            ctx.result(body.toString());
        });

        app.post("/connections/{connectionId}/{method}", ctx -> {
            requireConnection(ctx.pathParam("connectionId"));
            String method = ctx.pathParam("method");
            Object args = parseBody(ctx).get("argMap");
            if (args != null && !(args instanceof Map)) {
                throw ProducerException.illegalArgument("argMap must be a JSON object");
            }
            Map<String, Object> argMap = castMap(args);
            if (OperationRouter.isBinaryDownload(method)) {
                Object id = argMap.get("objectId");
                streamBinary(ctx, facade.downloadBinary(id == null ? null : id.toString()));
                return;
            }
            String result = OperationRouter.executeOperation(facade, method, argMap);
            ctx.contentType("application/json").result(result);
        });

        app.get("/healthz", ctx -> {
            // DESIGN §9 payload; Node polls every 30s. 200 healthy / 503 degraded.
            ctx.status(health.healthy() ? 200 : 503)
               .contentType("application/json")
               .result(GSON.toJson(health.status()));
        });
    }

    /**
     * DESIGN §2.8: full-content 200 with the raw EDI bytes, streamed from disk with a
     * {@code Content-Length}. Compression stays off — Javalin would gzip the stream after the
     * length was set — and Javalin closes the stream once it is written.
     */
    private static void streamBinary(Context ctx, BinaryContent bin) {
        InputStream in;
        try {
            in = bin.open();
        } catch (IOException e) {
            // Removed (or swapped for a symlink) since it was resolved.
            throw ProducerException.fileGone(bin.fileId());
        }
        ctx.minSizeForCompression(Integer.MAX_VALUE);
        ctx.contentType(bin.mimeType());
        ctx.res().setCharacterEncoding(null);   // bytes, not text: no charset on the Content-Type
        ctx.header("Content-Length", Long.toString(bin.size()));
        ctx.header("Content-Disposition", contentDisposition(bin.fileName()));
        ctx.result(in);
    }

    /**
     * RFC 6266 {@code attachment} with an ASCII {@code filename} fallback (quotes,
     * backslashes, control and non-ASCII characters replaced by {@code _}) and the exact
     * name as RFC 5987 {@code filename*}. A file name is operator/sender-controlled, so
     * nothing from it may end the header or the quoted string.
     */
    static String contentDisposition(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "attachment";
        }
        StringBuilder ascii = new StringBuilder(fileName.length());
        for (int i = 0; i < fileName.length(); i++) {
            char c = fileName.charAt(i);
            ascii.append(c < 0x20 || c > 0x7e || c == '"' || c == '\\' ? '_' : c);
        }
        StringBuilder pct = new StringBuilder();
        for (byte b : fileName.getBytes(StandardCharsets.UTF_8)) {
            int u = b & 0xff;
            if ((u >= 'a' && u <= 'z') || (u >= 'A' && u <= 'Z') || (u >= '0' && u <= '9')
                    || "!#$&+-.^_`|~".indexOf(u) >= 0) {
                pct.append((char) u);
            } else {
                pct.append('%').append(Character.toUpperCase(Character.forDigit(u >> 4, 16)))
                   .append(Character.toUpperCase(Character.forDigit(u & 0xf, 16)));
            }
        }
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + pct;
    }

    private void registerExceptionHandlers(Javalin app) {
        app.exception(ProducerException.class, (e, ctx) -> respond(ctx, e));
        app.exception(Exception.class, (e, ctx) -> {
            // The cause can name buffer or inbox paths: log it here, answer generically.
            LOG.error("Unexpected error serving {} {}", ctx.method(), ctx.path(), e);
            respond(ctx, ProducerException.unexpected());
        });
    }

    private static void respond(Context ctx, ProducerException e) {
        ctx.status(e.httpStatus()).contentType("application/json").result(GSON.toJson(e.toBody()));
    }

    private void requireConnection(String connectionId) {
        if (!connections.containsKey(connectionId)) {
            throw ProducerException.illegalArgument("Connection not found: " + connectionId);
        }
    }

    /** The request body as a JSON object; empty body = empty object, anything else malformed = 400. */
    private static Map<String, Object> parseBody(Context ctx) {
        Object parsed;
        try {
            parsed = GSON.fromJson(ctx.body(), Object.class);
        } catch (JsonParseException e) {
            throw ProducerException.illegalArgument("Request body is not valid JSON");
        }
        if (parsed != null && !(parsed instanceof Map)) {
            throw ProducerException.illegalArgument("Request body must be a JSON object");
        }
        return castMap(parsed);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new HashMap<>();
    }
}

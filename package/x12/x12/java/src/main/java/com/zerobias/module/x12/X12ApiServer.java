package com.zerobias.module.x12;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.RetentionSweeper;
import com.zerobias.module.x12.health.HealthCheck;
import com.zerobias.module.x12.materializer.StructureResolver;
import com.zerobias.module.x12.producer.BinaryContent;
import com.zerobias.module.x12.producer.GraphBackfill;
import com.zerobias.module.x12.producer.MaterializerRecastHook;
import com.zerobias.module.x12.producer.ObjectTree;
import com.zerobias.module.x12.producer.ObjectTreeApi;
import com.zerobias.module.x12.producer.OperationRouter;
import com.zerobias.module.x12.producer.OperationsApi;
import com.zerobias.module.x12.producer.ProducerException;
import com.zerobias.module.x12.producer.RecastHook;
import com.zerobias.module.x12.producer.SchemaRegistry;
import com.zerobias.module.x12.producer.X12Operations;
import com.zerobias.module.x12.producer.X12ProducerFacade;
import io.javalin.Javalin;
import io.javalin.http.HttpResponseException;
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
import java.util.ServiceLoader;
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
 *       {@code BinaryApi.downloadBinary} streams the file bytes (DESIGN §2.8) and
 *       {@code BinaryApi.uploadBinaryContent} takes them as the request body (DESIGN §2.9)</li>
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

    /**
     * Largest accepted request body: 64 MiB, the receiver's {@code maxFileBytes} default, so a
     * real 835 batch can be uploaded raw (Javalin's own default is 1 MiB). Must match
     * {@code client_max_body_size 64m} in both committed nginx confs, which default to 1 MiB
     * too; a base64 JSON upload inflates by a third, so that intake tops out near 48 MiB.
     */
    static final long MAX_REQUEST_BYTES = 64L * 1024 * 1024;

    /** Profile fields safe to log/display (the profile is informational; the daemon never reads it). */
    private static final Set<String> NONSENSITIVE_PROFILE_FIELDS = Set.of("ackDurability");

    private final Map<String, String> connections = new ConcurrentHashMap<>();
    private X12ProducerFacade facade;
    private BufferStore buffer;
    private RetentionSweeper retentionSweeper;
    private PollerHandle pollers;
    private HealthCheck health;

    X12ApiServer() {
    }

    public static void main(String[] args) {
        try {
            ModuleConfig config = ModuleConfig.fromEnv();
            ModuleRuntimeConfig mc = ModuleRuntimeConfig.fromEnv(config);
            new X12ApiServer().start(config, mc);
        } catch (Exception fatal) {
            // DESIGN §3: a daemon that cannot mark files consumed must not run.
            LOG.error("X12 receiver failed to start: {}", fatal.getMessage(), fatal);
            System.exit(1);
        }
    }

    void start(ModuleConfig config, ModuleRuntimeConfig mc) throws Exception {
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
        this.buffer = new BufferStore(config.bufferDbPath(), mc.fullDurability());
        LOG.info("Buffer open at {} (ackDurability={})", config.bufferDbPath(),
            mc.fullDurability() ? "full" : "normal");
        // Rows buffered before the object graph existed have no body: rebuild it from raw_x12
        // before anything can drain or browse them (and before the pollers add new rows).
        GraphBackfill.run(buffer, new MaterializerRecastHook(new StructureResolver(), Clock.systemUTC()));

        if (mc.retention().isBounded()) {
            this.retentionSweeper = new RetentionSweeper(buffer, mc.retention(), Clock.systemUTC());
            this.retentionSweeper.start(Duration.ofMinutes(10));
            LOG.info("Retention sweeper started: maxAge={}, maxBytes={}",
                mc.retention().maxAge(), mc.retention().maxBytes());
        } else {
            LOG.info("Retention: unbounded (no maxAge/maxBytes in module config)");
        }

        // ==== INBOX HOOK ====================================================
        // The inbox package plugs in via InboxPollerFactory (ServiceLoader provider or
        // by replacing pollerFactory()). With no provider the daemon boots with NO
        // pollers and /healthz reports poller.up=false (503) — visibly degraded.
        InboxPollerFactory factory = pollerFactory();
        this.pollers = factory.start(mc, buffer, retentionSweeper);
        LOG.info("Inbox pollers: {} ({} source(s) reporting)",
            factory == InboxPollerFactory.NONE ? "NONE (no InboxPollerFactory provider)" : factory.getClass().getName(),
            pollers.sources().size());
        // ====================================================================

        this.health = new HealthCheck(buffer, pollers);

        // --- producer surface ---
        this.facade = buildFacade(buffer, mc, pollers);

        Javalin app = Javalin.create(cfg -> {
            cfg.http.defaultContentType = "application/json";
            cfg.showJavalinBanner = false;
            cfg.http.maxRequestSize = MAX_REQUEST_BYTES;
        });
        registerExceptionHandlers(app);
        registerRoutes(app);
        app.start(config.internalPort());
        LOG.info("Operations server listening on {}", config.internalPort());

        Runtime.getRuntime().addShutdownHook(new Thread(
            () -> shutdown(app::stop, pollers, retentionSweeper, buffer), "x12-shutdown"));
    }

    /**
     * Stop everything that uses the buffer, then the buffer. Routes first (no new take/ack or
     * file upload lands mid-close), then the pollers (each finishes the file in hand), then
     * the sweeper, and only then the buffer — closing it earlier failed whatever was still
     * running against it. Each step is attempted even if an earlier one threw.
     */
    static void shutdown(Runnable stopRoutes, PollerHandle pollers, RetentionSweeper sweeper, AutoCloseable buffer) {
        try {
            stopRoutes.run();
        } catch (RuntimeException e) {
            LOG.warn("http shutdown", e);
        }
        try {
            pollers.close();
        } catch (RuntimeException e) {
            LOG.warn("poller shutdown", e);
        }
        if (sweeper != null) {
            sweeper.stop();
        }
        try {
            buffer.close();
        } catch (Exception e) {
            LOG.warn("buffer shutdown", e);
        }
    }

    /**
     * The poller factory: the first {@link ServiceLoader} provider of
     * {@link InboxPollerFactory}, else {@link InboxPollerFactory#NONE}.
     */
    static InboxPollerFactory pollerFactory() {
        for (InboxPollerFactory f : ServiceLoader.load(InboxPollerFactory.class)) {
            return f;
        }
        return InboxPollerFactory.NONE;
    }

    /**
     * ==== PRODUCER HOOK ====
     * The producer surface (DESIGN §12 step 4): the classpath {@link SchemaRegistry}, the
     * emergent {@link ObjectTree} over the buffer (with the live poller status feeding
     * {@code /stats}), and the {@link X12Operations} functions with the
     * {@link MaterializerRecastHook} behind {@code recast}/{@code validate} (re-parse the
     * stored raw under the classpath structure indexes).
     */
    static X12ProducerFacade buildFacade(BufferStore buffer, ModuleRuntimeConfig mc, PollerHandle pollers) {
        SchemaRegistry schemas = SchemaRegistry.fromClasspath();
        String consumedSuffix = mc == null ? ModuleRuntimeConfig.DEFAULT_CONSUMED_SUFFIX : mc.consumedSuffix();
        String errorSuffix = mc == null ? ModuleRuntimeConfig.DEFAULT_ERROR_SUFFIX : mc.errorSuffix();
        List<SourceConfig> sources = mc == null ? List.of() : mc.sources();
        boolean fileManagement = mc != null && mc.allowFileManagement();
        // Business element schemas are generated from the mappings, so a /claims collection can
        // advertise a collectionSchema the registry actually serves (DESIGN §8.5).
        List<com.zerobias.module.x12.producer.mapping.EntityMapping> mappings =
            com.zerobias.module.x12.producer.BusinessEntities.mappingsFor(
                com.zerobias.module.x12.producer.PackCatalog.fromClasspath().guides());
        schemas.addMappingSchemas(mappings);
        ObjectTreeApi tree = new ObjectTree(buffer, schemas, () -> pollers, consumedSuffix, sources, errorSuffix);
        RecastHook recaster = new MaterializerRecastHook(new StructureResolver(), Clock.systemUTC());
        OperationsApi ops = new X12Operations(buffer, X12ProducerFacade::toElement, () -> pollers, schemas, recaster);
        LOG.info("Business entities: {}", mappings.stream()
            .map(m -> m.collection() + " (" + m.name() + " @ " + m.anchorSchemaId() + ")").toList());
        LOG.info("Producer: {} schema(s), tree={}, ops={}, fileManagement={}", schemas.size(),
            tree.getClass().getSimpleName(), ops.getClass().getSimpleName(),
            fileManagement ? "ENABLED (uploads/mkdir/delete accepted under /inbox)" : "disabled (receive-only)");
        return new X12ProducerFacade(buffer, tree, schemas, ops, fileManagement);
    }

    void registerRoutes(Javalin app) {
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
            md.addProperty("bufferDepth", buffer.count());
            ctx.result(md.toString());
        });

        app.get("/connections/{connectionId}/isSupported/{operationId}", ctx -> {
            requireConnection(ctx.pathParam("connectionId"));
            JsonObject body = new JsonObject();
            body.addProperty("supported", supported(ctx.pathParam("operationId")));
            ctx.result(body.toString());
        });

        app.post("/connections/{connectionId}/{method}", ctx -> {
            requireConnection(ctx.pathParam("connectionId"));
            String method = ctx.pathParam("method");
            if (OperationRouter.isBinaryUpload(method)) {
                // DESIGN §2.9: the request body is the file's bytes, so this one op cannot
                // read its arguments from the JSON argMap envelope. Handled before any
                // attempt to parse the body as JSON.
                ctx.status(201).contentType("application/json").result(upload(ctx));
                return;
            }
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
     * {@code Content-Length} rather than read onto the heap. Compression stays off — Javalin
     * would gzip the stream after the length was set — and Javalin closes the stream once it
     * is written.
     */
    static void streamBinary(io.javalin.http.Context ctx, BinaryContent bin) {
        InputStream in;
        try {
            in = bin.open();
        } catch (IOException e) {
            // Removed (or swapped for a symlink) since it was resolved.
            LOG.warn("download: {} vanished between resolve and open: {}", bin.objectId(), e.toString());
            throw ProducerException.fileGone(bin.objectId());
        }
        ctx.minSizeForCompression(Integer.MAX_VALUE);
        ctx.contentType(bin.mimeType() == null ? BinaryContent.MIME_X12 : bin.mimeType());
        ctx.res().setCharacterEncoding(null);   // bytes, not text: no charset on the Content-Type
        ctx.header("Content-Length", Long.toString(bin.size()));
        ctx.header("Content-Disposition", contentDisposition(bin.fileName()));
        ctx.result(in);
    }

    /**
     * RFC 6266 {@code attachment} with an ASCII {@code filename} fallback (quotes,
     * backslashes, control and non-ASCII characters replaced by {@code _}) and the exact name
     * as RFC 5987 {@code filename*}. A file name is sender-controlled (it arrived on the
     * feed), so nothing in it may end the header or the quoted string.
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

    /**
     * Every error body is the interface's {@code errorModelBase}. A {@link ProducerException}
     * carries its own status; an {@link IllegalArgumentException} is a caller mistake the
     * lite-filter / sort code raises with a caller-facing message (400); anything else is a
     * 500 with a generic message — the cause is logged here, never returned, because an SQLite
     * or IO message can name buffer and inbox paths inside the container.
     */
    static void registerExceptionHandlers(Javalin app) {
        app.exception(ProducerException.class, (e, ctx) -> respond(ctx, e));
        app.exception(IllegalArgumentException.class, (e, ctx) ->
            respond(ctx, ProducerException.illegalArgument(e.getMessage())));
        // Javalin's own HTTP errors (a body over maxRequestSize is a 413) keep their status and
        // get the platform envelope, rather than being swallowed by the 500 below.
        app.exception(HttpResponseException.class, (e, ctx) -> {
            if (e.getStatus() == 413) {
                respond(ctx, ProducerException.payloadTooLarge(MAX_REQUEST_BYTES));
            } else if (e.getStatus() >= 500) {
                LOG.error("HTTP {} serving {} {}", e.getStatus(), ctx.method(), ctx.path(), e);
                respond(ctx, ProducerException.unexpected());
            } else {
                respond(ctx, ProducerException.illegalArgument(e.getMessage()));
            }
        });
        app.exception(Exception.class, (e, ctx) -> {
            LOG.error("Unexpected error serving {} {}", ctx.method(), ctx.path(), e);
            respond(ctx, ProducerException.unexpected());
        });
    }

    private static void respond(io.javalin.http.Context ctx, ProducerException e) {
        ctx.status(e.httpStatus()).contentType("application/json").result(GSON.toJson(e.toBody()));
    }

    /** The request body as a JSON object; empty body = empty object; malformed or not an object = 400. */
    private static Map<String, Object> parseBody(io.javalin.http.Context ctx) {
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

    /**
     * {@code BinaryApi.uploadBinaryContent} (DESIGN §2.9). Two intakes, because the RPC
     * envelope has nowhere to put raw bytes:
     * <ul>
     *   <li><b>raw</b> — the body is the file, {@code ?objectId=&fileName=} carry the
     *       arguments. Symmetric with download (JSON in, bytes out) and the only form that
     *       does not inflate a large interchange by a third.</li>
     *   <li><b>JSON envelope</b> — {@code {"argMap":{"objectId":…,"fileName":…,
     *       "contentBase64":…}}}, for an invoker that can only speak the uniform envelope.</li>
     * </ul>
     * Returns the new file's object metadata as the interface's {@code 201} body.
     */
    private String upload(io.javalin.http.Context ctx) throws Exception {
        // The gate answers first: a receive-only deployment says "unsupported", not "your
        // arguments are wrong" — the caller cannot fix a disabled operation by fixing its body.
        if (facade == null || !facade.fileManagementEnabled()) {
            throw ProducerException.unsupported("uploadBinaryContent is disabled: the receiver is receive-only "
                + "unless the deployment sets config.allowFileManagement=true");
        }
        String objectId = ctx.queryParam("objectId");
        String fileName = ctx.queryParam("fileName");
        byte[] bytes = ctx.bodyAsBytes();
        if (objectId == null || objectId.isBlank()) {
            Map<String, Object> argMap = castMap(parseBody(ctx).get("argMap"));
            objectId = asString(argMap.get("objectId"));
            fileName = fileName != null ? fileName : asString(argMap.get("fileName"));
            Object encoded = argMap.get("contentBase64");
            if (encoded == null) {
                throw ProducerException.illegalArgument(
                    "uploadBinaryContent needs either raw bytes with ?objectId=&fileName=, "
                    + "or an argMap carrying objectId, fileName and contentBase64");
            }
            try {
                bytes = Base64.getDecoder().decode(asString(encoded));
            } catch (IllegalArgumentException malformed) {
                throw ProducerException.illegalArgument("contentBase64 is not valid base64");
            }
        }
        return facade.uploadBinary(objectId, fileName, bytes);
    }

    /**
     * {@code isSupported}: the receiver's real capability set, not a blanket yes — an
     * explicit whitelist of the routed, implemented operations ({@link OperationRouter#isSupported}).
     * The file-management ops follow {@code config.allowFileManagement}, so an operator can see
     * from the outside whether this deployment accepts uploads.
     */
    boolean supported(String operationId) {
        return OperationRouter.isSupported(operationId, facade != null && facade.fileManagementEnabled());
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private void requireConnection(String connectionId) {
        if (!connections.containsKey(connectionId)) {
            throw ProducerException.illegalArgument("Connection not found: " + connectionId);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new HashMap<>();
    }
}

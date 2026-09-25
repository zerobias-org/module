package com.zerobias.module.x12;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    /** Profile fields safe to log/display (the profile is informational; the daemon never reads it). */
    private static final Set<String> NONSENSITIVE_PROFILE_FIELDS = Set.of("ackDurability");

    private final Map<String, String> connections = new ConcurrentHashMap<>();
    private X12ProducerFacade facade;
    private BufferStore buffer;
    private RetentionSweeper retentionSweeper;
    private PollerHandle pollers;
    private HealthCheck health;

    private X12ApiServer() {
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
        });
        registerExceptionHandlers(app);
        registerRoutes(app);
        app.start(config.internalPort());
        LOG.info("Operations server listening on {}", config.internalPort());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
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
            app.stop();
        }));
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

    private void registerRoutes(Javalin app) {
        app.get("/", ctx -> {
            JsonObject body = new JsonObject();
            body.add("nonsensitiveProfileFields", GSON.toJsonTree(NONSENSITIVE_PROFILE_FIELDS));
            ctx.result(body.toString());
        });

        app.post("/connections", ctx -> {
            Map<?, ?> requestBody = GSON.fromJson(ctx.body(), Map.class);
            Object connectionId = requestBody == null ? null : requestBody.get("connectionId");
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
            Map<String, Object> requestBody = castMap(GSON.fromJson(ctx.body(), Map.class));
            Map<String, Object> argMap = castMap(requestBody.get("argMap"));
            if (OperationRouter.isBinaryDownload(method)) {
                // DESIGN §2.8: full-content 200 with the raw EDI bytes.
                Object id = argMap.get("objectId");
                BinaryContent bin = facade.downloadBinary(id == null ? null : id.toString());
                ctx.contentType(bin.mimeType() == null ? BinaryContent.MIME_X12 : bin.mimeType());
                if (bin.fileName() != null) {
                    ctx.header("Content-Disposition", "attachment; filename=\"" + bin.fileName() + "\"");
                }
                ctx.result(bin.bytes());
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

    private void registerExceptionHandlers(Javalin app) {
        app.exception(ProducerException.class, (e, ctx) ->
            ctx.status(e.httpStatus()).contentType("application/json").result(GSON.toJson(e.toBody())));
        app.exception(IllegalArgumentException.class, (e, ctx) -> {
            ProducerException pe = ProducerException.illegalArgument(e.getMessage());
            ctx.status(pe.httpStatus()).contentType("application/json").result(GSON.toJson(pe.toBody()));
        });
        app.exception(Exception.class, (e, ctx) -> {
            LOG.error("Unexpected error", e);
            JsonObject body = new JsonObject();
            body.addProperty("code", "internalError");
            body.addProperty("message", String.valueOf(e.getMessage()));
            ctx.status(500).contentType("application/json").result(body.toString());
        });
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
        String objectId = ctx.queryParam("objectId");
        String fileName = ctx.queryParam("fileName");
        byte[] bytes = ctx.bodyAsBytes();
        if (objectId == null || objectId.isBlank()) {
            Map<String, Object> argMap = castMap(castMap(GSON.fromJson(ctx.body(), Map.class)).get("argMap"));
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

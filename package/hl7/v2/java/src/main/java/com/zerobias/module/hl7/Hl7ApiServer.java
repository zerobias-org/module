package com.zerobias.module.hl7;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zerobias.module.hl7.buffer.BufferStore;
import com.zerobias.module.hl7.buffer.RetentionSweeper;
import com.zerobias.module.hl7.listener.BufferingApp;
import com.zerobias.module.hl7.listener.Hl7ListenerService;
import com.zerobias.module.hl7.materializer.EnvelopeMaterializer;
import com.zerobias.module.hl7.ext.Discriminator;
import com.zerobias.module.hl7.ext.ExtensionLoader;
import com.zerobias.module.hl7.health.HealthCheck;
import com.zerobias.module.hl7.health.HealthSelfTest;
import com.zerobias.module.hl7.materializer.Materializer;
import com.zerobias.module.hl7.materializer.MessageMaterializer;
import com.zerobias.module.hl7.materializer.StructureIndex;
import com.zerobias.module.hl7.materializer.StructureResolver;

import java.util.List;
import com.zerobias.module.hl7.producer.Hl7ProducerFacade;
import com.zerobias.module.hl7.producer.ObjectTree;
import com.zerobias.module.hl7.producer.OperationRouter;
import com.zerobias.module.hl7.producer.ProducerException;
import com.zerobias.module.hl7.producer.SchemaRegistry;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entry point for the HL7 v2 receiver module.
 *
 * <p>Boots the always-on daemon (MLLP listener + SQLite buffer, DESIGN §4) and
 * the DataProducer operations HTTP server on {@code INTERNAL_PORT} (nginx fronts
 * it on 8888). The HTTP surface mirrors the SQL generic module's RPC contract so
 * the Hub Node's java-http invoker drives it unchanged:
 * <ul>
 *   <li>{@code GET  /} — readiness + non-sensitive profile fields</li>
 *   <li>{@code POST /connections} — connect (handshake; the daemon is already running)</li>
 *   <li>{@code PUT  /connections/{id}/disconnect} — release the session</li>
 *   <li>{@code GET  /connections/{id}/metadata} — connection metadata</li>
 *   <li>{@code GET  /connections/{id}/isSupported/{operationId}}</li>
 *   <li>{@code POST /connections/{id}/{method}} — dispatch via {@link OperationRouter}</li>
 *   <li>{@code GET  /healthz} — daemon health probe (full payload: Phase 9)</li>
 * </ul>
 *
 * <p>The MLLP listener and buffer are daemon-owned singletons: every connection
 * shares the one running buffer, so {@code connect} is a handshake, not a
 * resource acquisition. Read ops are live (Phase 6); functions land in Phase 7.
 */
public final class Hl7ApiServer {

    private static final Logger LOG = LoggerFactory.getLogger(Hl7ApiServer.class);
    private static final Gson GSON = new Gson();

    /** Profile fields safe to log/display (no secrets in this receiver profile). */
    private static final Set<String> NONSENSITIVE_PROFILE_FIELDS =
        Set.of("hl7Version", "ackDurability", "backpressurePolicy", "senderDiscriminator");

    private final Map<String, String> connections = new ConcurrentHashMap<>();
    private final java.util.List<Hl7ListenerService> listeners = new java.util.ArrayList<>();
    private Hl7ProducerFacade facade;
    private BufferStore buffer;
    private RetentionSweeper retentionSweeper;
    private HealthCheck health;

    private Hl7ApiServer() {
    }

    public static void main(String[] args) throws Exception {
        ModuleConfig config = ModuleConfig.fromEnv();
        new Hl7ApiServer().start(config);
    }

    void start(ModuleConfig config) throws Exception {
        LOG.info("HL7 v2 receiver starting: ops port {}, {} MLLP listener(s) {}, extensionDir {}",
            config.internalPort(), config.listeners().size(), config.listeners(), config.extensionDir());

        // Daemon-level knobs (ack durability, retention, active extensions) arrive via
        // the opaque MODULE_CONFIG — the only channel the platform delivers to a running
        // module. They configure the buffer at boot, before any connection exists, so
        // they cannot come from the per-connection connectionProfile. (DESIGN §3.2, §8)
        ModuleRuntimeConfig mc = ModuleRuntimeConfig.fromEnv();
        // Target HL7 version drives which baked-in structure index materializes the
        // typed JSON (DESIGN §11.5). The slot is HAPI's vNN package name (2.7 -> v27).
        final String hl7Version = mc.hl7Version();
        final String versionSlot = mc.versionSlot();
        LOG.info("Target HL7 version: {} (structure slot {})", hl7Version, versionSlot);

        // --- daemon: buffer + MLLP listener (DESIGN §4) ---
        String dbPath = System.getenv().getOrDefault("BUFFER_DB", "/var/lib/module/buffer.db");
        this.buffer = new BufferStore(dbPath, mc.fullDurability());

        // Retention trimming (DESIGN §8.3): the platform provisions the volume but
        // cannot cap a named volume's size, so the module owns trimming. Sweep on a
        // schedule when a ceiling is declared; none() = unbounded (dev/test default).
        if (mc.retention().maxAge() != null || mc.retention().maxBytes() != null) {
            this.retentionSweeper = new RetentionSweeper(buffer, mc.retention(), Clock.systemUTC());
            this.retentionSweeper.start(Duration.ofMinutes(10));
            LOG.info("Retention sweeper started: maxAge={}, maxBytes={}",
                mc.retention().maxAge(), mc.retention().maxBytes());
        } else {
            LOG.info("Retention: unbounded (no maxAge/maxBytes in MODULE_CONFIG)");
        }

        // Base schemas + structure index, both baked into the jar at build (Phase 1)
        // and served from the classpath. SCHEMA_DIR is a dev fallback for running
        // against an on-disk tree (e.g. a build that skipped the shade step).
        SchemaRegistry schemas = SchemaRegistry.fromClasspath();
        if (schemas.size() == 0) {
            Path schemaRoot = Path.of(System.getenv().getOrDefault("SCHEMA_DIR", "/opt/module/generated"));
            schemas = SchemaRegistry.fromDirectory(schemaRoot);
        }
        StructureIndex index = StructureIndex.fromClasspath(versionSlot);

        // Extensions (DESIGN §7.3): baked into the image under EXTENSION_DIR, optionally
        // narrowed by MODULE_CONFIG.activeExtensions. Merges schemas + structure-index in
        // place and yields the discriminators that route augmented structures.
        ExtensionLoader.Result ext = ExtensionLoader.load(
            Path.of(config.extensionDir()), mc.activeExtensions(), hl7Version, schemas, index);

        StructureResolver resolver = resolverFor(ext.discriminators());
        Map<String, String> extensionScopes = new java.util.LinkedHashMap<>();
        for (Discriminator d : ext.discriminators()) {
            extensionScopes.put(d.structure(), d.whereClause());
        }

        MessageMaterializer materializer = index != null
            ? new Materializer(index, resolver) : new EnvelopeMaterializer();
        LOG.info("Materializer: {}; {} schemas, {} extension(s)", index != null
            ? "structure-index " + versionSlot : "ENVELOPE fallback", schemas.size(), ext.loaded().size());

        // One listener per declared port; each tags its messages with its name for
        // per-port provenance (DESIGN §11.4). All listeners feed the one shared buffer.
        for (ListenerSpec spec : config.listeners()) {
            Hl7ListenerService l = new Hl7ListenerService(spec.port(),
                new BufferingApp(buffer, materializer, versionSlot, spec.name()));
            l.start();
            listeners.add(l);
            LOG.info("MLLP listener '{}' up on {}", spec.name(), spec.port());

            // Startup MLLP self-test (DESIGN §9): confirm each receive loop is wired.
            boolean selfTest = HealthSelfTest.run(spec.port());
            LOG.info("MLLP self-test '{}': {}", spec.name(),
                selfTest ? "OK" : "FAILED (listener may not be accepting)");
        }

        this.health = new HealthCheck(buffer, this::allListenersRunning, ext.loaded());

        // --- producer surface ---
        ObjectTree tree = new ObjectTree(buffer, schemas, versionSlot, extensionScopes);
        this.facade = new Hl7ProducerFacade(buffer, tree, schemas);

        Javalin app = Javalin.create(cfg -> {
            cfg.http.defaultContentType = "application/json";
            cfg.showJavalinBanner = false;
        });
        registerExceptionHandlers(app);
        registerRoutes(app);
        app.start(config.internalPort());
        LOG.info("Operations server listening on {}", config.internalPort());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (Hl7ListenerService l : listeners) {
                try {
                    l.close();
                } catch (Exception e) {
                    LOG.warn("listener shutdown", e);
                }
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
            md.addProperty("status", allListenersRunning() ? "On" : "Error");
            md.addProperty("bufferDepth", buffer.count());
            ctx.result(md.toString());
        });

        app.get("/connections/{connectionId}/isSupported/{operationId}", ctx -> {
            requireConnection(ctx.pathParam("connectionId"));
            ctx.result("{\"supported\":true}");
        });

        app.post("/connections/{connectionId}/{method}", ctx -> {
            requireConnection(ctx.pathParam("connectionId"));
            String method = ctx.pathParam("method");
            Map<String, Object> requestBody = castMap(GSON.fromJson(ctx.body(), Map.class));
            Map<String, Object> argMap = castMap(requestBody.get("argMap"));
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

    /** Healthy only when every bound listener is accepting — a single dead port degrades the daemon. */
    private boolean allListenersRunning() {
        return !listeners.isEmpty() && listeners.stream().allMatch(Hl7ListenerService::isRunning);
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

    /** Build a {@link StructureResolver} that routes via the first matching discriminator. */
    private static StructureResolver resolverFor(List<Discriminator> discriminators) {
        if (discriminators.isEmpty()) {
            return StructureResolver.DEFAULT;
        }
        return (code, sendingApp, dflt) -> {
            for (Discriminator d : discriminators) {
                if (d.matches(code, sendingApp)) {
                    return d.structure();
                }
            }
            return dflt;
        };
    }
}

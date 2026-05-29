package com.zerobias.module.hl7;

import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the HL7 v2 receiver module.
 *
 * <p>Phase 0 scaffold: boots the operations HTTP server on {@code INTERNAL_PORT}
 * (fronted by nginx on 8888) and asserts the daemon precondition that
 * {@code LISTENER_PORT_MLLP} was injected by Hub Node. The MLLP listener,
 * SQLite buffer, materializer, and DataProducer operations are wired in by
 * later phases (see PLAN.md):
 * <ul>
 *   <li>Phase 2 — {@code buffer.BufferStore} (SQLite + WAL)</li>
 *   <li>Phase 3 — {@code materializer.Materializer}</li>
 *   <li>Phase 4 — {@code listener.Hl7ListenerService} (HAPI HL7Service on LISTENER_PORT_MLLP)</li>
 *   <li>Phase 6 — {@code OperationRouter} + {@code Hl7ProducerFacade} (DataProducer ops)</li>
 *   <li>Phase 9 — real {@code /healthz} payload</li>
 * </ul>
 */
public final class Hl7ApiServer {

    private static final Logger LOG = LoggerFactory.getLogger(Hl7ApiServer.class);

    private Hl7ApiServer() {
    }

    public static void main(String[] args) {
        final ModuleConfig config = ModuleConfig.fromEnv();
        LOG.info("HL7 v2 receiver starting: operations port {}, MLLP port {}, extensionDir {}",
                config.internalPort(), config.mllpPort(), config.extensionDir());

        // TODO(Phase 2-6): start BufferStore, Materializer, Hl7ListenerService, and
        // register the DataProducer OperationRouter on this Javalin instance.

        final Javalin app = Javalin.create();
        app.get("/healthz", ctx -> {
            // TODO(Phase 9): real payload (listener up, bufferDepth, WAL stats, extensions).
            ctx.contentType("application/json")
               .result("{\"listener\":{\"up\":false},\"status\":\"starting\"}");
        });
        app.start(config.internalPort());

        LOG.info("Operations server listening on {}", config.internalPort());
    }
}

package com.zerobias.module.hl7.listener;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.app.HL7Service;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.GenericModelClassFactory;
import ca.uhn.hl7v2.protocol.ReceivingApplication;
import ca.uhn.hl7v2.validation.impl.ValidationContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The always-on MLLP receiver (DESIGN §4.1). Wraps a HAPI {@link HL7Service}
 * bound to the listener port, registered to hand every inbound message to a
 * {@link ReceivingApplication} (the {@link BufferingApp}).
 *
 * <p>Configured to accept real-world dirty feeds: validation is disabled
 * (lenient parse), and the MLLP layer + parser handle framing and partial
 * frames. The typed structure jars are NOT needed here at runtime — generic
 * parsing + Terser is enough for the receive/buffer path (DESIGN §4.1).
 */
public final class Hl7ListenerService implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(Hl7ListenerService.class);

    private final HapiContext context;
    private final HL7Service server;
    private final int port;

    public Hl7ListenerService(int port, ReceivingApplication<Message> app) {
        this.port = port;
        this.context = new DefaultHapiContext();
        this.context.setValidationContext(ValidationContextFactory.noValidation());
        // Force generic parsing: the runtime carries no typed structure jars (DESIGN
        // §4.1) and the structure-index Materializer requires a generic model (it
        // navigates by segment code, not typed group accessors). Pinning it here makes
        // that independent of whatever happens to be on the classpath.
        this.context.setModelClassFactory(new GenericModelClassFactory());
        this.server = context.newServer(port, false);
        // Register the X01 health no-op BEFORE the wildcard: HAPI's router returns the
        // first matching binding, so ("*","X01") must precede ("*","*") to intercept the
        // startup self-test message (DESIGN §9) without it reaching the buffering app.
        this.server.registerApplication("*", "X01", new HealthNoOpApplication());
        this.server.registerApplication("*", "*", app);
    }

    /** Bind and start accepting connections; returns once the service is up. */
    public void start() throws InterruptedException {
        server.startAndWait();
        LOG.info("MLLP listener up on port {}", port);
    }

    public void stop() {
        server.stopAndWait();
        LOG.info("MLLP listener on port {} stopped", port);
    }

    public int port() {
        return port;
    }

    /** Whether the underlying MLLP service is currently accepting connections. */
    public boolean isRunning() {
        return server.isRunning();
    }

    public HapiContext context() {
        return context;
    }

    @Override
    public void close() {
        try {
            stop();
        } finally {
            try {
                context.close();
            } catch (Exception ignore) {
                // best-effort
            }
        }
    }
}

package com.zerobias.module.hl7.health;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.app.Connection;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.GenericModelClassFactory;
import ca.uhn.hl7v2.util.Terser;
import ca.uhn.hl7v2.validation.impl.ValidationContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Startup MLLP self-test (DESIGN §9): dials {@code 127.0.0.1:<listenerPort>} and
 * sends a synthetic {@code ZZZ^X01} message, asserting it round-trips with
 * {@code MSA|AA} via the X01-trigger no-op handler (which never persists). This
 * confirms the receive loop is wired end-to-end (the listener is bound, framing
 * and parsing work, and ACKs flow back) without polluting the buffer.
 */
public final class HealthSelfTest {

    private static final Logger LOG = LoggerFactory.getLogger(HealthSelfTest.class);

    private HealthSelfTest() {
    }

    /** @return true iff the synthetic message round-tripped with an AA acknowledgment. */
    public static boolean run(int listenerPort) {
        String er7 = "MSH|^~\\&|HEALTHCHECK|SELF|RECV|DEST|20260101000000||ZZZ^X01|HC-SELFTEST|P|2.7\r";
        try (HapiContext ctx = new DefaultHapiContext()) {
            ctx.setValidationContext(ValidationContextFactory.noValidation());
            ctx.setModelClassFactory(new GenericModelClassFactory());
            // Dedicated executor: closing this client context must NOT shut down the
            // shared default ExecutorService the listener relies on (see Hl7ListenerService).
            ctx.setExecutorService(java.util.concurrent.Executors.newCachedThreadPool());
            Connection conn = ctx.newClient("127.0.0.1", listenerPort, false);
            try {
                Message ack = conn.getInitiator().sendAndReceive(ctx.getPipeParser().parse(er7));
                String code = new Terser(ack).get("/MSA-1");
                boolean ok = "AA".equals(code);
                if (!ok) {
                    LOG.warn("MLLP self-test got MSA-1={} (expected AA) on port {}", code, listenerPort);
                }
                return ok;
            } finally {
                conn.close();
            }
        } catch (Exception e) {
            LOG.warn("MLLP self-test failed on port {}: {}", listenerPort, e.toString());
            return false;
        }
    }
}

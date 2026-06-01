package com.zerobias.module.hl7.health;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.app.Connection;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.GenericModelClassFactory;
import ca.uhn.hl7v2.util.Terser;
import ca.uhn.hl7v2.validation.impl.ValidationContextFactory;
import com.zerobias.module.hl7.buffer.BufferStore;
import com.zerobias.module.hl7.listener.BufferingApp;
import com.zerobias.module.hl7.listener.Hl7ListenerService;
import com.zerobias.module.hl7.materializer.EnvelopeMaterializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 9: the startup MLLP self-test against a real HAPI listener (DESIGN §9) —
 * the synthetic {@code ZZZ^X01} round-trips with AA via the X01-trigger no-op
 * handler and does NOT persist. (That this X01 binding doesn't shadow real
 * messages is covered by {@code Hl7ListenerIT}, which sends a real ADT^A01 through
 * the same listener — now carrying the X01 binding — and asserts it buffers.)
 */
class HealthSelfTestIT {

    private static int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    @Test
    void selfTestRoundTripsWithoutPolluting(@TempDir Path dir) throws Exception {
        int port = freePort();
        try (BufferStore buffer = new BufferStore(dir.resolve("buffer.db").toString(), false);
             Hl7ListenerService listener = new Hl7ListenerService(port,
                 new BufferingApp(buffer, new EnvelopeMaterializer(), "v251"))) {
            listener.start();

            assertTrue(HealthSelfTest.run(port), "self-test should round-trip AA");
            assertEquals(0, buffer.count(), "self-test must not persist");

            // REGRESSION: the self-test creates + closes its own client HapiContext.
            // If that shuts down the shared ExecutorService the listener uses, the
            // server can no longer accept connections and a real message hangs (it did
            // in a real container). A real ADT after the self-test must still be received.
            try (HapiContext ctx = new DefaultHapiContext()) {
                ctx.setValidationContext(ValidationContextFactory.noValidation());
                ctx.setModelClassFactory(new GenericModelClassFactory());
                String adt = "MSH|^~\\&|EPIC|HOSP|RECV|DEST|20260601120000||ADT^A01^ADT_A01|POST-SELFTEST|P|2.5.1\r"
                    + "PID|1||5551212^^^EPIC^MR||DOE^JANE||19850315|F\r";
                Connection c = ctx.newClient("127.0.0.1", port, false);
                Message ack = c.getInitiator().sendAndReceive(ctx.getPipeParser().parse(adt));
                assertEquals("AA", new Terser(ack).get("/MSA-1"), "real message after self-test must ACK");
                c.close();
            }
            assertEquals(1, buffer.count(), "real message after self-test must persist");
        }
    }
}

package com.zerobias.module.hl7.health;

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
        }
    }
}

package com.zerobias.module.x12;

import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.TestRows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The shutdown hook stops routes, then pollers, then the buffer — never the buffer under a user. */
class ShutdownOrderTest {

    @Test
    void routesThenPollersThenBuffer(@TempDir Path dir) throws Exception {
        BufferStore buffer = new BufferStore(dir.resolve("buffer.db").toString(), false,
            new TestRows.MutableClock(TestRows.BASE));
        List<String> order = new ArrayList<>();
        PollerHandle pollers = new PollerHandle() {
            public RescanResult rescan(String source) { return new RescanResult(0, 0, 0, 0); }
            public boolean up() { return true; }
            public Optional<Instant> lastScan() { return Optional.empty(); }
            public Optional<Instant> lastConsumed() { return Optional.empty(); }
            public boolean backpressure() { return false; }
            public List<SourceStatus> sources() { return List.of(); }
            public void close() {
                try {
                    buffer.count();   // a scan finishing its file still needs the buffer
                } catch (Exception e) {
                    throw new AssertionError("buffer closed before the pollers", e);
                }
                order.add("pollers");
            }
        };
        Runnable routes = () -> {
            try {
                buffer.count();   // an in-flight take/ack still needs the buffer
            } catch (Exception e) {
                throw new AssertionError("buffer closed before the routes", e);
            }
            order.add("routes");
        };

        X12ApiServer.shutdown(routes, pollers, null, buffer);
        assertEquals(List.of("routes", "pollers"), order);
        assertThrows(Exception.class, buffer::count, "the buffer is closed last");
    }

    @Test
    void aFailingStepDoesNotSkipTheRest(@TempDir Path dir) throws Exception {
        BufferStore buffer = new BufferStore(dir.resolve("buffer.db").toString(), false,
            new TestRows.MutableClock(TestRows.BASE));
        X12ApiServer.shutdown(() -> { throw new IllegalStateException("jetty"); }, InboxPollerFactory.NONE
            .start(ModuleRuntimeConfig.defaults(), buffer, null), null, buffer);
        assertThrows(Exception.class, buffer::count, "buffer still closed");
    }
}

package com.zerobias.module.x12.health;

import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.FileStatus;
import com.zerobias.module.x12.buffer.TestRows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.zerobias.module.x12.buffer.TestRows.FILE_A;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The {@code /healthz} payload (DESIGN §9) over a real SQLite buffer, and the 200/503 gate. */
class HealthCheckTest {

    private static PollerStatus poller(boolean up, boolean backpressure, List<PollerStatus.SourceStatus> sources) {
        return new PollerStatus() {
            public boolean up() { return up; }
            public Optional<Instant> lastScan() { return Optional.of(TestRows.BASE.plusSeconds(5)); }
            public Optional<Instant> lastConsumed() { return Optional.empty(); }
            public boolean backpressure() { return backpressure; }
            public List<SourceStatus> sources() { return sources; }
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> poller(Map<String, Object> status) {
        return (Map<String, Object>) status.get("poller");
    }

    @Test
    void emptyBufferWithNoPollerIsDegradedAndOmitsOptionalFields(@TempDir Path dir) throws Exception {
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false)) {
            HealthCheck h = new HealthCheck(b, PollerStatus.DOWN);
            assertFalse(h.healthy(), "no poller wired in → 503");
            Map<String, Object> p = poller(h.status());
            assertEquals(false, p.get("up"));
            assertEquals(0L, ((Number) p.get("bufferDepth")).longValue());
            assertEquals(false, p.get("backpressure"));
            assertFalse(p.containsKey("lastScan"));
            assertFalse(p.containsKey("lastConsumed"));
            assertFalse(p.containsKey("oldestUnackedSec"));
            assertEquals(List.of(), p.get("sources"));
            @SuppressWarnings("unchecked")
            Map<String, Object> db = (Map<String, Object>) h.status().get("db");
            assertTrue(((Number) db.get("walBytes")).longValue() >= 0);
            assertTrue(((Number) db.get("sizeBytes")).longValue() > 0);
            assertFalse(new HealthCheck(b, null).healthy(), "null poller behaves as DOWN");
        }
    }

    @Test
    void populatedBufferReportsDepthAgesAndSources(@TempDir Path dir) throws Exception {
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false)) {
            b.consumeFile(TestRows.file(FILE_A, "inbox", "c1", FileStatus.CONSUMED, 2),
                List.of(TestRows.tx("1", "0001", 0), TestRows.tx("1", "0002", 10)));
            HealthCheck h = new HealthCheck(b, poller(true, false,
                List.of(new PollerStatus.SourceStatus("inbox", "/var/lib/x12/inbox", true, 3, 1))));
            assertTrue(h.healthy());
            Map<String, Object> p = poller(h.status());
            assertEquals(true, p.get("up"));
            assertEquals(2L, ((Number) p.get("bufferDepth")).longValue());
            assertEquals(TestRows.BASE.plusSeconds(5).toString(), p.get("lastScan"));
            assertEquals(TestRows.BASE.toString(), p.get("lastConsumed"), "falls back to files.consumed_at");
            assertTrue(((Number) p.get("oldestUnackedSec")).longValue() >= 0);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sources = (List<Map<String, Object>>) p.get("sources");
            assertEquals(1, sources.size());
            assertEquals("inbox", sources.get(0).get("name"));
            assertEquals("/var/lib/x12/inbox", sources.get(0).get("path"));
            assertEquals(true, sources.get(0).get("writable"));
            assertEquals(3, ((Number) sources.get(0).get("pending")).intValue());
            assertEquals(1, ((Number) sources.get(0).get("errored")).intValue());
        }
    }

    @Test
    void degradedWhenSourceUnwritableOrUnderBackpressure(@TempDir Path dir) throws Exception {
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false)) {
            List<PollerStatus.SourceStatus> ok = List.of(new PollerStatus.SourceStatus("a", "/a", true, 0, 0));
            assertTrue(new HealthCheck(b, poller(true, false, ok)).healthy());
            assertFalse(new HealthCheck(b, poller(false, false, ok)).healthy(), "poller thread dead");
            assertFalse(new HealthCheck(b, poller(true, true, ok)).healthy(), "backpressure");
            assertFalse(new HealthCheck(b, poller(true, false,
                List.of(new PollerStatus.SourceStatus("a", "/a", true, 0, 0),
                        new PollerStatus.SourceStatus("b", "/b", false, 0, 0)))).healthy(), "one unwritable source");
            assertEquals(true, poller(new HealthCheck(b, poller(true, true, ok)).status()).get("backpressure"));
        }
    }
}

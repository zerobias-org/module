package com.zerobias.module.hl7.health;

import com.zerobias.module.hl7.buffer.BufferRow;
import com.zerobias.module.hl7.buffer.BufferStore;
import com.zerobias.module.hl7.buffer.MessageStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 9: the {@code /healthz} payload (DESIGN §9) built over a real SQLite
 * buffer. Validates field presence/omission and the healthy/degraded gate.
 */
class HealthCheckTest {

    private static void insert(BufferStore b, String cid, long offsetSec) throws Exception {
        b.insert(new BufferRow(0, Instant.parse("2026-05-28T00:00:00Z").plusSeconds(offsetSec), cid,
            "ADT_A01", "ADT", "A01", "EPIC", "HOSP", "2.7", "schema:table:hl7v2.v27.ADT_A01",
            ("raw-" + cid).getBytes(), "{}", MessageStatus.NEW, null, null, null));
    }

    @Test
    void emptyBufferOmitsOptionalFields(@TempDir Path dir) throws Exception {
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false)) {
            HealthCheck h = new HealthCheck(b, () -> true, List.of());
            Map<String, Object> status = h.status();

            @SuppressWarnings("unchecked")
            Map<String, Object> listener = (Map<String, Object>) status.get("listener");
            assertEquals(true, listener.get("up"));
            assertEquals(0L, ((Number) listener.get("bufferDepth")).longValue());
            assertFalse(listener.containsKey("lastReceived"), "no lastReceived when empty");
            assertFalse(listener.containsKey("oldestUnackedSec"), "no oldestUnackedSec when empty");

            @SuppressWarnings("unchecked")
            Map<String, Object> db = (Map<String, Object>) status.get("db");
            assertTrue(((Number) db.get("walBytes")).longValue() >= 0, "walBytes present");

            assertEquals(List.of(), status.get("extensions"));
        }
    }

    @Test
    void populatedBufferReportsDepthAndAges(@TempDir Path dir) throws Exception {
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false)) {
            insert(b, "A", 0);
            insert(b, "B", 10);
            HealthCheck h = new HealthCheck(b, () -> true,
                List.of(new HealthCheck.ExtensionInfo("@zerobias-org/hl7-extension-epic-adt@1.2.4", 7)));
            Map<String, Object> status = h.status();

            @SuppressWarnings("unchecked")
            Map<String, Object> listener = (Map<String, Object>) status.get("listener");
            assertEquals(2L, ((Number) listener.get("bufferDepth")).longValue());
            assertTrue(listener.containsKey("lastReceived"), "lastReceived present");
            assertTrue(((Number) listener.get("oldestUnackedSec")).longValue() >= 0, "oldestUnackedSec present");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> exts = (List<Map<String, Object>>) status.get("extensions");
            assertEquals(1, exts.size());
            assertEquals("@zerobias-org/hl7-extension-epic-adt@1.2.4", exts.get(0).get("artifact"));
            assertEquals(7, ((Number) exts.get(0).get("schemasLoaded")).intValue());
        }
    }

    @Test
    void oldestUnackedReflectsYoungestRemainingAfterPartialAck(@TempDir Path dir) throws Exception {
        // Fixed "now" 1000s after the base instant, so received_at ages are exact.
        Instant base = Instant.parse("2026-05-28T00:00:00Z");
        Clock now = Clock.fixed(base.plusSeconds(1000), ZoneOffset.UTC);
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false, now)) {
            insert(b, "OLD", 0);     // received at base     → age 1000s
            insert(b, "YOUNG", 600); // received at base+600 → age 400s

            // Ack the OLD row (taken first, FIFO), leaving only YOUNG unacked.
            var lease = b.take(null, 1, Duration.ofMinutes(5));
            assertEquals(1, b.ack(lease.leaseId(), null));

            HealthCheck h = new HealthCheck(b, () -> true, List.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> listener = (Map<String, Object>) h.status().get("listener");

            // bufferDepth counts ALL rows (acked + unacked); oldestUnackedSec must track
            // the youngest-still-unacked row, never the older acked one.
            assertEquals(2L, ((Number) listener.get("bufferDepth")).longValue());
            assertEquals(400L, ((Number) listener.get("oldestUnackedSec")).longValue());
        }
    }

    @Test
    void healthyGateFollowsListener(@TempDir Path dir) throws Exception {
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false)) {
            assertTrue(new HealthCheck(b, () -> true, List.of()).healthy());
            assertFalse(new HealthCheck(b, () -> false, List.of()).healthy());
        }
    }
}

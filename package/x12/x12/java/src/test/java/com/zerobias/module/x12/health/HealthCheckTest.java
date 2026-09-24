package com.zerobias.module.x12.health;

import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.FileStatus;
import com.zerobias.module.x12.buffer.TestRows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.zerobias.module.x12.buffer.TestRows.FILE_A;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void emptyBufferWithPollerDownIsDegradedAndOmitsOptionalFields(@TempDir Path dir) throws Exception {
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false)) {
            PollerStatus neverScanned = new PollerStatus() {
                public boolean up() { return false; }
                public Optional<Instant> lastScan() { return Optional.empty(); }
                public Optional<Instant> lastConsumed() { return Optional.empty(); }
                public boolean backpressure() { return false; }
                public List<SourceStatus> sources() { return List.of(); }
            };
            HealthCheck h = new HealthCheck(b, neverScanned);
            assertFalse(h.healthy(), "no poller thread alive → 503");
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
            assertThrows(NullPointerException.class, () -> new HealthCheck(b, null), "no silent stand-in");
        }
    }

    @Test
    void populatedBufferReportsDepthAgesAndSources(@TempDir Path dir) throws Exception {
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false)) {
            b.consumeFile(TestRows.file(FILE_A, "inbox", "c1", FileStatus.CONSUMED, 2),
                List.of(TestRows.tx("1", "0001", 0), TestRows.tx("1", "0002", 10)));
            HealthCheck h = new HealthCheck(b, poller(true, false,
                List.of(SourceStatuses.idle("inbox", "/var/lib/x12/inbox", true, 3, 1))));
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
    void bufferDepthCountsOnlyUnackedRows(@TempDir Path dir) throws Exception {
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false)) {
            b.consumeFile(TestRows.file(FILE_A, "inbox", "c1", FileStatus.CONSUMED, 3),
                List.of(TestRows.tx("1", "0001", 0), TestRows.tx("1", "0002", 10), TestRows.tx("1", "0003", 20)));
            String leaseId = b.takeWhere(null, 1, Duration.ofMinutes(5)).leaseId();
            b.ack(leaseId, null);
            b.takeWhere(null, 1, Duration.ofMinutes(5));
            Map<String, Object> p = poller(new HealthCheck(b, poller(true, false, List.of())).status());
            assertEquals(2L, ((Number) p.get("bufferDepth")).longValue(),
                "new + in_flight; the acked row waiting for retention is not backlog");
        }
    }

    @Test
    void degradedWhenSourceUnwritableOrUnderBackpressure(@TempDir Path dir) throws Exception {
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false)) {
            List<PollerStatus.SourceStatus> ok = List.of(SourceStatuses.idle("a", "/a", true, 0, 0));
            assertTrue(new HealthCheck(b, poller(true, false, ok)).healthy());
            assertFalse(new HealthCheck(b, poller(false, false, ok)).healthy(), "poller thread dead");
            assertFalse(new HealthCheck(b, poller(true, true, ok)).healthy(), "backpressure");
            assertFalse(new HealthCheck(b, poller(true, false,
                List.of(SourceStatuses.idle("a", "/a", true, 0, 0),
                        SourceStatuses.idle("b", "/b", false, 0, 0)))).healthy(), "one unwritable source");
            assertEquals(true, poller(new HealthCheck(b, poller(true, true, ok)).status()).get("backpressure"));
        }
    }

    private static PollerStatus.SourceStatus scanned(Instant started, Instant completed, Instant progress,
                                                     String error, int failures) {
        return new PollerStatus.SourceStatus("inbox", "/in", true, 0, 0, 30, started, completed, progress, error, failures);
    }

    @Test
    void degradedWhenScansStallOrKeepFailingEvenThoughTheThreadIsUp(@TempDir Path dir) throws Exception {
        Instant now = TestRows.BASE.plusSeconds(3600);
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false, new TestRows.MutableClock(now))) {
            // pollIntervalSec 30 → stall window 3 × 30s + 60s grace = 150s.
            Instant recent = now.minusSeconds(20);
            Instant old = now.minusSeconds(151);
            assertTrue(new HealthCheck(b, poller(true, false, List.of(scanned(recent, recent, recent, null, 0)))).healthy());
            assertTrue(new HealthCheck(b, poller(true, false, List.of(scanned(null, null, null, null, 0)))).healthy(),
                "no scan started yet: nothing to call stalled");

            HealthCheck stalled = new HealthCheck(b, poller(true, false, List.of(scanned(old, old, old, null, 0))));
            assertFalse(stalled.healthy(), "no scan completed for > 3 intervals + grace");
            @SuppressWarnings("unchecked")
            Map<String, Object> src = ((List<Map<String, Object>>) poller(stalled.status()).get("sources")).get(0);
            assertEquals(true, src.get("stalled"));
            assertEquals(old.toString(), src.get("lastScanCompleted"));

            assertFalse(new HealthCheck(b, poller(true, false, List.of(scanned(old, null, null, null, 0)))).healthy(),
                "a first scan that never completes is a stall");
            assertTrue(new HealthCheck(b, poller(true, false, List.of(scanned(old, old, recent, null, 0)))).healthy(),
                "a long scan still finishing files is progress, not a stall");

            HealthCheck failing = new HealthCheck(b, poller(true, false,
                List.of(scanned(recent, recent, recent, "java.sql.SQLException: disk I/O error", 3))));
            assertFalse(failing.healthy(), "three failed scans in a row");
            @SuppressWarnings("unchecked")
            Map<String, Object> f = ((List<Map<String, Object>>) poller(failing.status()).get("sources")).get(0);
            assertEquals(3, f.get("consecutiveFailures"));
            assertEquals("java.sql.SQLException: disk I/O error", f.get("lastError"));
            assertTrue(new HealthCheck(b, poller(true, false,
                List.of(scanned(recent, recent, recent, "transient", 2)))).healthy(), "two is not yet a pattern");
        }
    }
}

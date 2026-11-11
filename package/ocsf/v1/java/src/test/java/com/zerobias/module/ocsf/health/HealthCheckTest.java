package com.zerobias.module.ocsf.health;

import com.zerobias.module.ocsf.buffer.BufferStore;
import com.zerobias.module.ocsf.buffer.FileStatus;
import com.zerobias.module.ocsf.TestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.zerobias.module.ocsf.TestSupport.FILE_A;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The {@code /healthz} payload (DESIGN §9) over a real SQLite buffer, and the 200/503 gate. */
class HealthCheckTest {

    private static PollerStatus poller(boolean up, boolean backpressure, List<PollerStatus.SourceStatus> sources) {
        return new PollerStatus() {
            public boolean up() { return up; }
            public Optional<Instant> lastScan() { return Optional.of(TestSupport.BASE.plusSeconds(5)); }
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
    void emptyBufferWithADownPollerIsDegradedAndOmitsOptionalFields(@TempDir Path dir) throws Exception {
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false)) {
            PollerStatus down = new PollerStatus() {
                public boolean up() { return false; }
                public Optional<Instant> lastScan() { return Optional.empty(); }
                public Optional<Instant> lastConsumed() { return Optional.empty(); }
                public boolean backpressure() { return false; }
                public List<SourceStatus> sources() { return List.of(); }
            };
            HealthCheck h = new HealthCheck(b, down);
            assertFalse(h.healthy(), "poller down → 503");
            Map<String, Object> p = poller(h.status());
            assertEquals(false, p.get("up"));
            assertEquals(0L, ((Number) p.get("eventCount")).longValue());
            assertEquals(false, p.get("backpressure"));
            assertFalse(p.containsKey("lastScan"));
            assertFalse(p.containsKey("lastConsumed"));
            assertFalse(p.containsKey("oldestUnackedSec"));
            assertEquals(List.of(), p.get("sources"));
            @SuppressWarnings("unchecked")
            Map<String, Object> db = (Map<String, Object>) h.status().get("db");
            assertTrue(((Number) db.get("walBytes")).longValue() >= 0);
            assertTrue(((Number) db.get("sizeBytes")).longValue() > 0);
            assertThrows(NullPointerException.class, () -> new HealthCheck(b, null),
                "a missing poller is a wiring bug, not a degraded-but-running receiver");
        }
    }

    @Test
    void populatedBufferReportsEventCountAndSources(@TempDir Path dir) throws Exception {
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false)) {
            b.consumeFile(TestSupport.file(FILE_A, "c1", FileStatus.CONSUMED),
                List.of(TestSupport.eventRow(FILE_A, "e1", 1000), TestSupport.eventRow(FILE_A, "e2", 2000)));
            HealthCheck h = new HealthCheck(b, poller(true, false,
                List.of(new PollerStatus.SourceStatus("inbox", "/var/lib/ocsf/inbox", true, 3, 1))));
            assertTrue(h.healthy());
            Map<String, Object> p = poller(h.status());
            assertEquals(true, p.get("up"));
            assertEquals(2L, ((Number) p.get("eventCount")).longValue());
            assertEquals(TestSupport.BASE.plusSeconds(5).toString(), p.get("lastScan"));
            assertEquals(TestSupport.BASE.toString(), p.get("lastConsumed"), "falls back to files.consumed_at");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sources = (List<Map<String, Object>>) p.get("sources");
            assertEquals(1, sources.size());
            assertEquals("inbox", sources.get(0).get("name"));
            assertEquals("/var/lib/ocsf/inbox", sources.get(0).get("path"));
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

    // ---- failing / stalled scans --------------------------------------------------------------

    private static PollerStatus.SourceStatus scanned(Instant started, Instant lastScan, Instant lastProgress,
                                                     String error, Instant errorAt) {
        return new PollerStatus.SourceStatus("inbox", "/in", true, 0, 0, 30, started, lastScan, lastScan,
            lastProgress, error, errorAt);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> source0(Map<String, Object> status) {
        return ((List<Map<String, Object>>) poller(status).get("sources")).get(0);
    }

    @Test
    void aSourceWhoseLastScanFailedIs503(@TempDir Path dir) throws Exception {
        TestSupport.MutableClock clock = new TestSupport.MutableClock(TestSupport.BASE);
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false, clock)) {
            Instant t = TestSupport.BASE;
            PollerStatus.SourceStatus failing = scanned(t.minusSeconds(60), t.minusSeconds(30), null,
                "java.sql.SQLException: disk I/O error", t.minusSeconds(1));
            HealthCheck h = new HealthCheck(b, poller(true, false, List.of(failing)));
            assertFalse(h.healthy(), "scans are failing while the thread is alive");
            Map<String, Object> m = source0(h.status());
            assertEquals(true, m.get("failing"));
            assertTrue(String.valueOf(m.get("lastError")).contains("disk I/O error"), m.toString());
            assertEquals(t.minusSeconds(1).toString(), m.get("lastErrorAt"));
            assertEquals(t.minusSeconds(30).toString(), m.get("lastScan"));

            // A later completed scan clears it; the last error stays visible for diagnosis.
            PollerStatus.SourceStatus recovered = scanned(t.minusSeconds(60), t, null, "old", t.minusSeconds(1));
            HealthCheck ok = new HealthCheck(b, poller(true, false, List.of(recovered)));
            assertTrue(ok.healthy());
            assertEquals(false, source0(ok.status()).get("failing"));
        }
    }

    @Test
    void aSourceWithNoRecentScanIsStalled(@TempDir Path dir) throws Exception {
        TestSupport.MutableClock clock = new TestSupport.MutableClock(TestSupport.BASE);
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false, clock)) {
            Instant t = TestSupport.BASE;
            // 30s cadence: window = max(3 x 30s, 120s floor) = 120s.
            PollerStatus.SourceStatus recent = scanned(t.minusSeconds(3600), t.minusSeconds(100), null, null, null);
            assertTrue(new HealthCheck(b, poller(true, false, List.of(recent))).healthy(), "inside the window");

            PollerStatus.SourceStatus wedged = scanned(t.minusSeconds(3600), t.minusSeconds(121), null, null, null);
            HealthCheck h = new HealthCheck(b, poller(true, false, List.of(wedged)));
            assertFalse(h.healthy(), "a scan wedged on a hung mount keeps up=true but moves nothing");
            assertEquals(true, source0(h.status()).get("stalled"));

            PollerStatus.SourceStatus catchingUp = scanned(t.minusSeconds(3600), t.minusSeconds(900),
                t.minusSeconds(5), null, null);
            assertTrue(new HealthCheck(b, poller(true, false, List.of(catchingUp))).healthy(),
                "a long scan that keeps finishing files is not stalled");

            PollerStatus.SourceStatus neverScanned = scanned(t.minusSeconds(600), null, null, null, null);
            assertFalse(new HealthCheck(b, poller(true, false, List.of(neverScanned))).healthy(),
                "started 10 min ago and no scan has ever completed");
            PollerStatus.SourceStatus justStarted = scanned(t.minusSeconds(10), null, null, null, null);
            assertTrue(new HealthCheck(b, poller(true, false, List.of(justStarted))).healthy());

            // A long cadence stretches the window past the floor.
            PollerStatus.SourceStatus slow = new PollerStatus.SourceStatus("s", "/s", true, 0, 0, 300,
                t.minusSeconds(3600), null, t.minusSeconds(600), null, null, null);
            assertTrue(new HealthCheck(b, poller(true, false, List.of(slow))).healthy(), "600s < 3 x 300s");
        }
    }

    @Test
    void aBufferThatRejectsEveryInsertTurnsTheProbeRed(@TempDir Path dir) throws Exception {
        // A buffer whose INSERTs all fail: every scan throws, the thread lives — the probe must not say 200.
        TestSupport.MutableClock clock = new TestSupport.MutableClock(TestSupport.BASE);
        Path inbox = Files.createDirectories(dir.resolve("inbox"));
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false, clock)) {
            com.zerobias.module.ocsf.ModuleRuntimeConfig cfg = new com.zerobias.module.ocsf.ModuleRuntimeConfig(
                List.of(new com.zerobias.module.ocsf.SourceConfig("inbox", inbox.toString(), "*", 30, 0)),
                ".done", ".error", false, com.zerobias.module.ocsf.buffer.RetentionConfig.none(), false);
            var handle = com.zerobias.module.ocsf.inbox.InboxPollerFactory.start(cfg, com.zerobias.module.ocsf.TestSupport.classes(), b, null, clock, false);
            PollerStatus wrapped = new PollerStatus() {
                public boolean up() { return true; }
                public Optional<Instant> lastScan() { return handle.lastScan(); }
                public Optional<Instant> lastConsumed() { return handle.lastConsumed(); }
                public boolean backpressure() { return handle.backpressure(); }
                public List<SourceStatus> sources() { return handle.sources(); }
            };
            try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("buffer.db"));
                 Statement st = c.createStatement()) {
                st.execute("CREATE TRIGGER broken BEFORE INSERT ON events "
                    + "BEGIN SELECT RAISE(ABORT, 'disk I/O error: events'); END");
            }
            Files.writeString(inbox.resolve("auth.ndjson"),
                com.zerobias.module.ocsf.TestSupport.event(com.zerobias.module.ocsf.TestSupport.AUTHENTICATION, "e1", 1000) + "\n");
            assertThrows(SQLException.class, () -> handle.rescan(null));

            HealthCheck h = new HealthCheck(b, wrapped);
            assertFalse(h.healthy(), "the scan failed: 503");
            Map<String, Object> m = source0(h.status());
            assertEquals(true, m.get("failing"));
            assertTrue(String.valueOf(m.get("lastError")).contains("disk I/O error"), m.toString());
            assertFalse(poller(h.status()).containsKey("lastScan"), "no scan has completed");
            handle.close();
        }
    }
}

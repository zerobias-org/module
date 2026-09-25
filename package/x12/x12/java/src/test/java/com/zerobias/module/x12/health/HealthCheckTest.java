package com.zerobias.module.x12.health;

import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.FileStatus;
import com.zerobias.module.x12.buffer.TestRows;
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
        TestRows.MutableClock clock = new TestRows.MutableClock(TestRows.BASE);
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false, clock)) {
            Instant t = TestRows.BASE;
            PollerStatus.SourceStatus failing = scanned(t.minusSeconds(60), t.minusSeconds(30), null,
                "java.sql.SQLException: NOT NULL constraint failed: transactions.mapped_json", t.minusSeconds(1));
            HealthCheck h = new HealthCheck(b, poller(true, false, List.of(failing)));
            assertFalse(h.healthy(), "scans are failing while the thread is alive");
            Map<String, Object> m = source0(h.status());
            assertEquals(true, m.get("failing"));
            assertTrue(String.valueOf(m.get("lastError")).contains("mapped_json"), m.toString());
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
        TestRows.MutableClock clock = new TestRows.MutableClock(TestRows.BASE);
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false, clock)) {
            Instant t = TestRows.BASE;
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
        // The mapped_json NOT NULL upgrade bug: every scan threw, the thread lived, /healthz said 200.
        TestRows.MutableClock clock = new TestRows.MutableClock(TestRows.BASE);
        Path inbox = Files.createDirectories(dir.resolve("inbox"));
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false, clock)) {
            com.zerobias.module.x12.ModuleRuntimeConfig cfg = new com.zerobias.module.x12.ModuleRuntimeConfig(
                List.of(new com.zerobias.module.x12.SourceConfig("inbox", inbox.toString(), "*", 30, 0)),
                ".done", ".error", false, com.zerobias.module.x12.buffer.RetentionConfig.none(), false, false);
            var handle = com.zerobias.module.x12.inbox.X12InboxPollerFactory.start(cfg, b, null, clock, false);
            PollerStatus wrapped = new PollerStatus() {
                public boolean up() { return true; }
                public Optional<Instant> lastScan() { return handle.lastScan(); }
                public Optional<Instant> lastConsumed() { return handle.lastConsumed(); }
                public boolean backpressure() { return handle.backpressure(); }
                public List<SourceStatus> sources() { return handle.sources(); }
            };
            try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("buffer.db"));
                 Statement st = c.createStatement()) {
                st.execute("CREATE TRIGGER broken BEFORE INSERT ON transactions "
                    + "BEGIN SELECT RAISE(ABORT, 'NOT NULL constraint failed: transactions.mapped_json'); END");
            }
            Files.write(inbox.resolve("remit.835"),
                com.zerobias.module.x12.parser.Fixtures.bytes(com.zerobias.module.x12.parser.Fixtures.F835));
            assertThrows(SQLException.class, () -> handle.rescan(null));

            HealthCheck h = new HealthCheck(b, wrapped);
            assertFalse(h.healthy(), "the scan failed: 503");
            Map<String, Object> m = source0(h.status());
            assertEquals(true, m.get("failing"));
            assertTrue(String.valueOf(m.get("lastError")).contains("mapped_json"), m.toString());
            assertFalse(poller(h.status()).containsKey("lastScan"), "no scan has completed");
            handle.close();
        }
    }
}

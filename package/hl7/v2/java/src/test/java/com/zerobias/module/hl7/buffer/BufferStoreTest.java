package com.zerobias.module.hl7.buffer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Buffer behavior against a real (temp-file) SQLite database (DESIGN §8): insert
 * + dedup, take/lease, full + partial ack, release, TTL revert, purge, and
 * retention by max-age. Time is driven by a {@link MutableClock} so expiry is
 * deterministic without sleeping.
 */
class BufferStoreTest {

    private static final Instant BASE = Instant.parse("2026-05-29T00:00:00Z");
    private static final String SCHEMA_A = "schema:table:hl7v2.v27.ADT_A01";
    private static final String SCHEMA_B = "schema:table:hl7v2.v27.ORU_R01";

    private BufferStore open(Path dir, MutableClock clock) throws Exception {
        return new BufferStore(dir.resolve("buffer.db").toString(), false, clock);
    }

    private static BufferRow row(String controlId, long offsetSec, String schemaId) {
        return new BufferRow(0, BASE.plusSeconds(offsetSec), controlId, "ADT_A01", "ADT", "A01",
            "EPIC", "HOSP", "2.7", schemaId, ("raw-" + controlId).getBytes(),
            "{\"controlId\":\"" + controlId + "\"}", MessageStatus.NEW, null, null, null);
    }

    @Test
    void insertDedupAndRoundTrip(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            assertTrue(s.insert(row("A", 0, SCHEMA_A)), "first insert");
            assertFalse(s.insert(row("A", 0, SCHEMA_A)), "duplicate controlId dropped");
            assertEquals(1, s.count());
            assertEquals(1, s.count(MessageStatus.NEW));

            // round-trip: take returns the stored fields intact
            Lease lease = s.take(null, 1, Duration.ofMinutes(5));
            BufferRow got = lease.messages().get(0);
            assertEquals("A", got.controlId());
            assertEquals(SCHEMA_A, got.schemaId());
            assertEquals("raw-A", new String(got.rawEr7()));
            assertEquals(MessageStatus.IN_FLIGHT, got.status());
        }
    }

    @Test
    void takeLeasesInOrderThenAck(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            s.insert(row("A", 0, SCHEMA_A));
            s.insert(row("B", 1, SCHEMA_A));
            s.insert(row("C", 2, SCHEMA_A));

            Lease lease = s.take(null, 2, Duration.ofMinutes(5));
            assertNotNull(lease.leaseId());
            assertEquals(List.of("A", "B"), lease.messages().stream().map(BufferRow::controlId).toList());
            assertEquals(2, s.count(MessageStatus.IN_FLIGHT));
            assertEquals(1, s.count(MessageStatus.NEW));
            assertEquals(1, lease.remaining(), "one row still drainable");

            assertEquals(2, s.ack(lease.leaseId(), null));
            assertEquals(2, s.count(MessageStatus.ACKED));
            assertEquals(0, s.count(MessageStatus.IN_FLIGHT));
        }
    }

    @Test
    void partialAckLeavesRestInFlight(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            s.insert(row("A", 0, SCHEMA_A));
            s.insert(row("B", 1, SCHEMA_A));
            Lease lease = s.take(null, 2, Duration.ofMinutes(5));

            assertEquals(1, s.ack(lease.leaseId(), List.of("A")));
            assertEquals(1, s.count(MessageStatus.ACKED));
            assertEquals(1, s.count(MessageStatus.IN_FLIGHT));

            // ...and it must be the RIGHT row: A acked, B still in_flight (guards the
            // control_id IN (...) subset clause, not just the 1/1 split).
            for (BufferRow r : s.search(null, 10)) {
                if (r.controlId().equals("A")) {
                    assertEquals(MessageStatus.ACKED, r.status());
                } else {
                    assertEquals(MessageStatus.IN_FLIGHT, r.status(), "B must remain in_flight");
                }
            }
        }
    }

    @Test
    void releaseReturnsToNew(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            s.insert(row("A", 0, SCHEMA_A));
            s.insert(row("B", 1, SCHEMA_A));
            Lease lease = s.take(null, 2, Duration.ofMinutes(5));

            assertEquals(2, s.release(lease.leaseId(), null));
            assertEquals(2, s.count(MessageStatus.NEW));
            assertEquals(0, s.count(MessageStatus.IN_FLIGHT));
        }
    }

    @Test
    void expiredLeaseRevertsAndIsRetakeable(@TempDir Path dir) throws Exception {
        MutableClock clock = new MutableClock(BASE);
        try (BufferStore s = open(dir, clock)) {
            s.insert(row("A", 0, SCHEMA_A));
            String firstLease = s.take(null, 1, Duration.ofMinutes(1)).leaseId();
            assertNotNull(firstLease);
            assertEquals(1, s.count(MessageStatus.IN_FLIGHT));

            // Before expiry, the row is not drainable.
            assertTrue(s.take(null, 1, Duration.ofMinutes(1)).isEmpty());

            clock.advance(Duration.ofMinutes(2)); // lease TTL elapses

            assertEquals(1, s.reclaimExpiredLeases());
            assertEquals(1, s.count(MessageStatus.NEW));

            // And a fresh take re-leases it under a new lease id.
            Lease retake = s.take(null, 1, Duration.ofMinutes(1));
            assertFalse(retake.isEmpty());
            assertEquals("A", retake.messages().get(0).controlId());
        }
    }

    @Test
    void purgeRemovesOldAckedOnly(@TempDir Path dir) throws Exception {
        MutableClock clock = new MutableClock(BASE);
        try (BufferStore s = open(dir, clock)) {
            s.insert(row("A", 0, SCHEMA_A));
            s.ack(s.take(null, 1, Duration.ofMinutes(5)).leaseId(), null); // acked at BASE
            s.insert(row("B", 1, SCHEMA_A)); // stays new

            clock.advance(Duration.ofDays(1));
            assertEquals(1, s.purge(Duration.ofHours(1)));
            assertEquals(0, s.count(MessageStatus.ACKED));
            assertEquals(1, s.count(MessageStatus.NEW), "un-acked row survives purge");
        }
    }

    @Test
    void purgeIncludesRowsAckedAtTheCutoffInstant(@TempDir Path dir) throws Exception {
        // Regression: deleteAckedOlderThanMillis uses `acked_at <= cutoff`. With strict
        // `<` (the pre-fix bug) a row acked at the current instant escaped purge(PT0S).
        // No clock advance — acked_at == now == cutoff.
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            s.insert(row("A", 0, SCHEMA_A));
            s.ack(s.take(null, 1, Duration.ofMinutes(5)).leaseId(), null); // acked at BASE
            assertEquals(1, s.count(MessageStatus.ACKED));

            assertEquals(1, s.purge(Duration.ZERO), "purge(PT0S) must include just-acked rows");
            assertEquals(0, s.count(MessageStatus.ACKED));
            assertEquals(0, s.count());
        }
    }

    @Test
    void retentionByMaxBytesEvictsAckedOnly(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            s.insert(row("A", 0, SCHEMA_A));
            s.insert(row("B", 1, SCHEMA_A));
            s.ack(s.take(null, 2, Duration.ofMinutes(5)).leaseId(), null); // A,B acked
            s.insert(row("C", 2, SCHEMA_A)); // un-acked

            // maxBytes=0 forces the eviction loop to run until no acked rows remain
            // (page sizes make exact-byte assertions brittle; this exercises the whole
            // maxBytes branch: deleteOldestAcked + incrementalVacuum + acked-only + break).
            RetentionSweeper sweeper = new RetentionSweeper(s, new RetentionConfig(null, 0L), new MutableClock(BASE));
            assertEquals(2, sweeper.sweep());
            assertEquals(0, s.count(MessageStatus.ACKED));
            assertEquals(1, s.count(MessageStatus.NEW), "un-acked row is never evicted by maxBytes");
        }
    }

    @Test
    void retentionByMaxAgeEvictsAckedOnly(@TempDir Path dir) throws Exception {
        MutableClock clock = new MutableClock(BASE);
        try (BufferStore s = open(dir, clock)) {
            s.insert(row("A", 0, SCHEMA_A));
            s.insert(row("B", 1, SCHEMA_A));
            s.ack(s.take(null, 2, Duration.ofMinutes(5)).leaseId(), null); // both acked at BASE
            s.insert(row("C", 2, SCHEMA_A)); // un-acked

            clock.advance(Duration.ofDays(10));
            RetentionSweeper sweeper = new RetentionSweeper(s, RetentionConfig.maxAge(Duration.ofDays(7)), clock);

            assertEquals(2, sweeper.sweep());
            assertEquals(0, s.count(MessageStatus.ACKED));
            assertEquals(1, s.count(MessageStatus.NEW), "un-acked row is never evicted");
        }
    }

    @Test
    void takeFiltersBySchemaId(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            s.insert(row("A", 0, SCHEMA_A));
            s.insert(row("B", 1, SCHEMA_B));
            s.insert(row("C", 2, SCHEMA_A));

            Lease lease = s.take(SCHEMA_A, 10, Duration.ofMinutes(5));
            assertEquals(List.of("A", "C"), lease.messages().stream().map(BufferRow::controlId).toList());
            assertEquals(1, s.count(MessageStatus.NEW), "the ORU row was not taken");
        }
    }

    /** Test clock whose instant can be advanced to exercise TTL/retention deterministically. */
    static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant start) {
            this.instant = start;
        }

        void advance(Duration d) {
            this.instant = instant.plus(d);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}

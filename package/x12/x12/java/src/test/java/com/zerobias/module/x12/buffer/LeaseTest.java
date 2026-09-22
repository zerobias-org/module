package com.zerobias.module.x12.buffer;

import com.zerobias.module.x12.buffer.TestRows.MutableClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static com.zerobias.module.x12.buffer.TestRows.BASE;
import static com.zerobias.module.x12.buffer.TestRows.FILE_A;
import static com.zerobias.module.x12.buffer.TestRows.FILE_B;
import static com.zerobias.module.x12.buffer.TestRows.SCHEMA_835;
import static com.zerobias.module.x12.buffer.TestRows.SCHEMA_837P;
import static com.zerobias.module.x12.buffer.TestRows.tx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lease/drain semantics (DESIGN §2.5, hl7/v2 §8.2): FIFO take, full + partial ack by
 * element key, release, TTL revert + reclaim, replay, filtered take, schema-scoped take,
 * and the TTL clamp. Time is a {@link MutableClock} so expiry is deterministic.
 */
class LeaseTest {

    private BufferStore open(Path dir, MutableClock clock) throws Exception {
        return new BufferStore(dir.resolve("buffer.db").toString(), false, clock);
    }

    private static List<String> sts(Lease l) {
        return l.transactions().stream().map(TransactionRow::stControl).toList();
    }

    @Test
    void takeLeasesFifoThenAck(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            s.insertTransaction(tx("1", "0001", 0));
            s.insertTransaction(tx("1", "0002", 1));
            s.insertTransaction(tx("1", "0003", 2));

            Lease lease = s.take(null, 2, Duration.ofMinutes(5));
            assertNotNull(lease.leaseId());
            assertEquals(List.of("0001", "0002"), sts(lease));
            assertEquals(Status.IN_FLIGHT, lease.transactions().get(0).status());
            assertEquals(lease.leaseId(), lease.transactions().get(0).leaseId());
            assertEquals(BASE.plusSeconds(300), lease.transactions().get(0).inFlightUntil());
            assertEquals(2, s.count(Status.IN_FLIGHT));
            assertEquals(1, s.count(Status.NEW));
            assertEquals(1, lease.remaining(), "one row still drainable");

            assertEquals(2, s.ack(lease.leaseId(), null));
            assertEquals(2, s.count(Status.ACKED));
            assertEquals(0, s.count(Status.IN_FLIGHT));
            assertEquals(0, s.ack(lease.leaseId(), null), "second ack of the same lease finalizes nothing");
        }
    }

    @Test
    void emptyTakeHasNullLeaseIdAndReportsBacklog(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            Lease none = s.take(null, 10, Duration.ofMinutes(5));
            assertTrue(none.isEmpty());
            assertNull(none.leaseId());
            assertEquals(0, none.remaining());
        }
    }

    @Test
    void partialAckByElementKeyLeavesRestInFlight(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            s.insertTransaction(tx("1", "0001", 0));
            s.insertTransaction(tx("1", "0002", 1));
            Lease lease = s.take(null, 2, Duration.ofMinutes(5));

            assertEquals(1, s.ack(lease.leaseId(), List.of(FILE_A + ":1:0001")));
            assertEquals(Status.ACKED, s.byElementKey(FILE_A + ":1:0001").orElseThrow().status());
            assertEquals(Status.IN_FLIGHT, s.byElementKey(FILE_A + ":1:0002").orElseThrow().status(),
                "0002 must remain in_flight");
            assertEquals(0, s.ack("some-other-lease", List.of(FILE_A + ":1:0002")), "wrong lease acks nothing");
        }
    }

    @Test
    void releaseReturnsToNew(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            s.insertTransaction(tx("1", "0001", 0));
            s.insertTransaction(tx("1", "0002", 1));
            Lease lease = s.take(null, 2, Duration.ofMinutes(5));

            assertEquals(1, s.release(lease.leaseId(), List.of(FILE_A + ":1:0002")));
            assertEquals(1, s.count(Status.NEW));
            assertEquals(1, s.release(lease.leaseId(), null));
            assertEquals(2, s.count(Status.NEW));
            assertNull(s.byElementKey(FILE_A + ":1:0001").orElseThrow().leaseId());
        }
    }

    @Test
    void expiredLeaseRevertsAndIsRetakeable(@TempDir Path dir) throws Exception {
        MutableClock clock = new MutableClock(BASE);
        try (BufferStore s = open(dir, clock)) {
            s.insertTransaction(tx("1", "0001", 0));
            String first = s.take(null, 1, Duration.ofMinutes(1)).leaseId();
            assertNotNull(first);

            // Before expiry, the row is not drainable.
            assertTrue(s.take(null, 1, Duration.ofMinutes(1)).isEmpty());

            clock.advance(Duration.ofMinutes(2));
            // An expired in_flight row is drainable directly (candidate predicate)...
            Lease retake = s.take(null, 1, Duration.ofMinutes(1));
            assertFalse(retake.isEmpty());
            assertFalse(first.equals(retake.leaseId()), "new lease id");
            assertEquals(0, s.ack(first, null), "the old lease can no longer ack it");

            // ...and reclaimExpired reverts the rest explicitly.
            clock.advance(Duration.ofMinutes(2));
            assertEquals(1, s.reclaimExpired());
            assertEquals(1, s.count(Status.NEW));
        }
    }

    @Test
    void replayForcesInFlightBackToNewRegardlessOfTtl(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            s.insertTransaction(tx("1", "0001", 0));
            s.insertTransaction(tx(FILE_B, "payer-b", "7", "0001", 1, "005010X222A1", "837P", SCHEMA_837P, "S"));
            s.take(null, 2, Duration.ofHours(1));
            assertEquals(2, s.count(Status.IN_FLIGHT));

            assertEquals(1, s.replayInFlight("transaction_type = '837P'"));
            assertEquals(1, s.count(Status.NEW));
            assertEquals(1, s.replayInFlight(null));
            assertEquals(2, s.count(Status.NEW));
        }
    }

    @Test
    void takeWhereAndSchemaScopedTake(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            s.insertTransaction(tx("1", "0001", 0));
            s.insertTransaction(tx(FILE_B, "payer-b", "7", "0001", 1, "005010X222A1", "837P", SCHEMA_837P, "S"));
            s.insertTransaction(tx("1", "0003", 2));

            Lease filtered = s.takeWhere("source_name = 'payer-b'", 10, Duration.ofMinutes(5));
            assertEquals(1, filtered.transactions().size());
            assertEquals("837P", filtered.transactions().get(0).transactionType());
            assertEquals(2, filtered.remaining(), "backlog counts the unfiltered drainable rows");

            Lease bySchema = s.take(SCHEMA_835, 10, Duration.ofMinutes(5));
            assertEquals(List.of("0001", "0003"), sts(bySchema));
            assertEquals(0, s.count(Status.NEW));
        }
    }

    @Test
    void ttlIsClampedToDefaultAndMax(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            s.insertTransaction(tx("1", "0001", 0));
            s.insertTransaction(tx("1", "0002", 0));
            s.insertTransaction(tx("1", "0003", 0));
            assertEquals(BASE.plus(LeaseManager.DEFAULT_TTL),
                s.take(null, 1, null).transactions().get(0).inFlightUntil(), "null ttl → default");
            assertEquals(BASE.plus(LeaseManager.DEFAULT_TTL),
                s.take(null, 1, Duration.ZERO).transactions().get(0).inFlightUntil(), "zero ttl → default");
            assertEquals(BASE.plus(LeaseManager.MAX_TTL),
                s.take(null, 1, Duration.ofDays(3)).transactions().get(0).inFlightUntil(), "huge ttl → max");
        }
    }
}

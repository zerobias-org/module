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
import static com.zerobias.module.x12.buffer.TestRows.file;
import static com.zerobias.module.x12.buffer.TestRows.tx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Retention + purge (DESIGN §8, §2.5): only ACKED transaction rows are ever evicted;
 * un-acked rows and {@code files} rows (the audit trail) survive every sweep.
 */
class RetentionSweeperTest {

    private BufferStore open(Path dir, MutableClock clock) throws Exception {
        return new BufferStore(dir.resolve("buffer.db").toString(), false, clock);
    }

    /** Consume a file with 3 transactions, ack the first two (at BASE), leave the third new. */
    private static void seed(BufferStore s) throws Exception {
        s.consumeFile(file(FILE_A, "inbox", "c1", FileStatus.CONSUMED, 3),
            List.of(tx("1", "0001", 0), tx("1", "0002", 1), tx("1", "0003", 2)));
        s.ack(s.takeWhere(null, 2, Duration.ofMinutes(5)).leaseId(), null);
    }

    @Test
    void purgeRemovesOldAckedOnlyAndIsInclusiveAtCutoff(@TempDir Path dir) throws Exception {
        MutableClock clock = new MutableClock(BASE);
        try (BufferStore s = open(dir, clock)) {
            seed(s);
            // acked_at == now == cutoff → purge(PT0S) must include just-acked rows
            assertEquals(2, s.purge(Duration.ZERO));
            assertEquals(0, s.count(Status.ACKED));
            assertEquals(1, s.count(Status.NEW), "un-acked row survives purge");
            assertEquals(1, s.fileCount(), "files rows are never purged");
        }
    }

    @Test
    void purgeRespectsOlderThan(@TempDir Path dir) throws Exception {
        MutableClock clock = new MutableClock(BASE);
        try (BufferStore s = open(dir, clock)) {
            seed(s);
            clock.advance(Duration.ofMinutes(30));
            assertEquals(0, s.purge(Duration.ofHours(1)), "acked 30 min ago is younger than 1h");
            assertEquals(2, s.purge(Duration.ofMinutes(10)));
        }
    }

    @Test
    void maxAgeEvictsAckedOnly(@TempDir Path dir) throws Exception {
        MutableClock clock = new MutableClock(BASE);
        try (BufferStore s = open(dir, clock)) {
            seed(s);
            RetentionSweeper sweeper = new RetentionSweeper(s, new RetentionConfig(Duration.ofDays(7), null), clock);
            assertEquals(0, sweeper.sweep(), "nothing old enough yet");
            clock.advance(Duration.ofDays(10));
            assertEquals(2, sweeper.sweep());
            assertEquals(0, s.count(Status.ACKED));
            assertEquals(1, s.count(Status.NEW), "un-acked row is never evicted");
            assertEquals(1, s.fileCount(), "files rows are never evicted");
        }
    }

    @Test
    void maxBytesEvictsAckedOnlyAndReportsBackpressure(@TempDir Path dir) throws Exception {
        MutableClock clock = new MutableClock(BASE);
        try (BufferStore s = open(dir, clock)) {
            seed(s);
            // maxBytes=0 forces the eviction loop until no acked rows remain, and leaves
            // the buffer "over capacity" with nothing left to evict → backpressure.
            RetentionSweeper sweeper = new RetentionSweeper(s, new RetentionConfig(null, 0L), clock);
            assertEquals(2, sweeper.sweep());
            assertEquals(0, s.count(Status.ACKED));
            assertEquals(1, s.count(Status.NEW), "un-acked row is never evicted by maxBytes");
            assertEquals(1, s.fileCount());
            assertTrue(sweeper.overCapacity(), "still over the (zero) ceiling with nothing evictable");

            RetentionSweeper roomy = new RetentionSweeper(s, new RetentionConfig(null, Long.MAX_VALUE), clock);
            assertFalse(roomy.overCapacity());
            assertFalse(new RetentionSweeper(s, RetentionConfig.none(), clock).overCapacity(), "unbounded = never");
        }
    }

    @Test
    void unboundedConfigSweepsNothing(@TempDir Path dir) throws Exception {
        MutableClock clock = new MutableClock(BASE);
        try (BufferStore s = open(dir, clock)) {
            seed(s);
            clock.advance(Duration.ofDays(365));
            assertEquals(0, new RetentionSweeper(s, RetentionConfig.none(), clock).sweep());
            assertEquals(2, s.count(Status.ACKED));
        }
    }

    @Test
    void freedPagesCountAsRoomBeforeTheyAreVacuumed(@TempDir Path dir) throws Exception {
        MutableClock clock = new MutableClock(BASE);
        try (BufferStore s = open(dir, clock)) {
            TestRows.seedAcked(s, FILE_A, 200, 4096);
            long full = s.usedBytes();
            RetentionSweeper sweeper = new RetentionSweeper(s, new RetentionConfig(null, full / 2), clock);
            assertTrue(sweeper.overCapacity());

            assertEquals(200, s.deleteAckedOlderThanMillis(Long.MAX_VALUE));
            assertTrue(s.dbSizeBytes() >= full, "the pages are still in the file (freelist)");
            assertTrue(s.usedBytes() < full / 4, "but no longer hold data");
            assertFalse(sweeper.overCapacity(), "so backpressure lifts right after the eviction");
        }
    }

    @Test
    void purgeAndSweepHandFreedPagesBackToTheFilesystem(@TempDir Path dir) throws Exception {
        // More rows than one delete batch and more freed pages than one vacuum step: both loop to the end.
        int rows = BufferStore.DELETE_BATCH + 100;
        MutableClock clock = new MutableClock(BASE);
        try (BufferStore s = open(dir, clock)) {
            TestRows.seedAcked(s, FILE_A, rows, 12 * 1024);
            long before = s.dbSizeBytes();
            assertEquals(rows, s.purge(Duration.ZERO));
            assertTrue(s.dbSizeBytes() < before / 4, "purge vacuums: " + s.dbSizeBytes() + " of " + before);
            assertEquals(s.usedBytes(), s.dbSizeBytes(), "every free page released, not just one step's");

            TestRows.seedAcked(s, FILE_B, rows, 4096);
            before = s.dbSizeBytes();
            clock.advance(Duration.ofDays(2));
            assertEquals(rows, new RetentionSweeper(s, new RetentionConfig(Duration.ofDays(1), null), clock).sweep());
            assertTrue(s.dbSizeBytes() < before / 4, "a maxAge sweep vacuums too");
            assertEquals(s.usedBytes(), s.dbSizeBytes());
        }
    }

    @Test
    void startSweepsImmediately(@TempDir Path dir) throws Exception {
        MutableClock clock = new MutableClock(BASE);
        try (BufferStore s = open(dir, clock)) {
            seed(s);
            clock.advance(Duration.ofDays(10));
            RetentionSweeper sweeper = new RetentionSweeper(s, new RetentionConfig(Duration.ofDays(1), null), clock);
            sweeper.start(Duration.ofHours(1));
            try {
                long deadline = System.currentTimeMillis() + 10_000;
                while (s.count(Status.ACKED) > 0 && System.currentTimeMillis() < deadline) {
                    Thread.sleep(20);
                }
                assertEquals(0, s.count(Status.ACKED), "the first sweep does not wait an interval");
            } finally {
                sweeper.stop();
            }
        }
    }

    @Test
    void startAndStopAreIdempotent(@TempDir Path dir) throws Exception {
        try (BufferStore s = open(dir, new MutableClock(BASE))) {
            RetentionSweeper sweeper = new RetentionSweeper(s, new RetentionConfig(Duration.ofDays(1), null),
                new MutableClock(BASE));
            sweeper.start(Duration.ofHours(1));
            sweeper.start(Duration.ofHours(1));
            sweeper.stop();
            sweeper.stop();
        }
    }
}

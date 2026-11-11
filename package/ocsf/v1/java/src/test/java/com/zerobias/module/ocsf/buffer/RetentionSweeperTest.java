package com.zerobias.module.ocsf.buffer;

import com.zerobias.module.ocsf.TestSupport;
import com.zerobias.module.ocsf.TestSupport.MutableClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static com.zerobias.module.ocsf.TestSupport.AUTHENTICATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Retention ages events by when they were received, never by the source's own {@code time}. */
class RetentionSweeperTest {

    private static final String FILE_B = "/var/lib/ocsf/inbox/auth-b.ndjson@f5e4d3c2b1a0";

    @Test
    void maxAgeEvictsByIngestTimeAndKeepsTheFilesRows(@TempDir Path dir) throws Exception {
        MutableClock clock = new MutableClock(TestSupport.BASE);
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false, clock)) {
            // received at BASE, but the source's time is ancient: that must not age it out
            b.consumeFile(TestSupport.file(TestSupport.FILE_A, "c1", FileStatus.CONSUMED),
                List.of(TestSupport.eventRow(TestSupport.FILE_A, "old", 1)));
            clock.advance(Duration.ofDays(2));
            EventRow fresh = TestSupport.eventRow(FILE_B, "new", 1);
            b.consumeFile(TestSupport.file(FILE_B, "c2", FileStatus.CONSUMED),
                List.of(new EventRow(0, fresh.eventKey(), fresh.classUid(), fresh.categoryUid(), fresh.time(),
                    fresh.severityId(), fresh.metadataVersion(), FILE_B, "inbox", 1, clock.instant(), fresh.body())));

            RetentionSweeper sweeper = new RetentionSweeper(b, RetentionConfig.maxAge(Duration.ofDays(1)), clock);
            assertEquals(1, sweeper.sweep());
            assertTrue(b.eventBody(AUTHENTICATION, "old").isEmpty(), "received 2 days ago");
            assertTrue(b.eventBody(AUTHENTICATION, "new").isPresent(), "received now, whatever its time says");
            assertEquals(2, b.fileCount(), "files rows are the audit trail: never evicted");
            assertEquals(0, sweeper.sweep(), "nothing more to do");
        }
    }
}

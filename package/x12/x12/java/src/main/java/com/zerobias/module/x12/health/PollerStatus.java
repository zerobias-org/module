package com.zerobias.module.x12.health;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * What the inbox pollers report into {@code /healthz} and {@code /stats} (DESIGN §9).
 */
public interface PollerStatus {

    /**
     * One watched source as reported in {@code poller.sources[]}. The scan fields feed the
     * stall check ({@link HealthCheck}): when the last scan started, when one last completed,
     * when the running scan last finished a file ({@code lastProgress}), the last
     * unexpected failure and how many scans in a row have failed. {@code pollIntervalSec}
     * 0 means "no cadence known" and disables the stall check.
     */
    record SourceStatus(String name, String path, boolean writable, int pending, int errored,
                        int pollIntervalSec, Instant lastScanStarted, Instant lastScanCompleted,
                        Instant lastProgress, String lastError, int consecutiveFailures) {
    }

    /** True while every poller thread is alive. */
    boolean up();

    /** Time of the most recent scan across all sources, if any. */
    Optional<Instant> lastScan();

    /** Time of the most recent successful file consumption, if any. */
    Optional<Instant> lastConsumed();

    /** True while the buffer is over its byte ceiling and files are being left untouched (DESIGN §4.2 step 4). */
    boolean backpressure();

    /** Per-source state, in configuration order. */
    List<SourceStatus> sources();
}

package com.zerobias.module.x12.buffer;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Background retention for acked transaction sets (DESIGN §8). Applies both bounds
 * from {@link RetentionConfig}; whichever fires first wins. Only {@code acked}
 * transaction rows are ever deleted — un-acked rows are never evicted, and
 * {@code files} rows are <b>never</b> touched (they are the audit trail).
 *
 * <p>{@link #sweep()} is the unit of work (directly callable + testable);
 * {@link #start(java.time.Duration)} runs it on a schedule (10-min cadence).
 * The byte bound is measured as live data ({@link BufferStore#usedBytes()}), so pages a
 * delete freed count as room at once, and every delete path ends with an incremental
 * vacuum (auto-vacuum is enabled by {@link BufferStore} at init) so the file shrinks too.
 */
public final class RetentionSweeper {

    private static final Logger LOG = LoggerFactory.getLogger(RetentionSweeper.class);
    private static final int EVICT_BATCH = 256;

    private final BufferStore store;
    private final RetentionConfig config;
    private final Clock clock;
    /** Guards {@link #scheduler}; never the sweep's monitor, so stop does not queue behind a sweep. */
    private final Object lifecycle = new Object();
    private ScheduledExecutorService scheduler;

    public RetentionSweeper(BufferStore store, RetentionConfig config, Clock clock) {
        this.store = store;
        this.config = config;
        this.clock = clock;
    }

    /**
     * Run one retention pass; returns the number of transaction rows evicted. Synchronized so
     * the schedule and any other caller never sweep at once.
     */
    public synchronized int sweep() throws SQLException {
        int removed = 0;
        if (config.maxAge() != null) {
            final long cutoff = Instant.now(clock).toEpochMilli() - config.maxAge().toMillis();
            removed += store.deleteAckedOlderThanMillis(cutoff);
        }
        if (config.maxBytes() != null) {
            while (store.usedBytes() > config.maxBytes() && !Thread.currentThread().isInterrupted()) {
                final int n = store.deleteOldestAcked(EVICT_BATCH);
                if (n == 0) {
                    break; // nothing more we are allowed to evict
                }
                removed += n;
            }
        }
        // After whichever axis deleted anything — and harmlessly when neither did, since a
        // purge or an earlier failed sweep may have left free pages behind.
        store.incrementalVacuum();
        return removed;
    }

    /**
     * Whether live data exceeds the byte ceiling — the poller's backpressure signal
     * (DESIGN §4.2 step 4): it then leaves files untouched. Free pages do not count: they
     * are room, whether or not the file has been shrunk yet.
     */
    public boolean overCapacity() throws SQLException {
        return config.maxBytes() != null && store.usedBytes() > config.maxBytes();
    }

    /** Start the periodic sweep (e.g. every 10 minutes). Exceptions are logged, not propagated. */
    public void start(java.time.Duration interval) {
        synchronized (lifecycle) {
            startLocked(interval);
        }
    }

    private void startLocked(java.time.Duration interval) {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            final Thread t = new Thread(r, "x12-retention-sweeper");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                final int n = sweep();
                if (n > 0) {
                    LOG.info("retention sweep evicted {} acked transaction(s)", n);
                }
            } catch (Exception e) {
                LOG.warn("retention sweep failed", e);
            }
        }, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Stop the schedule and wait briefly for a sweep in progress, so the buffer can close after. */
    public void stop() {
        ScheduledExecutorService s;
        synchronized (lifecycle) {
            s = scheduler;
            scheduler = null;
        }
        if (s == null) {
            return;
        }
        s.shutdownNow();
        try {
            if (!s.awaitTermination(5, TimeUnit.SECONDS)) {
                LOG.warn("retention sweep still running at stop; abandoning it");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

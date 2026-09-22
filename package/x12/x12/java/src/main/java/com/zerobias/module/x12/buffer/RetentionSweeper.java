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
 * Byte-bounded eviction relies on incremental auto-vacuum (enabled by
 * {@link BufferStore} at init) so deletes actually reclaim file pages.
 */
public final class RetentionSweeper {

    private static final Logger LOG = LoggerFactory.getLogger(RetentionSweeper.class);
    private static final int EVICT_BATCH = 256;

    private final BufferStore store;
    private final RetentionConfig config;
    private final Clock clock;
    private ScheduledExecutorService scheduler;

    public RetentionSweeper(BufferStore store, RetentionConfig config, Clock clock) {
        this.store = store;
        this.config = config;
        this.clock = clock;
    }

    /** Run one retention pass; returns the number of transaction rows evicted. */
    public int sweep() throws SQLException {
        int removed = 0;
        if (config.maxAge() != null) {
            final long cutoff = Instant.now(clock).toEpochMilli() - config.maxAge().toMillis();
            removed += store.deleteAckedOlderThanMillis(cutoff);
        }
        if (config.maxBytes() != null) {
            while (store.dbSizeBytes() > config.maxBytes()) {
                final int n = store.deleteOldestAcked(EVICT_BATCH);
                if (n == 0) {
                    break; // nothing more we are allowed to evict
                }
                store.incrementalVacuum();
                removed += n;
            }
        }
        return removed;
    }

    /**
     * Whether the buffer is over its byte ceiling with nothing left to evict — the
     * poller's backpressure signal (DESIGN §4.2 step 4): it then leaves files untouched.
     */
    public boolean overCapacity() throws SQLException {
        return config.maxBytes() != null && store.dbSizeBytes() > config.maxBytes();
    }

    /** Start the periodic sweep (e.g. every 10 minutes). Exceptions are logged, not propagated. */
    public synchronized void start(java.time.Duration interval) {
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

    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }
}

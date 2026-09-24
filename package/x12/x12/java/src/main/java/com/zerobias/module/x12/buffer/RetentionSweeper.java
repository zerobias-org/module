package com.zerobias.module.x12.buffer;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
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
 * {@link #start(java.time.Duration)} runs it immediately and then on a schedule.
 * The byte bound is measured as live data ({@link BufferStore#usedBytes()}), so pages a
 * delete freed count as room at once; every sweep ends with an incremental vacuum
 * (auto-vacuum is enabled by {@link BufferStore} at init) so the file shrinks too.
 */
public final class RetentionSweeper {

    private static final Logger LOG = LoggerFactory.getLogger(RetentionSweeper.class);
    private static final int EVICT_BATCH = 256;

    private final BufferStore store;
    private final RetentionConfig config;
    private final Clock clock;
    private ScheduledExecutorService scheduler;

    public RetentionSweeper(BufferStore store, RetentionConfig config, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Run one retention pass; returns the number of transaction rows evicted. Synchronized
     * because the pollers also sweep (before declaring backpressure) alongside the schedule.
     */
    public synchronized int sweep() throws SQLException {
        int removed = 0;
        if (config.maxAge() != null) {
            final long cutoff = Instant.now(clock).toEpochMilli() - config.maxAge().toMillis();
            removed += store.deleteAckedOlderThanMillis(cutoff);
        }
        if (config.maxBytes() != null) {
            while (store.usedBytes() > config.maxBytes()) {
                final int n = store.deleteOldestAcked(EVICT_BATCH);
                if (n == 0) {
                    break; // nothing more we are allowed to evict
                }
                removed += n;
            }
        }
        store.incrementalVacuum();
        return removed;
    }

    /**
     * Whether live data exceeds the byte ceiling — the poller's backpressure signal
     * (DESIGN §4.2 step 4): it sweeps first and, if still over, leaves files untouched.
     */
    public boolean overCapacity() throws SQLException {
        return config.maxBytes() != null && store.usedBytes() > config.maxBytes();
    }

    /**
     * Sweep now and then every {@code interval}; a restart after an outage must not wait an
     * interval before making room. Exceptions are logged, not propagated.
     */
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
        }, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }
}

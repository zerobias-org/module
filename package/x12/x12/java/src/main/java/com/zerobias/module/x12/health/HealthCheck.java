package com.zerobias.module.x12.health;

import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.Status;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Builds the {@code /healthz} payload (DESIGN §9) for the always-on daemon. The Hub
 * Node polls this every 30s purely to know the container is healthy (a failing probe
 * raises a Node alert; it does not feed the platform event system).
 *
 * <pre>
 * { poller: { up, lastScan?, lastConsumed?, bufferDepth, oldestUnackedSec?, backpressure,
 *             sources: [ { name, path, writable, pending, errored, stalled, consecutiveFailures,
 *                          lastScanStarted?, lastScanCompleted?, lastError? } ] },
 *   db: { walBytes, sizeBytes } }
 * </pre>
 *
 * <p>503 (degraded) when the poller is down, under backpressure, or when any source is
 * unwritable, has failed {@link #FAILED_SCANS_THRESHOLD} scans in a row, or is stalled —
 * no scan completed and no file finished for {@link #STALL_INTERVALS} poll intervals plus
 * {@link #STALL_GRACE}. A live thread is not proof of ingestion: a scan wedged on a hung
 * mount, or one that throws every time, keeps {@code up=true} while nothing moves.
 * Measuring from the last finished file as well as the last completed scan keeps a long
 * catch-up scan over a large backlog from reading as a stall.
 *
 * <p>{@code bufferDepth} is the un-acked backlog ({@code new} + {@code in_flight}), the same
 * figure {@code /stats} and the connection metadata report; acked rows awaiting retention are
 * not depth.
 *
 * <p>Optional fields are omitted when there is nothing to report rather than emitting
 * misleading zeros. {@code lastConsumed} falls back to the buffer's
 * {@code files.consumed_at} when the poller does not report one (e.g. right after a
 * restart) so the field survives process restarts.
 */
public final class HealthCheck {

    static final int FAILED_SCANS_THRESHOLD = 3;
    static final int STALL_INTERVALS = 3;
    static final Duration STALL_GRACE = Duration.ofSeconds(60);

    private final BufferStore buffer;
    private final PollerStatus poller;

    public HealthCheck(BufferStore buffer, PollerStatus poller) {
        this.buffer = Objects.requireNonNull(buffer, "buffer");
        this.poller = Objects.requireNonNull(poller, "poller");
    }

    /** Whether to serve 200 (healthy) or 503 (degraded). */
    public boolean healthy() {
        if (!poller.up() || poller.backpressure()) {
            return false;
        }
        Instant now = Instant.now(buffer.clock());
        for (PollerStatus.SourceStatus s : poller.sources()) {
            if (!s.writable() || s.consecutiveFailures() >= FAILED_SCANS_THRESHOLD || stalled(s, now)) {
                return false;
            }
        }
        return true;
    }

    /** No scan completed and no file finished for longer than the stall window (see class doc). */
    static boolean stalled(PollerStatus.SourceStatus s, Instant now) {
        if (s.pollIntervalSec() <= 0 || s.lastScanStarted() == null) {
            return false;
        }
        Instant ref = latest(s.lastScanCompleted(), s.lastProgress());
        if (ref == null) {
            ref = s.lastScanStarted();
        }
        Duration window = Duration.ofSeconds((long) STALL_INTERVALS * s.pollIntervalSec()).plus(STALL_GRACE);
        return Duration.between(ref, now).compareTo(window) > 0;
    }

    private static Instant latest(Instant a, Instant b) {
        if (a == null) {
            return b;
        }
        return b == null || a.isAfter(b) ? a : b;
    }

    public Map<String, Object> status() throws SQLException {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("up", poller.up());
        poller.lastScan().ifPresent(t -> p.put("lastScan", t.toString()));
        if (poller.lastConsumed().isPresent()) {
            p.put("lastConsumed", poller.lastConsumed().get().toString());
        } else {
            OptionalLong last = buffer.lastConsumedMillis();
            if (last.isPresent()) {
                p.put("lastConsumed", Instant.ofEpochMilli(last.getAsLong()).toString());
            }
        }
        p.put("bufferDepth", buffer.count(Status.NEW) + buffer.count(Status.IN_FLIGHT));
        OptionalLong oldestUnacked = buffer.oldestUnackedSeconds();
        if (oldestUnacked.isPresent()) {
            p.put("oldestUnackedSec", oldestUnacked.getAsLong());
        }
        p.put("backpressure", poller.backpressure());

        Instant now = Instant.now(buffer.clock());
        List<Map<String, Object>> sources = new ArrayList<>();
        for (PollerStatus.SourceStatus s : poller.sources()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", s.name());
            m.put("path", s.path());
            m.put("writable", s.writable());
            m.put("pending", s.pending());
            m.put("errored", s.errored());
            m.put("stalled", stalled(s, now));
            m.put("consecutiveFailures", s.consecutiveFailures());
            if (s.lastScanStarted() != null) {
                m.put("lastScanStarted", s.lastScanStarted().toString());
            }
            if (s.lastScanCompleted() != null) {
                m.put("lastScanCompleted", s.lastScanCompleted().toString());
            }
            if (s.lastError() != null) {
                m.put("lastError", s.lastError());
            }
            sources.add(m);
        }
        p.put("sources", sources);

        Map<String, Object> db = new LinkedHashMap<>();
        db.put("walBytes", buffer.walBytes());
        db.put("sizeBytes", buffer.dbSizeBytes());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("poller", p);
        out.put("db", db);
        return out;
    }
}

package com.zerobias.module.x12.health;

import com.zerobias.module.x12.buffer.BufferStore;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Builds the {@code /healthz} payload (DESIGN §9) for the always-on daemon. The Hub
 * Node polls this every 30s purely to know the container is healthy (a failing probe
 * raises a Node alert; it does not feed the platform event system).
 *
 * <pre>
 * { poller: { up, lastScan?, lastConsumed?, bufferDepth, oldestUnackedSec?, backpressure,
 *             sources: [ { name, path, writable, pending, errored, failing, stalled,
 *                          lastScan?, lastScanStarted?, lastError?, lastErrorAt? } ] },
 *   db: { walBytes, sizeBytes } }
 * </pre>
 *
 * <p>503 (degraded) when the poller is down, under backpressure, or when any source is
 * unwritable, <b>failing</b> (its most recent scan failed — the buffer rejected the work, or
 * the directory could not be listed) or <b>stalled</b> (no scan has completed and no file has
 * finished for {@link #STALL_INTERVALS} poll intervals, at least {@link #STALL_FLOOR}). A live
 * thread is not proof of ingestion: a scan that throws every time, or one wedged on a hung
 * mount, keeps {@code up=true} while nothing moves — which is how a buffer whose INSERTs all
 * failed once looked healthy. Optional fields are omitted when there is nothing to report rather
 * than emitting misleading zeros. {@code lastConsumed} falls back to the buffer's
 * {@code files.consumed_at} when the poller does not report one (e.g. right after a
 * restart) so the field survives process restarts.
 */
public final class HealthCheck {

    /** Poll intervals without a completed scan (or a finished file) before a source is stalled. */
    static final int STALL_INTERVALS = 3;
    /** The stall window never drops below this, whatever the cadence (GC pauses, a slow mount). */
    static final Duration STALL_FLOOR = Duration.ofSeconds(120);

    private final BufferStore buffer;
    private final PollerStatus poller;

    public HealthCheck(BufferStore buffer, PollerStatus poller) {
        this.buffer = buffer;
        this.poller = poller == null ? PollerStatus.DOWN : poller;
    }

    /** Whether to serve 200 (healthy) or 503 (degraded). */
    public boolean healthy() {
        if (!poller.up() || poller.backpressure()) {
            return false;
        }
        final Instant now = Instant.now(buffer.clock());
        for (PollerStatus.SourceStatus s : poller.sources()) {
            if (!s.writable() || s.failing() || stalled(s, now)) {
                return false;
            }
        }
        return true;
    }

    /**
     * No scan completed and no file finished within the stall window, measured from the later
     * of the two — or, before the first scan ever completes, from when the poller started.
     */
    static boolean stalled(PollerStatus.SourceStatus s, Instant now) {
        if (s.pollIntervalSec() <= 0) {
            return false;
        }
        Instant ref = latest(s.lastScan(), s.lastProgress());
        if (ref == null) {
            ref = s.startedAt();
        }
        if (ref == null) {
            return false;   // never started: up() already says so
        }
        Duration window = Duration.ofSeconds((long) STALL_INTERVALS * s.pollIntervalSec());
        if (window.compareTo(STALL_FLOOR) < 0) {
            window = STALL_FLOOR;
        }
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
        p.put("bufferDepth", buffer.count());
        OptionalLong oldestUnacked = buffer.oldestUnackedSeconds();
        if (oldestUnacked.isPresent()) {
            p.put("oldestUnackedSec", oldestUnacked.getAsLong());
        }
        p.put("backpressure", poller.backpressure());

        final Instant now = Instant.now(buffer.clock());
        List<Map<String, Object>> sources = new ArrayList<>();
        for (PollerStatus.SourceStatus s : poller.sources()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", s.name());
            m.put("path", s.path());
            m.put("writable", s.writable());
            m.put("pending", s.pending());
            m.put("errored", s.errored());
            m.put("failing", s.failing());
            m.put("stalled", stalled(s, now));
            if (s.lastScan() != null) {
                m.put("lastScan", s.lastScan().toString());
            }
            if (s.lastScanStarted() != null) {
                m.put("lastScanStarted", s.lastScanStarted().toString());
            }
            if (s.lastError() != null) {
                m.put("lastError", s.lastError());
                m.put("lastErrorAt", s.lastErrorAt().toString());
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

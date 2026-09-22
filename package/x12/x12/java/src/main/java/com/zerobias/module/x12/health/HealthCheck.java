package com.zerobias.module.x12.health;

import com.zerobias.module.x12.buffer.BufferStore;

import java.sql.SQLException;
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
 *             sources: [ { name, path, writable, pending, errored } ] },
 *   db: { walBytes, sizeBytes } }
 * </pre>
 *
 * <p>503 (degraded) when the poller is down, when any source is unwritable, or under
 * backpressure. Optional fields are omitted when there is nothing to report rather
 * than emitting misleading zeros. {@code lastConsumed} falls back to the buffer's
 * {@code files.consumed_at} when the poller does not report one (e.g. right after a
 * restart) so the field survives process restarts.
 */
public final class HealthCheck {

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
        for (PollerStatus.SourceStatus s : poller.sources()) {
            if (!s.writable()) {
                return false;
            }
        }
        return true;
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

        List<Map<String, Object>> sources = new ArrayList<>();
        for (PollerStatus.SourceStatus s : poller.sources()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", s.name());
            m.put("path", s.path());
            m.put("writable", s.writable());
            m.put("pending", s.pending());
            m.put("errored", s.errored());
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

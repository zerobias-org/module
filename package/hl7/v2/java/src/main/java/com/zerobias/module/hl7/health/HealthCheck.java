package com.zerobias.module.hl7.health;

import com.zerobias.module.hl7.buffer.BufferStore;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.BooleanSupplier;

/**
 * Builds the {@code /healthz} payload (DESIGN §9) for the always-on daemon. The
 * Hub Node polls this every 30s purely to know the container is healthy — it does
 * NOT feed the platform event system (a failing probe raises a Node alert;
 * corrected 2026-06-01). Healthy iff the MLLP listener is accepting connections.
 *
 * <p>Payload shape mirrors {@code api.yml}'s {@code HealthStatus}: listener state
 * + buffer depth + oldest-unacked age, WAL bytes, and loaded extensions. Optional
 * fields ({@code lastReceived}, {@code oldestUnackedSec}) are omitted when there's
 * nothing to report rather than emitting misleading zeros.
 */
public final class HealthCheck {

    /** One loaded extension pack (Phase 8 populates the list; empty until then). */
    public record ExtensionInfo(String artifact, int schemasLoaded) {
    }

    private final BufferStore buffer;
    private final BooleanSupplier listenerUp;
    private final List<ExtensionInfo> extensions;

    public HealthCheck(BufferStore buffer, BooleanSupplier listenerUp, List<ExtensionInfo> extensions) {
        this.buffer = buffer;
        this.listenerUp = listenerUp;
        this.extensions = extensions;
    }

    /** Whether to serve 200 (healthy) or 503 (degraded): the listener must be up. */
    public boolean healthy() {
        return listenerUp.getAsBoolean();
    }

    public Map<String, Object> status() throws SQLException {
        Map<String, Object> listener = new LinkedHashMap<>();
        listener.put("up", listenerUp.getAsBoolean());
        OptionalLong lastReceived = buffer.lastReceivedMillis();
        if (lastReceived.isPresent()) {
            listener.put("lastReceived", Instant.ofEpochMilli(lastReceived.getAsLong()).toString());
        }
        listener.put("bufferDepth", buffer.count());
        OptionalLong oldestUnacked = buffer.oldestUnackedSeconds();
        if (oldestUnacked.isPresent()) {
            listener.put("oldestUnackedSec", oldestUnacked.getAsLong());
        }

        Map<String, Object> db = new LinkedHashMap<>();
        db.put("walBytes", buffer.walBytes());

        List<Map<String, Object>> exts = new ArrayList<>();
        for (ExtensionInfo e : extensions) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("artifact", e.artifact());
            m.put("schemasLoaded", e.schemasLoaded());
            exts.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("listener", listener);
        out.put("db", db);
        out.put("extensions", exts);
        return out;
    }
}

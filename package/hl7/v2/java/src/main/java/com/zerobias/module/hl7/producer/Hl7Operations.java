package com.zerobias.module.hl7.producer;

import com.zerobias.module.hl7.buffer.BufferRow;
import com.zerobias.module.hl7.buffer.BufferStore;
import com.zerobias.module.hl7.buffer.Lease;
import com.zerobias.module.hl7.filter.Hl7Filter;

import java.sql.SQLException;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The mutating drain operations exposed as Function objects under
 * {@code /hl7-v2-receiver/ops/*} (DESIGN §2.5), invoked via {@code invokeFunction}.
 * Thin I/O-shaping wrappers over the buffer's lease primitives (Phase 2):
 *
 * <ul>
 *   <li>{@code take} — lease drainable rows ({@code new} or TTL-expired in_flight),
 *       optionally narrowed by an RFC4515 {@code filter}; returns {@code leaseId},
 *       the materialized {@code messages}, and the approximate {@code remaining}
 *       backlog.</li>
 *   <li>{@code ack} — finalize a lease; optional {@code controlIds} subset
 *       (partial acks allowed — un-acked rows revert at TTL, §11.2).</li>
 *   <li>{@code release} — return a lease early without consuming.</li>
 *   <li>{@code replay} — force in_flight rows back to {@code new} (consumer
 *       recovery), optionally filtered.</li>
 *   <li>{@code purge} — delete acked rows older than a duration.</li>
 * </ul>
 *
 * <p>The {@code take.throws} codes ({@code lease_capacity_exceeded},
 * {@code backpressure}) are declared on the object but not raised in v1: there is
 * no artificial outstanding-lease cap, and buffer-full backpressure is handled on
 * the receive path ({@code MSA|AE}, §11.3), not here. Likewise {@code ack}/
 * {@code release} report affected-row counts rather than raising
 * {@code lease_expired}/{@code not_found} — a 0 count is the signal (a lease that
 * expired simply has no in_flight rows left to finalize).
 */
public final class Hl7Operations {

    private static final int DEFAULT_MAX = 100;
    private static final int MAX_CAP = 1000;
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final BufferStore buffer;
    private final Function<BufferRow, Map<String, Object>> elementMapper;

    public Hl7Operations(BufferStore buffer, Function<BufferRow, Map<String, Object>> elementMapper) {
        this.buffer = buffer;
        this.elementMapper = elementMapper;
    }

    /** Dispatch a {@code /hl7-v2-receiver/ops/<fn>} invocation. */
    public Map<String, Object> invoke(String fn, Map<String, Object> input) throws SQLException {
        switch (fn) {
            case "take":
                return take(input);
            case "ack":
                return ack(input);
            case "release":
                return release(input);
            case "replay":
                return replay(input);
            case "purge":
                return purge(input);
            default:
                throw ProducerException.noSuchObject("/hl7-v2-receiver/ops/" + fn);
        }
    }

    private Map<String, Object> take(Map<String, Object> input) throws SQLException {
        int max = clampMax(intArg(input, "max", DEFAULT_MAX));
        Duration ttl = durationArg(input, "leaseTtl", DEFAULT_TTL);
        String where = renderFilter(strArg(input, "filter"));

        Lease lease = buffer.takeWhere(where, max, ttl);
        List<Map<String, Object>> messages = new ArrayList<>(lease.messages().size());
        for (BufferRow r : lease.messages()) {
            messages.add(elementMapper.apply(r));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("leaseId", lease.leaseId());      // null when nothing was drainable
        out.put("messages", messages);
        out.put("remaining", lease.remaining());
        return out;
    }

    private Map<String, Object> ack(Map<String, Object> input) throws SQLException {
        String leaseId = requireLeaseId(input);
        int acked = buffer.ack(leaseId, controlIds(input));
        return Map.of("acked", acked);
    }

    private Map<String, Object> release(Map<String, Object> input) throws SQLException {
        String leaseId = requireLeaseId(input);
        int released = buffer.release(leaseId, controlIds(input));
        return Map.of("released", released);
    }

    private Map<String, Object> replay(Map<String, Object> input) throws SQLException {
        int replayed = buffer.replay(renderFilter(strArg(input, "filter")));
        return Map.of("replayed", replayed);
    }

    private Map<String, Object> purge(Map<String, Object> input) throws SQLException {
        // Default Duration.ZERO = delete all acked rows (acked before "now").
        Duration olderThan = durationArg(input, "olderThan", Duration.ZERO);
        int purged = buffer.purge(olderThan);
        return Map.of("purged", purged);
    }

    // --- input parsing -----------------------------------------------------

    private String renderFilter(String filter) {
        if (filter == null || filter.isBlank()) {
            return null;
        }
        try {
            return Hl7Filter.toWhereClause(filter);
        } catch (RuntimeException e) {
            throw ProducerException.illegalArgument("Malformed filter: " + e.getMessage());
        }
    }

    private String requireLeaseId(Map<String, Object> input) {
        String leaseId = strArg(input, "leaseId");
        if (leaseId == null || leaseId.isBlank()) {
            throw ProducerException.illegalArgument("leaseId is required");
        }
        return leaseId;
    }

    @SuppressWarnings("unchecked")
    private List<String> controlIds(Map<String, Object> input) {
        Object v = input.get("controlIds");
        if (v == null) {
            return null;   // full-lease ack/release
        }
        if (v instanceof List) {
            List<String> out = new ArrayList<>();
            for (Object o : (List<Object>) v) {
                out.add(String.valueOf(o));
            }
            return out;
        }
        throw ProducerException.illegalArgument("controlIds must be an array");
    }

    private int clampMax(int max) {
        if (max <= 0) {
            return DEFAULT_MAX;
        }
        return Math.min(max, MAX_CAP);
    }

    private static String strArg(Map<String, Object> input, String key) {
        Object v = input.get(key);
        return v == null ? null : v.toString();
    }

    private static int intArg(Map<String, Object> input, String key, int dflt) {
        Object v = input.get(key);
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        if (v instanceof String) {
            try {
                return Integer.parseInt((String) v);
            } catch (NumberFormatException e) {
                throw ProducerException.illegalArgument(key + " must be an integer");
            }
        }
        return dflt;
    }

    private static Duration durationArg(Map<String, Object> input, String key, Duration dflt) {
        String raw = strArg(input, key);
        if (raw == null || raw.isBlank()) {
            return dflt;
        }
        try {
            return Duration.parse(raw);   // ISO-8601, e.g. PT5M
        } catch (DateTimeParseException e) {
            throw ProducerException.illegalArgument(key + " must be an ISO-8601 duration (e.g. PT5M): " + raw);
        }
    }
}

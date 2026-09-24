package com.zerobias.module.x12.buffer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Lease mechanics over the {@code transactions} table (DESIGN §8, hl7/v2 §8.2):
 * {@code take} atomically returns a batch and marks it {@code in_flight} under a
 * lease; {@code ack} finalizes (partial subsets by element key allowed — un-acked
 * rows in the lease become drainable again at TTL); {@code release} returns a lease
 * early. An expired lease needs no sweeper: {@code take} treats its rows as drainable.
 *
 * <p>Operates on the {@link BufferStore}'s single connection; all entry points are
 * reached through the store's {@code synchronized} methods, so the
 * select-then-update {@code take} is race-free without an explicit write lock.
 */
final class LeaseManager {

    static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    static final Duration MAX_TTL = Duration.ofHours(1);

    private final Connection conn;
    private final Clock clock;

    LeaseManager(Connection conn, Clock clock) {
        this.conn = Objects.requireNonNull(conn, "conn");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Lease up to {@code max} drainable rows (new, or in_flight with an expired lease),
     * oldest first, optionally narrowed by a pre-rendered WHERE fragment (the RFC4515
     * {@code take.filter}, rendered by {@code X12SqlAdapter}; null/blank = all).
     */
    Lease take(String extraWhere, int max, Duration leaseTtl) throws SQLException {
        final long now = now();
        final Duration ttl = clampTtl(leaseTtl);
        return SqlTransaction.run(conn, () -> {
            final List<Long> ids = candidateIds(extraWhere, max, now);
            if (ids.isEmpty()) {
                return Lease.empty(backlog(now));
            }
            final String leaseId = UUID.randomUUID().toString();
            markInFlight(ids, leaseId, now + ttl.toMillis());
            final List<TransactionRow> rows = fetchByIds(ids);
            return new Lease(leaseId, rows, backlog(now));
        });
    }

    int ack(String leaseId, List<String> elementKeys) throws SQLException {
        return finalize(
            "UPDATE transactions SET status='acked', acked_at=?, lease_id=NULL, in_flight_until=NULL "
                + "WHERE lease_id=? AND status='in_flight'",
            leaseId, elementKeys, now());
    }

    int release(String leaseId, List<String> elementKeys) throws SQLException {
        return finalize(
            "UPDATE transactions SET status='new', lease_id=NULL, in_flight_until=NULL "
                + "WHERE lease_id=? AND status='in_flight'",
            leaseId, elementKeys, null);
    }

    /**
     * Force {@code in_flight} rows back to {@code new} regardless of TTL (DESIGN §2.5
     * {@code replay}). An optional pre-rendered WHERE fragment narrows the scope;
     * null/blank replays all in-flight rows. Returns rows reverted.
     */
    int replayInFlight(String extraWhere) throws SQLException {
        String sql = "UPDATE transactions SET status='new', lease_id=NULL, in_flight_until=NULL "
            + "WHERE status='in_flight'"
            + (extraWhere != null && !extraWhere.isBlank() ? " AND (" + extraWhere + ")" : "");
        try (Statement st = conn.createStatement()) {
            return st.executeUpdate(sql);
        }
    }

    // --- internals ---

    /*
     * Shaped for the transactions_unacked partial index (the un-acked rows in received_at
     * order): "status <> 'acked'" is logically redundant but is that index's WHERE clause,
     * which SQLite requires verbatim; the unary "+" keeps the planner from answering the
     * status tests through transactions_acked instead — two lookups plus a sort of every
     * drainable row per take, where the partial index stops after "max" rows.
     */
    private static final String DRAINABLE =
        "status <> 'acked' AND (+status = 'new' OR (+status = 'in_flight' AND in_flight_until < ?))";

    static final String BACKLOG_SQL = "SELECT count(*) FROM transactions WHERE " + DRAINABLE;

    static String candidateSql(String extraWhere) {
        final boolean hasFilter = extraWhere != null && !extraWhere.isBlank();
        return "SELECT id FROM transactions WHERE " + DRAINABLE + " "
            + (hasFilter ? "AND (" + extraWhere + ") " : "")
            + "ORDER BY received_at ASC, id ASC LIMIT ?";
    }

    private List<Long> candidateIds(String extraWhere, int max, long now) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(candidateSql(extraWhere))) {
            ps.setLong(1, now);
            ps.setInt(2, max);
            final List<Long> ids = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
            return ids;
        }
    }

    private void markInFlight(List<Long> ids, String leaseId, long expiry) throws SQLException {
        final String sql = "UPDATE transactions SET status='in_flight', lease_id=?, in_flight_until=? "
            + "WHERE id IN (" + placeholders(ids.size()) + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, leaseId);
            ps.setLong(i++, expiry);
            for (Long id : ids) {
                ps.setLong(i++, id);
            }
            ps.executeUpdate();
        }
    }

    private List<TransactionRow> fetchByIds(List<Long> ids) throws SQLException {
        final String sql = "SELECT " + BufferStore.TX_COLS + " FROM transactions WHERE id IN ("
            + placeholders(ids.size()) + ") ORDER BY received_at ASC, id ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setLong(i + 1, ids.get(i));
            }
            final List<TransactionRow> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(BufferStore.mapTransaction(rs));
                }
            }
            return rows;
        }
    }

    /** Approximate drainable backlog: rows that are new or have an expired lease. */
    private long backlog(long now) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(BACKLOG_SQL)) {
            ps.setLong(1, now);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    /** Shared ack/release: optional element-key subset; {@code ackedAt} non-null only for ack. */
    private int finalize(String baseSql, String leaseId, List<String> elementKeys, Long ackedAt)
            throws SQLException {
        final boolean subset = elementKeys != null && !elementKeys.isEmpty();
        final String sql = subset
            ? baseSql + " AND element_key IN (" + placeholders(elementKeys.size()) + ")"
            : baseSql;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            if (ackedAt != null) {
                ps.setLong(i++, ackedAt);
            }
            ps.setString(i++, leaseId);
            if (subset) {
                for (String k : elementKeys) {
                    ps.setString(i++, k);
                }
            }
            return ps.executeUpdate();
        }
    }

    private long now() {
        return Instant.now(clock).toEpochMilli();
    }

    static Duration clampTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return DEFAULT_TTL;
        }
        return ttl.compareTo(MAX_TTL) > 0 ? MAX_TTL : ttl;
    }

    private static String placeholders(int n) {
        return "?,".repeat(n - 1) + "?";
    }
}

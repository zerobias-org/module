package com.zerobias.module.hl7.buffer;

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
import java.util.UUID;

/**
 * Lease mechanics over the buffer (DESIGN §8.2): {@code take} atomically returns
 * a batch and marks it {@code in_flight} under a lease; {@code ack} finalizes
 * (partial subsets allowed — un-acked rows in the lease revert at TTL);
 * {@code release} returns a lease early; {@code reclaimExpired} reverts timed-out
 * leases to {@code new}.
 *
 * <p>Operates on the {@link BufferStore}'s single connection; all entry points
 * are reached through the store's {@code synchronized} methods, so the
 * select-then-update {@code take} is race-free without an explicit write lock.
 */
final class LeaseManager {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private static final Duration MAX_TTL = Duration.ofHours(1);

    private final Connection conn;
    private final Clock clock;

    LeaseManager(Connection conn, Clock clock) {
        this.conn = conn;
        this.clock = clock;
    }

    Lease take(String schemaId, int max, Duration leaseTtl) throws SQLException {
        return take(schemaId, null, max, leaseTtl);
    }

    /**
     * As {@link #take(String, int, Duration)} but with an additional pre-rendered
     * WHERE fragment (the RFC4515 {@code take.filter} from DESIGN §2.5, rendered by
     * {@code Hl7SqlAdapter}). Candidates are still constrained to drainable rows
     * (new or expired in_flight); the fragment further narrows them.
     */
    Lease take(String schemaId, String extraWhere, int max, Duration leaseTtl) throws SQLException {
        final long now = now();
        final Duration ttl = clampTtl(leaseTtl);

        final boolean prev = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            final List<Long> ids = candidateIds(schemaId, extraWhere, max, now);
            if (ids.isEmpty()) {
                conn.commit();
                return Lease.empty(backlog(now));
            }
            final String leaseId = UUID.randomUUID().toString();
            markInFlight(ids, leaseId, now + ttl.toMillis());
            final List<BufferRow> rows = fetchByIds(ids);
            final long remaining = backlog(now);
            conn.commit();
            return new Lease(leaseId, rows, remaining);
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(prev);
        }
    }

    int ack(String leaseId, List<String> controlIds) throws SQLException {
        return finalize(
            "UPDATE messages SET status='acked', acked_at=?, lease_id=NULL, in_flight_until=NULL "
                + "WHERE lease_id=? AND status='in_flight'",
            leaseId, controlIds, now());
    }

    int release(String leaseId, List<String> controlIds) throws SQLException {
        return finalize(
            "UPDATE messages SET status='new', lease_id=NULL, in_flight_until=NULL "
                + "WHERE lease_id=? AND status='in_flight'",
            leaseId, controlIds, null);
    }

    int reclaimExpired() throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE messages SET status='new', lease_id=NULL, in_flight_until=NULL "
                    + "WHERE status='in_flight' AND in_flight_until IS NOT NULL AND in_flight_until < ?")) {
            ps.setLong(1, now());
            return ps.executeUpdate();
        }
    }

    /**
     * Force {@code in_flight} rows back to {@code new} regardless of TTL (DESIGN §2.5
     * {@code replay}) — recovers messages whose consumer died without acking, so a
     * later {@code take} re-leases them. An optional pre-rendered WHERE fragment
     * narrows the scope; null/blank replays all in-flight rows. Returns rows reverted.
     */
    int replayInFlight(String extraWhere) throws SQLException {
        String sql = "UPDATE messages SET status='new', lease_id=NULL, in_flight_until=NULL "
            + "WHERE status='in_flight'"
            + (extraWhere != null && !extraWhere.isBlank() ? " AND (" + extraWhere + ")" : "");
        try (Statement st = conn.createStatement()) {
            return st.executeUpdate(sql);
        }
    }

    // --- internals ---

    private List<Long> candidateIds(String schemaId, String extraWhere, int max, long now)
            throws SQLException {
        final boolean hasFilter = extraWhere != null && !extraWhere.isBlank();
        final String sql = "SELECT id FROM messages WHERE "
            + "(status='new' OR (status='in_flight' AND in_flight_until < ?)) "
            + (schemaId != null ? "AND schema_id = ? " : "")
            + (hasFilter ? "AND (" + extraWhere + ") " : "")
            + "ORDER BY received_at ASC LIMIT ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setLong(i++, now);
            if (schemaId != null) {
                ps.setString(i++, schemaId);
            }
            ps.setInt(i, max);
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
        final String sql = "UPDATE messages SET status='in_flight', lease_id=?, in_flight_until=? "
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

    private List<BufferRow> fetchByIds(List<Long> ids) throws SQLException {
        final String sql = "SELECT " + BufferStore.COLS + " FROM messages WHERE id IN ("
            + placeholders(ids.size()) + ") ORDER BY received_at ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setLong(i + 1, ids.get(i));
            }
            final List<BufferRow> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(BufferStore.mapRow(rs));
                }
            }
            return rows;
        }
    }

    /** Approximate drainable backlog: rows that are new or have an expired lease. */
    private long backlog(long now) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT count(*) FROM messages WHERE status='new' "
                    + "OR (status='in_flight' AND in_flight_until < ?)")) {
            ps.setLong(1, now);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    /** Shared ack/release: optional control-id subset; {@code ackedAt} non-null only for ack. */
    private int finalize(String baseSql, String leaseId, List<String> controlIds, Long ackedAt)
            throws SQLException {
        final boolean subset = controlIds != null && !controlIds.isEmpty();
        final String sql = subset
            ? baseSql + " AND control_id IN (" + placeholders(controlIds.size()) + ")"
            : baseSql;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            if (ackedAt != null) {
                ps.setLong(i++, ackedAt);
            }
            ps.setString(i++, leaseId);
            if (subset) {
                for (String cid : controlIds) {
                    ps.setString(i++, cid);
                }
            }
            return ps.executeUpdate();
        }
    }

    private long now() {
        return Instant.now(clock).toEpochMilli();
    }

    private static Duration clampTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return DEFAULT_TTL;
        }
        return ttl.compareTo(MAX_TTL) > 0 ? MAX_TTL : ttl;
    }

    private static String placeholders(int n) {
        return "?,".repeat(n - 1) + "?";
    }
}

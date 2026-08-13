package com.zerobias.module.hl7.buffer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The durable message buffer (DESIGN §8): a single SQLite file in WAL mode.
 *
 * <p>Owns one JDBC connection; all methods are {@code synchronized}, which makes
 * the lease/drain operations race-free by construction for v1 (single writer).
 * A reader pool for higher browse concurrency is a later concern (Phase 4/6).
 *
 * <p>Timestamps are stored as epoch-millis INTEGERs, not ISO text — integer
 * comparison is correct for ordering/expiry, whereas {@code Instant.toString()}
 * varies in fractional precision and would mis-sort lexicographically.
 *
 * <p>Lease semantics (take/ack/release/reclaim) live in {@link LeaseManager};
 * retention policy lives in {@link RetentionSweeper}. This class owns the
 * connection, schema, inserts, counts, and the deletion primitives they use.
 */
public final class BufferStore implements AutoCloseable {

    static final String COLS = "id, received_at, control_id, message_structure, message_code, "
        + "trigger_event, sending_app, sending_facility, source_port, hl7_version, schema_id, raw_er7, "
        + "mapped_json, status, lease_id, in_flight_until, acked_at";

    private static final String SCHEMA_RESOURCE = "/buffer/schema.sql";

    private final Connection conn;
    private final Clock clock;
    private final LeaseManager leases;
    private final String dbPath;

    public BufferStore(String dbPath, boolean fullDurability, Clock clock) throws SQLException {
        this.clock = clock;
        this.dbPath = dbPath;
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        this.leases = new LeaseManager(conn, clock);
        init(fullDurability);
    }

    public BufferStore(String dbPath, boolean fullDurability) throws SQLException {
        this(dbPath, fullDurability, Clock.systemUTC());
    }

    private void init(boolean fullDurability) throws SQLException {
        try (Statement st = conn.createStatement()) {
            // Must precede table creation (fresh db) so DELETEs can reclaim pages.
            st.execute("PRAGMA auto_vacuum=INCREMENTAL");
        }
        for (String stmt : loadSchemaStatements()) {
            try (Statement st = conn.createStatement()) {
                st.execute(stmt);
            }
        }
        migrate();
        // ackDurability=full -> fsync per row (DESIGN §8.1); overrides the schema's NORMAL.
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA synchronous=" + (fullDurability ? "FULL" : "NORMAL"));
        }
    }

    /**
     * Additive schema migrations for buffers created by earlier versions. The durable
     * {@code buffer.db} survives upgrades, and {@code CREATE TABLE IF NOT EXISTS} is a
     * no-op on an existing table — so a column added to {@link #schema.sql} after the
     * buffer was first created must be back-filled with an explicit {@code ALTER}.
     * Idempotent: each step is guarded by a column-existence check, so a fresh db
     * (which already has the column from the schema) skips it.
     */
    private void migrate() throws SQLException {
        if (!columnExists("messages", "source_port")) {
            try (Statement st = conn.createStatement()) {
                // Nullable, no default — pre-migration rows keep source_port = NULL.
                st.execute("ALTER TABLE messages ADD COLUMN source_port TEXT");
            }
        }
    }

    private boolean columnExists(String table, String column) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static List<String> loadSchemaStatements() {
        final String raw = readResource();
        final StringBuilder clean = new StringBuilder();
        for (String line : raw.split("\n")) {
            final String t = line.strip();
            if (t.isEmpty() || t.startsWith("--")) {
                continue;
            }
            clean.append(line).append('\n');
        }
        final List<String> stmts = new ArrayList<>();
        for (String chunk : clean.toString().split(";")) {
            final String s = chunk.strip();
            if (!s.isEmpty()) {
                stmts.add(s);
            }
        }
        return stmts;
    }

    private static String readResource() {
        try (InputStream in = BufferStore.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing classpath resource: " + SCHEMA_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read " + SCHEMA_RESOURCE, e);
        }
    }

    /**
     * Persist a received message. Returns true if inserted, false if a row with
     * the same {@code controlId} already exists (HL7 re-sends are routine and are
     * silently dropped — DESIGN §4.2). The caller acks the sender only on a
     * committed insert (ack-on-persist).
     */
    public synchronized boolean insert(BufferRow row) throws SQLException {
        final String sql = "INSERT INTO messages (received_at, control_id, message_structure, "
            + "message_code, trigger_event, sending_app, sending_facility, source_port, hl7_version, "
            + "schema_id, raw_er7, mapped_json, status, lease_id, in_flight_until, acked_at) "
            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(control_id) DO NOTHING";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setLong(i++, row.receivedAt().toEpochMilli());
            ps.setString(i++, row.controlId());
            ps.setString(i++, row.messageStructure());
            ps.setString(i++, row.messageCode());
            ps.setString(i++, row.triggerEvent());
            ps.setString(i++, row.sendingApp());
            ps.setString(i++, row.sendingFacility());
            ps.setString(i++, row.sourcePort());
            ps.setString(i++, row.hl7Version());
            ps.setString(i++, row.schemaId());
            ps.setBytes(i++, row.rawEr7());
            ps.setString(i++, row.mappedJson());
            ps.setString(i++, row.status() == null ? MessageStatus.NEW.wire() : row.status().wire());
            ps.setString(i++, row.leaseId());
            setNullableInstant(ps, i++, row.inFlightUntil());
            setNullableInstant(ps, i, row.ackedAt());
            return ps.executeUpdate() > 0;
        }
    }

    public synchronized Lease take(String schemaId, int max, Duration leaseTtl) throws SQLException {
        return leases.take(schemaId, max, leaseTtl);
    }

    /**
     * Lease drainable rows narrowed by a pre-rendered WHERE fragment (the RFC4515
     * {@code take.filter}, DESIGN §2.5). Used by {@code ops/take}.
     */
    public synchronized Lease takeWhere(String whereClause, int max, Duration leaseTtl) throws SQLException {
        return leases.take(null, whereClause, max, leaseTtl);
    }

    /** Force in_flight rows back to new (DESIGN §2.5 {@code replay}); null = all. */
    public synchronized int replay(String whereClause) throws SQLException {
        return leases.replayInFlight(whereClause);
    }

    public synchronized int ack(String leaseId, List<String> controlIds) throws SQLException {
        return leases.ack(leaseId, controlIds);
    }

    public synchronized int release(String leaseId, List<String> controlIds) throws SQLException {
        return leases.release(leaseId, controlIds);
    }

    /** Revert expired in-flight leases to {@code new} (TTL revert, DESIGN §8.2). */
    public synchronized int reclaimExpiredLeases() throws SQLException {
        return leases.reclaimExpired();
    }

    public synchronized long count() throws SQLException {
        return queryLong("SELECT count(*) FROM messages");
    }

    public synchronized long count(MessageStatus status) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM messages WHERE status=?")) {
            ps.setString(1, status.wire());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    /**
     * Read-only browse over the buffer using a pre-rendered SQLite WHERE-clause
     * fragment (built by {@code Hl7SqlAdapter} from an RFC4515 filter, DESIGN §2.6).
     * Returns up to {@code limit} rows, newest first. A null/blank clause matches all.
     *
     * <p>The clause is interpolated, not bound — the adapter is the only producer and
     * it single-quote-escapes every literal — mirroring lite-filter's
     * {@code expression.as(...)} contract, which has no parameter seam.
     */
    public synchronized List<BufferRow> search(String whereClause, int limit) throws SQLException {
        return search(whereClause, limit, 0);
    }

    /** As {@link #search(String, int)} but with an OFFSET for page-number paging. */
    public synchronized List<BufferRow> search(String whereClause, int limit, int offset) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT ").append(COLS).append(" FROM messages");
        if (whereClause != null && !whereClause.isBlank()) {
            sql.append(" WHERE ").append(whereClause);
        }
        sql.append(" ORDER BY received_at DESC, id DESC LIMIT ? OFFSET ?");
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setInt(1, limit);
            ps.setInt(2, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                List<BufferRow> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapRow(rs));
                }
                return out;
            }
        }
    }

    /**
     * Rows eligible for re-materialization ({@code ops/recast}, DESIGN §2.5): every
     * row except {@code in_flight} (leased) ones, newest first, optionally narrowed
     * by a pre-rendered WHERE fragment (null/blank = all). Leased rows are excluded
     * so a recast never rewrites a message a consumer is mid-{@code take} on.
     */
    public synchronized List<BufferRow> recastable(String whereClause, int limit) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT ").append(COLS)
            .append(" FROM messages WHERE status <> 'in_flight'");
        if (whereClause != null && !whereClause.isBlank()) {
            sql.append(" AND (").append(whereClause).append(')');
        }
        sql.append(" ORDER BY received_at DESC, id DESC LIMIT ?");
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<BufferRow> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapRow(rs));
                }
                return out;
            }
        }
    }

    /**
     * Rewrite a row's materialized JSON + schema id ({@code ops/recast}). Guarded on
     * {@code status <> 'in_flight'} so a row leased between {@link #recastable} and
     * this update is not overwritten mid-flight (the select/update pair is not one
     * transaction). Returns true iff a row was updated.
     */
    public synchronized boolean updateMapping(long id, String schemaId, String mappedJson)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE messages SET schema_id=?, mapped_json=? WHERE id=? AND status <> 'in_flight'")) {
            ps.setString(1, schemaId);
            ps.setString(2, mappedJson);
            ps.setLong(3, id);
            return ps.executeUpdate() > 0;
        }
    }

    /** Row count matching a pre-rendered WHERE clause (null/blank = all). */
    public synchronized long countWhere(String whereClause) throws SQLException {
        String sql = "SELECT count(*) FROM messages"
            + (whereClause != null && !whereClause.isBlank() ? " WHERE " + whereClause : "");
        return queryLong(sql);
    }

    /**
     * Distinct non-null values of a column, used to enumerate dynamic object-tree
     * children (e.g. {@code /by-sender/<app>}). Only an allow-listed set of columns
     * is permitted — the name is interpolated, so it must never be caller-derived.
     */
    public synchronized List<String> distinctValues(String column) throws SQLException {
        return distinctValues(column, null);
    }

    /**
     * Distinct non-null values of a column within a pre-rendered scope (a WHERE
     * fragment; null/blank = all rows). Drives the nested object tree — e.g. the
     * versions present for one message structure ({@code /by-type/<MSG>/<v>}). The
     * column is allow-listed (interpolated, never caller-derived); the scope is a
     * caller-escaped fragment, same contract as {@link #countWhere}.
     */
    public synchronized List<String> distinctValues(String column, String whereClause) throws SQLException {
        if (!ALLOWED_DISTINCT_COLUMNS.contains(column)) {
            throw new IllegalArgumentException("distinctValues not allowed for column: " + column);
        }
        String where = column + " IS NOT NULL"
            + (whereClause != null && !whereClause.isBlank() ? " AND (" + whereClause + ")" : "");
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT " + column + " FROM messages WHERE "
                 + where + " ORDER BY " + column)) {
            List<String> out = new ArrayList<>();
            while (rs.next()) {
                out.add(rs.getString(1));
            }
            return out;
        }
    }

    private static final java.util.Set<String> ALLOWED_DISTINCT_COLUMNS =
        java.util.Set.of("sending_app", "sending_facility", "message_structure", "message_code",
            "source_port", "hl7_version");

    /** Delete acked rows acked longer ago than {@code olderThan} (ops/purge, DESIGN §2.5). */
    public synchronized int purge(Duration olderThan) throws SQLException {
        final long cutoff = nowMillis() - olderThan.toMillis();
        return deleteAckedOlderThanMillis(cutoff);
    }

    // --- primitives used by RetentionSweeper ---

    synchronized int deleteAckedOlderThanMillis(long cutoffMillis) throws SQLException {
        // Inclusive boundary (age >= olderThan): purge(PT0S) means "all acked",
        // which must include rows acked at the current instant (acked_at == cutoff).
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM messages WHERE status='acked' AND acked_at IS NOT NULL AND acked_at <= ?")) {
            ps.setLong(1, cutoffMillis);
            return ps.executeUpdate();
        }
    }

    synchronized int deleteOldestAcked(int limit) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM messages WHERE id IN (SELECT id FROM messages WHERE status='acked' "
                + "ORDER BY received_at ASC LIMIT ?)")) {
            ps.setInt(1, limit);
            return ps.executeUpdate();
        }
    }

    synchronized long dbSizeBytes() throws SQLException {
        return queryLong("PRAGMA page_count") * queryLong("PRAGMA page_size");
    }

    synchronized void incrementalVacuum() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA incremental_vacuum");
        }
    }

    // --- health metrics (DESIGN §9) ---------------------------------------

    /** Size of the WAL sidecar file in bytes, or 0 if it doesn't exist (e.g. fresh db). */
    public synchronized long walBytes() {
        try {
            java.nio.file.Path wal = java.nio.file.Path.of(dbPath + "-wal");
            return java.nio.file.Files.exists(wal) ? java.nio.file.Files.size(wal) : 0L;
        } catch (java.io.IOException e) {
            return 0L;
        }
    }

    /** Epoch-millis of the most recently received message, or empty if the buffer is empty. */
    public synchronized java.util.OptionalLong lastReceivedMillis() throws SQLException {
        return queryNullableLong("SELECT max(received_at) FROM messages");
    }

    /** Age in seconds of the oldest not-yet-acked message, or empty if none are pending. */
    public synchronized java.util.OptionalLong oldestUnackedSeconds() throws SQLException {
        java.util.OptionalLong oldest =
            queryNullableLong("SELECT min(received_at) FROM messages WHERE status != 'acked'");
        if (oldest.isEmpty()) {
            return java.util.OptionalLong.empty();
        }
        return java.util.OptionalLong.of(Math.max(0, (nowMillis() - oldest.getAsLong()) / 1000));
    }

    Clock clock() {
        return clock;
    }

    @Override
    public synchronized void close() throws SQLException {
        conn.close();
    }

    private long nowMillis() {
        return Instant.now(clock).toEpochMilli();
    }

    private long queryLong(String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    /** Like {@link #queryLong} but distinguishes SQL NULL (e.g. min/max over no rows) from 0. */
    private java.util.OptionalLong queryNullableLong(String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                long v = rs.getLong(1);
                if (!rs.wasNull()) {
                    return java.util.OptionalLong.of(v);
                }
            }
            return java.util.OptionalLong.empty();
        }
    }

    static void setNullableInstant(PreparedStatement ps, int idx, Instant value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setLong(idx, value.toEpochMilli());
        }
    }

    /** Map a result row (column order per {@link #COLS}) to a {@link BufferRow}. */
    static BufferRow mapRow(ResultSet rs) throws SQLException {
        return new BufferRow(
            rs.getLong("id"),
            instant(rs, "received_at"),
            rs.getString("control_id"),
            rs.getString("message_structure"),
            rs.getString("message_code"),
            rs.getString("trigger_event"),
            rs.getString("sending_app"),
            rs.getString("sending_facility"),
            rs.getString("hl7_version"),
            rs.getString("schema_id"),
            rs.getBytes("raw_er7"),
            rs.getString("mapped_json"),
            MessageStatus.fromWire(rs.getString("status")),
            rs.getString("lease_id"),
            instant(rs, "in_flight_until"),
            instant(rs, "acked_at"),
            rs.getString("source_port"));
    }

    private static Instant instant(ResultSet rs, String col) throws SQLException {
        final long v = rs.getLong(col);
        return rs.wasNull() ? null : Instant.ofEpochMilli(v);
    }
}

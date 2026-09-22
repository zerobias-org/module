package com.zerobias.module.x12.buffer;

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
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * The durable buffer (DESIGN §8): a single SQLite file in WAL mode holding the
 * {@code files} (audit trail, one per interchange file) and {@code transactions}
 * (drain atoms, one per ST..SE) tables.
 *
 * <p>Owns one JDBC connection; all methods are {@code synchronized}, which makes
 * the lease/drain operations race-free by construction (single writer). The
 * consume path is one SQL transaction per file ({@link #consumeFile}) — the caller
 * renames the file {@code .done} only after that commit returns (rename is the ack).
 *
 * <p>Timestamps are stored as epoch-millis INTEGERs, not ISO text — integer
 * comparison is correct for ordering/expiry, whereas {@code Instant.toString()}
 * varies in fractional precision and would mis-sort lexicographically.
 *
 * <p>Lease semantics (take/ack/release/reclaim) live in {@link LeaseManager};
 * retention policy lives in {@link RetentionSweeper}. This class owns the
 * connection, schema, inserts, lookups, counts, and the deletion primitives.
 */
public final class BufferStore implements AutoCloseable {

    static final String TX_COLS = "id, element_key, file_id, source_name, received_at, isa_control, "
        + "gs_control, st_control, gs08, transaction_type, sender_id, receiver_id, interchange_at, "
        + "schema_id, raw_x12, mapped_json, parser_error_count, envelope, status, lease_id, "
        + "in_flight_until, acked_at";

    static final String FILE_COLS = "id, file_id, file_path, file_name, source_name, current_path, size_bytes, "
        + "checksum, file_mtime, discovered_at, consumed_at, status, isa_count, transaction_count, "
        + "error_message, rename_failed, redelivery_count";

    /**
     * Columns {@link #distinctValues} may enumerate (interpolated, never caller-derived).
     * These drive the emergent object tree: {@code /by-type}, {@code /by-version},
     * {@code /by-sender}, {@code /by-source}, {@code /files}.
     */
    private static final Set<String> ALLOWED_DISTINCT_COLUMNS = Set.of(
        "transaction_type", "gs08", "sender_id", "receiver_id", "source_name", "file_id",
        "schema_id", "envelope");

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
        // ackDurability=full -> fsync per commit (DESIGN §8); overrides the schema's NORMAL.
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA synchronous=" + (fullDurability ? "FULL" : "NORMAL"));
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
            // strip trailing "-- comment" so a ';' in a comment can't split a statement
            int c = line.indexOf("--");
            clean.append(c >= 0 ? line.substring(0, c) : line).append('\n');
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

    // --- consume path (DESIGN §4.2) ------------------------------------------

    /**
     * Persist one interchange file and all of its transaction sets in ONE SQL
     * transaction (DESIGN §4.2 step 3c): every row is inserted with
     * {@code ON CONFLICT(element_key) DO NOTHING}, then the {@code files} row. On any
     * failure the whole unit rolls back and the exception propagates (the caller then
     * renames {@code .error}). Returns the number of transaction rows actually
     * inserted (duplicates by element key are silently dropped). The caller renames
     * {@code .done} only after this returns — rename is the ack.
     */
    public synchronized int consumeFile(FileRow file, List<TransactionRow> rows) throws SQLException {
        final boolean prev = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            int inserted = 0;
            for (TransactionRow r : rows) {
                if (insertTransactionUnsynchronized(r)) {
                    inserted++;
                }
            }
            insertFileUnsynchronized(file);
            conn.commit();
            return inserted;
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(prev);
        }
    }

    /**
     * Insert a {@code files} row on its own (the {@code error} and {@code duplicate}
     * paths, DESIGN §4.2 a/e). {@code file_id} ({@code <path>@<hash>}) is UNIQUE: the
     * same bytes re-landing at the same path is a redelivery ({@link #bumpRedelivery}),
     * never a second row — the consumer resolves that before inserting.
     */
    public synchronized void insertFile(FileRow file) throws SQLException {
        insertFileUnsynchronized(file);
    }

    /**
     * Persist one transaction set. Returns true if inserted, false if a row with the
     * same {@code elementKey} already exists (silently dropped).
     */
    public synchronized boolean insertTransaction(TransactionRow row) throws SQLException {
        return insertTransactionUnsynchronized(row);
    }

    private boolean insertTransactionUnsynchronized(TransactionRow row) throws SQLException {
        final String sql = "INSERT INTO transactions (element_key, file_id, source_name, received_at, "
            + "isa_control, gs_control, st_control, gs08, transaction_type, sender_id, receiver_id, "
            + "interchange_at, schema_id, raw_x12, mapped_json, parser_error_count, envelope, status, "
            + "lease_id, in_flight_until, acked_at) "
            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(element_key) DO NOTHING";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, row.elementKey());
            ps.setString(i++, row.fileId());
            ps.setString(i++, row.sourceName());
            ps.setLong(i++, row.receivedAt().toEpochMilli());
            ps.setString(i++, row.isaControl());
            ps.setString(i++, row.gsControl());
            ps.setString(i++, row.stControl());
            ps.setString(i++, row.gs08());
            ps.setString(i++, row.transactionType());
            ps.setString(i++, row.senderId());
            ps.setString(i++, row.receiverId());
            setNullableInstant(ps, i++, row.interchangeAt());
            ps.setString(i++, row.schemaId());
            ps.setBytes(i++, row.rawX12());
            ps.setString(i++, row.mappedJson());
            ps.setInt(i++, row.parserErrorCount());
            ps.setString(i++, row.envelope() == null ? TransactionRow.ENVELOPE_FILE : row.envelope());
            ps.setString(i++, row.status() == null ? Status.NEW.wire() : row.status().wire());
            ps.setString(i++, row.leaseId());
            setNullableInstant(ps, i++, row.inFlightUntil());
            setNullableInstant(ps, i, row.ackedAt());
            return ps.executeUpdate() > 0;
        }
    }

    private void insertFileUnsynchronized(FileRow f) throws SQLException {
        final String sql = "INSERT INTO files (file_id, file_path, file_name, source_name, current_path, size_bytes, "
            + "checksum, file_mtime, discovered_at, consumed_at, status, isa_count, transaction_count, "
            + "error_message, rename_failed, redelivery_count) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, f.fileId());
            ps.setString(i++, f.filePath() == null ? FileRow.pathOf(f.fileId()) : f.filePath());
            ps.setString(i++, f.fileName());
            ps.setString(i++, f.sourceName());
            ps.setString(i++, f.currentPath());
            ps.setLong(i++, f.sizeBytes());
            ps.setString(i++, f.checksum());
            ps.setLong(i++, f.fileMtime() == null ? 0L : f.fileMtime().toEpochMilli());
            ps.setLong(i++, (f.discoveredAt() == null ? Instant.now(clock) : f.discoveredAt()).toEpochMilli());
            setNullableInstant(ps, i++, f.consumedAt());
            ps.setString(i++, f.status().wire());
            setNullableInt(ps, i++, f.isaCount());
            setNullableInt(ps, i++, f.transactionCount());
            ps.setString(i++, f.errorMessage());
            ps.setInt(i++, f.renameFailed() ? 1 : 0);
            ps.setInt(i, f.redeliveryCount());
            ps.executeUpdate();
        }
    }

    /**
     * The file row whose checksum matches, if any (duplicate detection, DESIGN §4.2 step
     * 3a). When several rows share the bytes the {@code consumed} one wins, then a
     * {@code duplicate}, then the newest {@code error} — so the caller sees "these bytes
     * were ingested" whenever they were, and only ever sees an error row when every
     * delivery of these bytes failed.
     */
    public synchronized Optional<FileRow> findFileByChecksum(String checksum) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT " + FILE_COLS + " FROM files WHERE checksum=? ORDER BY "
                + "CASE status WHEN 'consumed' THEN 0 WHEN 'duplicate' THEN 1 ELSE 2 END ASC, id DESC LIMIT 1")) {
            ps.setString(1, checksum);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapFile(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Delete one {@code files} row (DESIGN §4.2 step 3a, the retry of an {@code error}
     * row whose bytes re-landed at the same path). Only {@code files} is touched: an error
     * row never has transactions (they were rolled back). Returns true iff a row matched.
     */
    public synchronized boolean deleteFile(String fileId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM files WHERE file_id=?")) {
            ps.setString(1, fileId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Record that the same bytes re-landed at the same path (DESIGN §4.2 step 3a): the
     * row's {@code status} stays, {@code redelivery_count} is incremented and, when
     * {@code currentPath} is non-null, {@code current_path} moves to where the newest copy
     * was renamed. Returns true iff a row matched.
     */
    public synchronized boolean bumpRedelivery(String fileId, String currentPath) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE files SET redelivery_count = redelivery_count + 1, "
                + "current_path = COALESCE(?, current_path) WHERE file_id=?")) {
            ps.setString(1, currentPath);
            ps.setString(2, fileId);
            return ps.executeUpdate() > 0;
        }
    }

    /** The file row for a {@code fileId} ({@code <path>@<hash>}), if known. */
    public synchronized Optional<FileRow> fileById(String fileId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT " + FILE_COLS + " FROM files WHERE file_id=?")) {
            ps.setString(1, fileId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapFile(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Record that the post-commit rename failed (DESIGN §4.2 step 3d): {@code rename_failed=1}
     * and {@code current_path} reset to the discovery path (the bytes are still there).
     * The id ({@code <path>@<hash>}) keeps the next scan from re-consuming it: it re-hashes
     * the file, finds this row and treats it as a redelivery. Returns true iff a row matched.
     */
    public synchronized boolean markRenameFailed(String fileId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE files SET rename_failed=1, current_path=file_path WHERE file_id=?")) {
            ps.setString(1, fileId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Update where a file's bytes live now (e.g. an operator moved it). Returns true iff a row matched. */
    public synchronized boolean updateFilePath(String fileId, String currentPath) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE files SET current_path=? WHERE file_id=?")) {
            ps.setString(1, currentPath);
            ps.setString(2, fileId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Browse {@code files} rows, newest discovery first, narrowed by a pre-rendered WHERE
     * fragment over the files table (null/blank = all). Drives {@code /files} children.
     */
    public synchronized List<FileRow> fileRows(String whereClause, int limit, int offset) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT ").append(FILE_COLS).append(" FROM files");
        if (whereClause != null && !whereClause.isBlank()) {
            sql.append(" WHERE ").append(whereClause);
        }
        sql.append(" ORDER BY discovered_at DESC, id DESC LIMIT ? OFFSET ?");
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setInt(1, limit);
            ps.setInt(2, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                List<FileRow> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapFile(rs));
                }
                return out;
            }
        }
    }

    public synchronized long fileCount() throws SQLException {
        return queryLong("SELECT count(*) FROM files");
    }

    public synchronized long fileCount(FileStatus status) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM files WHERE status=?")) {
            ps.setString(1, status.wire());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    /** Files of one status within one source (health {@code sources[].errored}, DESIGN §9). */
    public synchronized long fileCount(String sourceName, FileStatus status) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT count(*) FROM files WHERE source_name=? AND status=?")) {
            ps.setString(1, sourceName);
            ps.setString(2, status.wire());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    /** Files row count matching a pre-rendered WHERE clause over {@code files} (null/blank = all). */
    public synchronized long countFilesWhere(String whereClause) throws SQLException {
        return queryLong("SELECT count(*) FROM files"
            + (whereClause != null && !whereClause.isBlank() ? " WHERE " + whereClause : ""));
    }

    // --- drain / lease (DESIGN §2.5) -----------------------------------------

    public synchronized Lease take(String schemaId, int max, Duration leaseTtl) throws SQLException {
        return leases.take(schemaId, max, leaseTtl);
    }

    /**
     * Lease drainable rows narrowed by a pre-rendered WHERE fragment (the RFC4515
     * {@code take.filter}). Used by {@code ops/take}.
     */
    public synchronized Lease takeWhere(String whereClause, int max, Duration leaseTtl) throws SQLException {
        return leases.take(null, whereClause, max, leaseTtl);
    }

    /** Force in_flight rows back to new ({@code ops/replay}); null = all. */
    public synchronized int replayInFlight(String whereClause) throws SQLException {
        return leases.replayInFlight(whereClause);
    }

    /** Finalize a lease; {@code elementKeys} null/empty = the whole lease (partial ack otherwise). */
    public synchronized int ack(String leaseId, List<String> elementKeys) throws SQLException {
        return leases.ack(leaseId, elementKeys);
    }

    public synchronized int release(String leaseId, List<String> elementKeys) throws SQLException {
        return leases.release(leaseId, elementKeys);
    }

    /** Revert expired in-flight leases to {@code new} (TTL revert). */
    public synchronized int reclaimExpired() throws SQLException {
        return leases.reclaimExpired();
    }

    // --- browse ----------------------------------------------------------------

    public synchronized long count() throws SQLException {
        return queryLong("SELECT count(*) FROM transactions");
    }

    public synchronized long count(Status status) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM transactions WHERE status=?")) {
            ps.setString(1, status.wire());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    /**
     * Read-only browse over transactions using a pre-rendered SQLite WHERE-clause
     * fragment (built by {@code X12SqlAdapter} from an RFC4515 filter, DESIGN §2.6).
     * Returns up to {@code limit} rows, newest first. A null/blank clause matches all.
     *
     * <p>The clause is interpolated, not bound — the adapter is the only producer and
     * it single-quote-escapes every literal — mirroring lite-filter's
     * {@code expression.as(...)} contract, which has no parameter seam.
     */
    public synchronized List<TransactionRow> search(String whereClause, int limit) throws SQLException {
        return search(whereClause, limit, 0);
    }

    /** As {@link #search(String, int)} but with an OFFSET for page-number paging. */
    public synchronized List<TransactionRow> search(String whereClause, int limit, int offset)
            throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT ").append(TX_COLS).append(" FROM transactions");
        if (whereClause != null && !whereClause.isBlank()) {
            sql.append(" WHERE ").append(whereClause);
        }
        sql.append(" ORDER BY received_at DESC, id DESC LIMIT ? OFFSET ?");
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setInt(1, limit);
            ps.setInt(2, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                List<TransactionRow> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapTransaction(rs));
                }
                return out;
            }
        }
    }

    /** The single row for an element key, if buffered ({@code ops/raw}, {@code ops/validate}). */
    public synchronized Optional<TransactionRow> byElementKey(String elementKey) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT " + TX_COLS + " FROM transactions WHERE element_key=?")) {
            ps.setString(1, elementKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapTransaction(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Rows eligible for re-materialization ({@code ops/recast}): every row except
     * {@code in_flight} (leased) ones, newest first, optionally narrowed by a
     * pre-rendered WHERE fragment (null/blank = all).
     */
    public synchronized List<TransactionRow> recastable(String whereClause, int limit) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT ").append(TX_COLS)
            .append(" FROM transactions WHERE status <> 'in_flight'");
        if (whereClause != null && !whereClause.isBlank()) {
            sql.append(" AND (").append(whereClause).append(')');
        }
        sql.append(" ORDER BY received_at DESC, id DESC LIMIT ?");
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<TransactionRow> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapTransaction(rs));
                }
                return out;
            }
        }
    }

    /**
     * Rewrite a row's materialized JSON + schema id ({@code ops/recast}). Guarded on
     * {@code status <> 'in_flight'} so a row leased between {@link #recastable} and
     * this update is not overwritten mid-flight. Returns true iff a row was updated.
     */
    public synchronized boolean updateMapping(long id, String schemaId, String mappedJson)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE transactions SET schema_id=?, mapped_json=? WHERE id=? AND status <> 'in_flight'")) {
            ps.setString(1, schemaId);
            ps.setString(2, mappedJson);
            ps.setLong(3, id);
            return ps.executeUpdate() > 0;
        }
    }

    /** Transaction row count matching a pre-rendered WHERE clause (null/blank = all). */
    public synchronized long countWhere(String whereClause) throws SQLException {
        return queryLong("SELECT count(*) FROM transactions"
            + (whereClause != null && !whereClause.isBlank() ? " WHERE " + whereClause : ""));
    }

    /** Distinct non-null values of an allow-listed transactions column (emergent tree children). */
    public synchronized List<String> distinctValues(String column) throws SQLException {
        return distinctValues(column, null);
    }

    /**
     * Distinct non-null values of an allow-listed column within a pre-rendered scope (a
     * WHERE fragment; null/blank = all rows) — e.g. the GS08 versions present for one
     * transaction type ({@code /by-type/<TS>/<GS08>}). The column is interpolated, so it
     * must never be caller-derived; the scope is a caller-escaped fragment.
     */
    public synchronized List<String> distinctValues(String column, String whereClause) throws SQLException {
        if (!ALLOWED_DISTINCT_COLUMNS.contains(column)) {
            throw new IllegalArgumentException("distinctValues not allowed for column: " + column);
        }
        String where = column + " IS NOT NULL"
            + (whereClause != null && !whereClause.isBlank() ? " AND (" + whereClause + ")" : "");
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT " + column + " FROM transactions WHERE "
                 + where + " ORDER BY " + column)) {
            List<String> out = new ArrayList<>();
            while (rs.next()) {
                out.add(rs.getString(1));
            }
            return out;
        }
    }

    /** Delete acked rows acked longer ago than {@code olderThan} ({@code ops/purge}). */
    public synchronized int purge(Duration olderThan) throws SQLException {
        final long cutoff = nowMillis() - olderThan.toMillis();
        return deleteAckedOlderThanMillis(cutoff);
    }

    // --- primitives used by RetentionSweeper ---

    synchronized int deleteAckedOlderThanMillis(long cutoffMillis) throws SQLException {
        // Inclusive boundary (age >= olderThan): purge(PT0S) means "all acked",
        // which must include rows acked at the current instant (acked_at == cutoff).
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM transactions WHERE status='acked' AND acked_at IS NOT NULL AND acked_at <= ?")) {
            ps.setLong(1, cutoffMillis);
            return ps.executeUpdate();
        }
    }

    synchronized int deleteOldestAcked(int limit) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM transactions WHERE id IN (SELECT id FROM transactions WHERE status='acked' "
                + "ORDER BY received_at ASC LIMIT ?)")) {
            ps.setInt(1, limit);
            return ps.executeUpdate();
        }
    }

    /** Current database size in bytes (page_count × page_size); {@code /stats} + backpressure. */
    public synchronized long dbSizeBytes() throws SQLException {
        return queryLong("PRAGMA page_count") * queryLong("PRAGMA page_size");
    }

    synchronized void incrementalVacuum() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA incremental_vacuum");
        }
    }

    // --- health / stats metrics (DESIGN §9) ---------------------------------

    /** Size of the WAL sidecar file in bytes, or 0 if it doesn't exist (e.g. fresh db). */
    public synchronized long walBytes() {
        try {
            java.nio.file.Path wal = java.nio.file.Path.of(dbPath + "-wal");
            return java.nio.file.Files.exists(wal) ? java.nio.file.Files.size(wal) : 0L;
        } catch (IOException e) {
            return 0L;
        }
    }

    /** Epoch-millis of the most recently received transaction, or empty if the buffer is empty. */
    public synchronized OptionalLong lastReceivedMillis() throws SQLException {
        return queryNullableLong("SELECT max(received_at) FROM transactions");
    }

    /** Epoch-millis of the most recent file consumption, or empty if none yet. */
    public synchronized OptionalLong lastConsumedMillis() throws SQLException {
        return queryNullableLong("SELECT max(consumed_at) FROM files WHERE status='consumed'");
    }

    /** Age in seconds of the oldest not-yet-acked transaction, or empty if none are pending. */
    public synchronized OptionalLong oldestUnackedSeconds() throws SQLException {
        OptionalLong oldest =
            queryNullableLong("SELECT min(received_at) FROM transactions WHERE status != 'acked'");
        if (oldest.isEmpty()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Math.max(0, (nowMillis() - oldest.getAsLong()) / 1000));
    }

    public Clock clock() {
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
    private OptionalLong queryNullableLong(String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                long v = rs.getLong(1);
                if (!rs.wasNull()) {
                    return OptionalLong.of(v);
                }
            }
            return OptionalLong.empty();
        }
    }

    static void setNullableInstant(PreparedStatement ps, int idx, Instant value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setLong(idx, value.toEpochMilli());
        }
    }

    private static void setNullableInt(PreparedStatement ps, int idx, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setInt(idx, value);
        }
    }

    /** Map a result row (columns per {@link #TX_COLS}) to a {@link TransactionRow}. */
    static TransactionRow mapTransaction(ResultSet rs) throws SQLException {
        return new TransactionRow(
            rs.getLong("id"),
            rs.getString("element_key"),
            rs.getString("file_id"),
            rs.getString("source_name"),
            instant(rs, "received_at"),
            rs.getString("isa_control"),
            rs.getString("gs_control"),
            rs.getString("st_control"),
            rs.getString("gs08"),
            rs.getString("transaction_type"),
            rs.getString("sender_id"),
            rs.getString("receiver_id"),
            instant(rs, "interchange_at"),
            rs.getString("schema_id"),
            rs.getBytes("raw_x12"),
            rs.getString("mapped_json"),
            rs.getInt("parser_error_count"),
            rs.getString("envelope"),
            Status.fromWire(rs.getString("status")),
            rs.getString("lease_id"),
            instant(rs, "in_flight_until"),
            instant(rs, "acked_at"));
    }

    /** Map a result row (columns per {@link #FILE_COLS}) to a {@link FileRow}. */
    static FileRow mapFile(ResultSet rs) throws SQLException {
        return new FileRow(
            rs.getLong("id"),
            rs.getString("file_id"),
            rs.getString("file_path"),
            rs.getString("file_name"),
            rs.getString("source_name"),
            rs.getString("current_path"),
            rs.getLong("size_bytes"),
            rs.getString("checksum"),
            instant(rs, "file_mtime"),
            instant(rs, "discovered_at"),
            instant(rs, "consumed_at"),
            FileStatus.fromWire(rs.getString("status")),
            nullableInt(rs, "isa_count"),
            nullableInt(rs, "transaction_count"),
            rs.getString("error_message"),
            rs.getInt("rename_failed") != 0,
            rs.getInt("redelivery_count"));
    }

    private static Instant instant(ResultSet rs, String col) throws SQLException {
        final long v = rs.getLong(col);
        return rs.wasNull() ? null : Instant.ofEpochMilli(v);
    }

    private static Integer nullableInt(ResultSet rs, String col) throws SQLException {
        final int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }
}

package com.zerobias.module.x12.buffer;

import com.zerobias.module.x12.materializer.EntityGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private static final Logger LOG = LoggerFactory.getLogger(BufferStore.class);

    static final String TX_COLS = "id, element_key, file_id, source_name, received_at, isa_control, "
        + "gs_control, st_control, gs08, transaction_type, sender_id, receiver_id, interchange_at, "
        + "schema_id, raw_x12, parser_error_count, envelope, status, lease_id, "
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

    /** {@code PRAGMA user_version} of the shape {@code schema.sql} creates; see {@link #migrate}. */
    static final int SCHEMA_VERSION = 1;

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
        // ackDurability=full -> fsync per commit (DESIGN §8); overrides the schema's NORMAL.
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA synchronous=" + (fullDurability ? "FULL" : "NORMAL"));
        }
    }

    /**
     * Bring an existing buffer up to {@link #SCHEMA_VERSION}. The buffer volume outlives every
     * redeploy by design, and {@code CREATE TABLE IF NOT EXISTS} never alters a table that is
     * already there — so every column change to {@code schema.sql} needs a step here, or an
     * upgraded container opens a table its INSERTs no longer match.
     *
     * <p>Steps probe the actual shape rather than trusting {@code user_version} alone: buffers
     * written before versioning carry 0 whatever their shape.
     */
    private void migrate() throws SQLException {
        final boolean prev = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (Statement st = conn.createStatement()) {
            // v1: the typed document moved into the object graph (DESIGN §8.4). The legacy
            // column is NOT NULL, so while it exists every ingest INSERT fails. Its content is
            // not carried over: raw_x12 is the source, and the startup backfill rebuilds the
            // graph from it (BufferStore#graphless).
            if (hasColumn("transactions", "mapped_json")) {
                st.execute("ALTER TABLE transactions DROP COLUMN mapped_json");
                LOG.info("buffer migration: dropped transactions.mapped_json (graph replaces it)");
            }
            if (queryLong("PRAGMA user_version") < SCHEMA_VERSION) {
                st.execute("PRAGMA user_version = " + SCHEMA_VERSION);
            }
            conn.commit();
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(prev);
        }
    }

    private boolean hasColumn(String table, String column) throws SQLException {
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
     * transaction (DESIGN §4.2 step 3c): the transaction rows, then the {@code files} row.
     * Every row must land — see {@link #consumeFile(FileRow, List, Map, Map)}. The caller
     * renames {@code .done} only after this returns — rename is the ack.
     */
    public synchronized int consumeFile(FileRow file, List<TransactionRow> rows) throws SQLException {
        return consumeFile(file, rows, Map.of());
    }

    /**
     * Consume a file, its transaction sets and their object graphs in ONE transaction
     * (DESIGN §8.4). The graph is not an afterthought: a committed transaction row whose
     * entities are missing would be queryable-but-empty, so both land or neither does —
     * and the {@code .done} rename still happens only after this returns.
     *
     * @param graphs element key → flattened instances ({@code EntityGraph.flatten} output)
     */
    public synchronized int consumeFile(FileRow file, List<TransactionRow> rows,
            Map<String, List<EntityGraph.Entity>> graphs) throws SQLException {
        return consumeFile(file, rows, graphs, Map.of());
    }

    /**
     * The whole consume unit. Every transaction row must land: the file's id is new (the
     * consumer resolves redelivery/duplicates by checksum before calling this), so an element
     * key that is already taken means two transaction sets of THIS file collide, and the unit
     * is rolled back with a {@link DuplicateElementKeyException} rather than committed with a
     * set missing — or, worse, with the surviving row carrying the other set's graph, since
     * both would be filed under the same key in {@code graphs}. On any failure, {@link Error}s
     * included, nothing is written and the exception propagates. Returns {@code rows.size()}.
     *
     * @param dims element key → resolved business dimensions (DESIGN §8.5)
     */
    public synchronized int consumeFile(FileRow file, List<TransactionRow> rows,
            Map<String, List<EntityGraph.Entity>> graphs,
            Map<String, Map<String, EntityGraph.Value>> dims) throws SQLException {
        return SqlTransaction.run(conn, () -> {
            for (TransactionRow r : rows) {
                if (!insertTransactionUnsynchronized(r)) {
                    throw new DuplicateElementKeyException(r.elementKey());
                }
                List<EntityGraph.Entity> graph = graphs == null ? null : graphs.get(r.elementKey());
                if (graph != null && !graph.isEmpty()) {
                    insertGraphUnsynchronized(r, graph);
                }
                Map<String, EntityGraph.Value> d = dims == null ? null : dims.get(r.elementKey());
                if (d != null && !d.isEmpty()) {
                    insertDimsUnsynchronized(r.elementKey(), d);
                }
            }
            insertFileUnsynchronized(file);
            return rows.size();
        });
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
     * same {@code elementKey} already exists. The insert keeps {@code ON CONFLICT DO NOTHING}
     * so a taken key reads as {@code false} rather than as a constraint error that would look
     * like a store failure; {@link #consumeFile} turns that {@code false} into a rollback.
     */
    public synchronized boolean insertTransaction(TransactionRow row) throws SQLException {
        return insertTransactionUnsynchronized(row);
    }

    /** Insert a transaction row together with its object graph, in one transaction. */
    public synchronized boolean insertTransaction(TransactionRow row, List<EntityGraph.Entity> graph)
            throws SQLException {
        final boolean prev = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            final boolean inserted = insertTransactionUnsynchronized(row);
            if (inserted && graph != null && !graph.isEmpty()) {
                insertGraphUnsynchronized(row, graph);
            }
            conn.commit();
            return inserted;
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(prev);
        }
    }

    private boolean insertTransactionUnsynchronized(TransactionRow row) throws SQLException {
        final String sql = "INSERT INTO transactions (element_key, file_id, source_name, received_at, "
            + "isa_control, gs_control, st_control, gs08, transaction_type, sender_id, receiver_id, "
            + "interchange_at, schema_id, raw_x12, parser_error_count, envelope, status, "
            + "lease_id, in_flight_until, acked_at) "
            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(element_key) DO NOTHING";
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
        return search(whereClause, limit, offset, null);
    }

    /**
     * @param orderBy a validated ORDER BY fragment (see {@code X12Filter.orderBy}), or null for
     *     the default newest-first order. Never accept a caller's raw string here.
     */
    public synchronized List<TransactionRow> search(String whereClause, int limit, int offset,
            String orderBy) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT ").append(TX_COLS).append(" FROM transactions");
        if (whereClause != null && !whereClause.isBlank()) {
            sql.append(" WHERE ").append(whereClause);
        }
        if (orderBy != null && !orderBy.isBlank()) {
            // the requested sort first, then the default order as a stable tiebreak
            sql.append(" ORDER BY ").append(orderBy).append(", received_at DESC, id DESC");
        } else {
            sql.append(" ORDER BY received_at DESC, id DESC");
        }
        sql.append(" LIMIT ? OFFSET ?");
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
    public synchronized boolean updateSchemaId(long id, String schemaId)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE transactions SET schema_id=? WHERE id=? AND status <> 'in_flight'")) {
            ps.setString(1, schemaId);
            ps.setLong(2, id);
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
        final List<String> keys = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT element_key FROM transactions WHERE status='acked' AND acked_at IS NOT NULL "
                + "AND acked_at <= ?")) {
            ps.setLong(1, cutoffMillis);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    keys.add(rs.getString(1));
                }
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM transactions WHERE status='acked' AND acked_at IS NOT NULL AND acked_at <= ?")) {
            ps.setLong(1, cutoffMillis);
            final int purged = ps.executeUpdate();
            deleteGraphs(keys);   // the graph goes with its transaction, never outlives it
            return purged;
        }
    }

    synchronized int deleteOldestAcked(int limit) throws SQLException {
        final List<String> keys = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT element_key FROM transactions WHERE status='acked' ORDER BY received_at ASC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    keys.add(rs.getString(1));
                }
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM transactions WHERE id IN (SELECT id FROM transactions WHERE status='acked' "
                + "ORDER BY received_at ASC LIMIT ?)")) {
            ps.setInt(1, limit);
            final int purged = ps.executeUpdate();
            deleteGraphs(keys);
            return purged;
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
    // --- object graph (DESIGN §8.4) -----------------------------------------

    private static final String ENTITY_COLS =
        "id, element_key, file_id, gs08, schema_id, xid, kind, parent_id, property, path, ordinal, "
        + "property_order";
    /** {@code property_order} joiner: a unit separator can never occur in an X12 property name. */
    private static final String ORDER_SEP = "\u001f";

    /**
     * Insert one transaction set's instances and their typed values. Local ids from
     * {@link EntityGraph} are remapped onto generated row ids as we go, so parent edges
     * survive; a child always follows its parent in flatten order, which is why one pass is
     * enough.
     */
    private void insertGraphUnsynchronized(TransactionRow tx, List<EntityGraph.Entity> graph)
            throws SQLException {
        final Map<Integer, Long> ids = new HashMap<>();
        try (PreparedStatement ent = conn.prepareStatement(
                "INSERT INTO entities (element_key, file_id, gs08, schema_id, xid, kind, parent_id, "
                + "property, path, ordinal, property_order) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS);
             PreparedStatement val = conn.prepareStatement(
                "INSERT INTO entity_values (entity_id, seq, property, data_type, value_text, value_num, "
                + "value_date) VALUES (?,?,?,?,?,?,?)")) {
            for (EntityGraph.Entity e : graph) {
                ent.setString(1, tx.elementKey());
                ent.setString(2, tx.fileId());
                ent.setString(3, tx.gs08());
                ent.setString(4, e.schemaId);
                ent.setString(5, e.xid);
                ent.setString(6, e.kind);
                if (e.parentLocalId == null) {
                    ent.setNull(7, Types.BIGINT);
                } else {
                    final Long parent = ids.get(e.parentLocalId);
                    if (parent == null) {
                        throw new SQLException("graph out of order: " + e.path + " precedes its parent");
                    }
                    ent.setLong(7, parent);
                }
                ent.setString(8, e.property);
                ent.setString(9, e.path);
                ent.setInt(10, e.ordinal);
                ent.setString(11, String.join(ORDER_SEP, e.propertyOrder));
                ent.executeUpdate();
                final long id;
                try (ResultSet keys = ent.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("no generated id for entity " + e.path);
                    }
                    id = keys.getLong(1);
                }
                ids.put(e.localId, id);
                int seq = 0;
                for (EntityGraph.Value v : e.values) {
                    val.setLong(1, id);
                    val.setInt(2, seq++);
                    val.setString(3, v.property());
                    val.setString(4, v.dataType());
                    val.setString(5, v.text());
                    final Long micros = microUnits(v.num());
                    if (micros == null) {
                        val.setNull(6, Types.INTEGER);
                    } else {
                        val.setLong(6, micros);
                    }
                    if (v.date() == null) {
                        val.setNull(7, Types.INTEGER);
                    } else {
                        val.setLong(7, v.date());
                    }
                    val.addBatch();
                }
                val.executeBatch();
            }
        }
    }

    /** One transaction set's instances, parents before children, with their values attached. */
    public synchronized List<EntityGraph.Entity> graphFor(String elementKey) throws SQLException {
        final Map<Long, EntityGraph.Entity> byId = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT " + ENTITY_COLS + " FROM entities WHERE element_key=? ORDER BY id")) {
            ps.setString(1, elementKey);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    final long id = rs.getLong("id");
                    final long parent = rs.getLong("parent_id");
                    final EntityGraph.Entity e = EntityGraph.Entity.of((int) id,
                        rs.wasNull() ? null : (int) parent,
                        rs.getString("schema_id"), rs.getString("xid"), rs.getString("kind"),
                        rs.getString("property"), rs.getString("path"), rs.getInt("ordinal"));
                    final String order = rs.getString("property_order");
                    if (order != null && !order.isEmpty()) {
                        e.propertyOrder.addAll(List.of(order.split(ORDER_SEP)));
                    }
                    byId.put(id, e);
                }
            }
        }
        if (byId.isEmpty()) {
            return List.of();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT v.entity_id, v.property, v.data_type, v.value_text, v.value_num, v.value_date "
                + "FROM entity_values v JOIN entities e ON e.id = v.entity_id "
                + "WHERE e.element_key=? ORDER BY v.entity_id, v.seq")) {
            ps.setString(1, elementKey);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    final EntityGraph.Entity e = byId.get(rs.getLong("entity_id"));
                    if (e == null) {
                        continue;
                    }
                    // The value comes back from value_text, which is exact; value_num is a
                    // comparison key and would lose scale (450.00 -> 450.0) if trusted here.
                    final String text = rs.getString("value_text");
                    final long date = rs.getLong("value_date");
                    e.values.add(new EntityGraph.Value(rs.getString("property"), rs.getString("data_type"),
                        text, EntityGraph.exactNumber(rs.getString("data_type"), text),
                        rs.wasNull() ? null : date));
                }
            }
        }
        return List.copyOf(byId.values());
    }

    // --- business dimensions (DESIGN §8.5) ----------------------------------

    /**
     * Store one transaction set's resolved dimensions. Written in the consume transaction
     * alongside the graph, because a claim that cannot be segmented by its payer is not much
     * more useful than a blob.
     */
    private void insertDimsUnsynchronized(String elementKey, Map<String, EntityGraph.Value> dims)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO transaction_dims (element_key, dim, data_type, value_text, "
                + "value_num, value_date) VALUES (?,?,?,?,?,?)")) {
            for (Map.Entry<String, EntityGraph.Value> e : dims.entrySet()) {
                final EntityGraph.Value v = e.getValue();
                if (v == null) {
                    continue;
                }
                ps.setString(1, elementKey);
                ps.setString(2, e.getKey());
                ps.setString(3, v.dataType());
                ps.setString(4, v.text());
                final Long micros = microUnits(v.num());
                if (micros == null) {
                    ps.setNull(5, Types.INTEGER);
                } else {
                    ps.setLong(5, micros);
                }
                if (v.date() == null) {
                    ps.setNull(6, Types.INTEGER);
                } else {
                    ps.setLong(6, v.date());
                }
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** The dimensions of one transaction set, as {@code dim -> value}. */
    public synchronized Map<String, EntityGraph.Value> dimsFor(String elementKey) throws SQLException {
        final Map<String, EntityGraph.Value> out = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT dim, data_type, value_text, value_num, value_date FROM transaction_dims "
                + "WHERE element_key=? ORDER BY dim")) {
            ps.setString(1, elementKey);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    final String type = rs.getString("data_type");
                    final String text = rs.getString("value_text");
                    final long date = rs.getLong("value_date");
                    out.put(rs.getString("dim"), new EntityGraph.Value(rs.getString("dim"), type, text,
                        EntityGraph.exactNumber(type, text), rs.wasNull() ? null : date));
                }
            }
        }
        return out;
    }

    /** The distinct values a dimension takes — the emergent children of a {@code /by-<dim>} node. */
    public synchronized List<String> distinctDim(String dim) throws SQLException {
        final List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT DISTINCT value_text FROM transaction_dims WHERE dim=? AND value_text IS NOT NULL "
                + "ORDER BY value_text")) {
            ps.setString(1, dim);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }

    /** Micro-units for the comparison column; null when unscalable or out of long range. */
    static Long microUnits(java.math.BigDecimal value) {
        if (value == null) {
            return null;
        }
        try {
            return value.movePointRight(6).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException tooBig) {
            return null;
        }
    }

    /**
     * Anchor instances of a business entity, scoped and paged (DESIGN §8.5). The scope is
     * pushed into SQL — {@code schema_id} always, plus a file or a dimension equality when the
     * caller is inside a {@code /by-<dim>} segment — so a segmented collection never loads the
     * whole buffer to filter it in memory.
     *
     * <p>Each key is {@code element_key + US + path + US + file_id} — enough to locate the
     * instance and stamp its provenance without a second query.
     *
     * @param anchorSchemaId the grain
     * @param fileId         restrict to one interchange file, or null
     * @param dim            dimension name to match, or null
     * @param dimValue       the dimension value to match when {@code dim} is set
     */
    public synchronized List<String> anchorKeys(String anchorSchemaId, String fileId, String dim,
            String dimValue, int limit, int offset) throws SQLException {
        final StringBuilder sql = new StringBuilder(
            "SELECT e.element_key, e.path, e.file_id FROM entities e");
        if (dim != null) {
            sql.append(" JOIN transaction_dims d ON d.element_key = e.element_key AND d.dim = ? "
                + "AND d.value_text = ?");
        }
        sql.append(" WHERE e.schema_id = ?");
        if (fileId != null) {
            sql.append(" AND e.file_id = ?");
        }
        sql.append(" ORDER BY e.id LIMIT ? OFFSET ?");
        final List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int i = 1;
            if (dim != null) {
                ps.setString(i++, dim);
                ps.setString(i++, dimValue);
            }
            ps.setString(i++, anchorSchemaId);
            if (fileId != null) {
                ps.setString(i++, fileId);
            }
            ps.setInt(i++, limit);
            ps.setInt(i, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString("element_key") + "\u001f" + rs.getString("path")
                        + "\u001f" + rs.getString("file_id"));
                }
            }
        }
        return out;
    }

    /** Total anchors in the same scope, for the PagedResults count. */
    public synchronized long anchorCount(String anchorSchemaId, String fileId, String dim,
            String dimValue) throws SQLException {
        final StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM entities e");
        if (dim != null) {
            sql.append(" JOIN transaction_dims d ON d.element_key = e.element_key AND d.dim = ? "
                + "AND d.value_text = ?");
        }
        sql.append(" WHERE e.schema_id = ?");
        if (fileId != null) {
            sql.append(" AND e.file_id = ?");
        }
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int i = 1;
            if (dim != null) {
                ps.setString(i++, dim);
                ps.setString(i++, dimValue);
            }
            ps.setString(i++, anchorSchemaId);
            if (fileId != null) {
                ps.setString(i, fileId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    /** The files that contributed anchors of this grain — emergent {@code /by-file} children. */
    public synchronized List<String> distinctAnchorFiles(String anchorSchemaId) throws SQLException {
        final List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT DISTINCT file_id FROM entities WHERE schema_id=? ORDER BY file_id")) {
            ps.setString(1, anchorSchemaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }

    /**
     * The typed document for one transaction set, reassembled from its graph (DESIGN §8.4).
     * There is no stored document — this IS the read path for {@code take}, the structural
     * collections and {@code validate}.
     */
    public synchronized Map<String, Object> documentFor(String elementKey) throws SQLException {
        return EntityGraph.assemble(graphFor(elementKey));
    }

    /**
     * Documents for a page of transaction sets in two queries rather than two per row: one
     * pass over {@code entities}, one over {@code entity_values}, then assemble each.
     */
    public synchronized Map<String, Map<String, Object>> documentsFor(List<String> elementKeys)
            throws SQLException {
        final Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        if (elementKeys == null || elementKeys.isEmpty()) {
            return out;
        }
        final String in = elementKeys.stream().map(k -> "?").collect(java.util.stream.Collectors.joining(","));
        final Map<String, Map<Long, EntityGraph.Entity>> byKey = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT " + ENTITY_COLS + " FROM entities WHERE element_key IN (" + in + ") ORDER BY id")) {
            for (int i = 0; i < elementKeys.size(); i++) {
                ps.setString(i + 1, elementKeys.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    final long id = rs.getLong("id");
                    final long parent = rs.getLong("parent_id");
                    final EntityGraph.Entity e = EntityGraph.Entity.of((int) id,
                        rs.wasNull() ? null : (int) parent, rs.getString("schema_id"), rs.getString("xid"),
                        rs.getString("kind"), rs.getString("property"), rs.getString("path"),
                        rs.getInt("ordinal"));
                    final String order = rs.getString("property_order");
                    if (order != null && !order.isEmpty()) {
                        e.propertyOrder.addAll(List.of(order.split(ORDER_SEP)));
                    }
                    byKey.computeIfAbsent(rs.getString("element_key"), k -> new LinkedHashMap<>()).put(id, e);
                }
            }
        }
        if (byKey.isEmpty()) {
            return out;
        }
        final Map<Long, EntityGraph.Entity> flat = new LinkedHashMap<>();
        byKey.values().forEach(flat::putAll);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT v.entity_id, v.property, v.data_type, v.value_text, v.value_date "
                + "FROM entity_values v JOIN entities e ON e.id = v.entity_id "
                + "WHERE e.element_key IN (" + in + ") ORDER BY v.entity_id, v.seq")) {
            for (int i = 0; i < elementKeys.size(); i++) {
                ps.setString(i + 1, elementKeys.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    final EntityGraph.Entity e = flat.get(rs.getLong("entity_id"));
                    if (e == null) {
                        continue;
                    }
                    final String type = rs.getString("data_type");
                    final String text = rs.getString("value_text");
                    final long date = rs.getLong("value_date");
                    e.values.add(new EntityGraph.Value(rs.getString("property"), type, text,
                        EntityGraph.exactNumber(type, text), rs.wasNull() ? null : date));
                }
            }
        }
        for (Map.Entry<String, Map<Long, EntityGraph.Entity>> e : byKey.entrySet()) {
            out.put(e.getKey(), EntityGraph.assemble(List.copyOf(e.getValue().values())));
        }
        return out;
    }

    /**
     * Replace one transaction set's graph with a re-derived one and rebind its schema id —
     * {@code ops/recast} (DESIGN §2.5). One transaction: a half-replaced graph would be a
     * transaction whose content is partly old and partly new. Dimensions are left alone; they
     * describe the interchange, not the materialization.
     */
    public synchronized boolean replaceGraph(TransactionRow row, String schemaId,
            List<EntityGraph.Entity> graph) throws SQLException {
        return replaceGraph(row, schemaId, graph, null);
    }

    /**
     * {@link #replaceGraph(TransactionRow, String, List)} that also rewrites the dimensions
     * when {@code dims} is non-null — the startup backfill, where a row with no graph has no
     * dimensions either and would otherwise stay invisible to business-collection filters.
     */
    public synchronized boolean replaceGraph(TransactionRow row, String schemaId,
            List<EntityGraph.Entity> graph, Map<String, EntityGraph.Value> dims) throws SQLException {
        final boolean prev = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE transactions SET schema_id=? WHERE id=? AND status <> 'in_flight'")) {
                ps.setString(1, schemaId);
                ps.setLong(2, row.id());
                if (ps.executeUpdate() == 0) {
                    conn.rollback();
                    return false;   // leased rows are never rewritten under the consumer
                }
            }
            deleteGraphRows(List.of(row.elementKey()), dims != null);
            if (graph != null && !graph.isEmpty()) {
                insertGraphUnsynchronized(row.withSchemaId(schemaId), graph);
            }
            if (dims != null && !dims.isEmpty()) {
                insertDimsUnsynchronized(row.elementKey(), dims);
            }
            conn.commit();
            return true;
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(prev);
        }
    }

    /**
     * Transaction rows with no object graph, oldest first, skipping leased rows and any
     * {@code excluded} keys. A row lands here when it was written before the graph existed
     * (see {@link #migrate}) or when its guide has no materializer — the latter legitimately
     * stays graphless, which is why the caller passes back the keys it already tried.
     */
    public synchronized List<TransactionRow> graphless(int limit, Set<String> excluded) throws SQLException {
        final List<TransactionRow> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT " + TX_COLS + " FROM transactions t WHERE status <> 'in_flight' "
                + "AND NOT EXISTS (SELECT 1 FROM entities e WHERE e.element_key = t.element_key) "
                + "ORDER BY id ASC")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next() && out.size() < limit) {
                    final TransactionRow row = mapTransaction(rs);
                    if (excluded == null || !excluded.contains(row.elementKey())) {
                        out.add(row);
                    }
                }
            }
        }
        return out;
    }

    /** How many instances a transaction set has (a cheap "is the graph there?" probe). */
    public synchronized long entityCount(String elementKey) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM entities WHERE element_key=?")) {
            ps.setString(1, elementKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    /**
     * Drop the graph for element keys whose transaction rows are going away. Called by every
     * delete path: SQLite enforces no foreign key unless the pragma is on, so orphaned
     * entities would otherwise accumulate invisibly and inflate every query that scans them.
     */
    synchronized int deleteGraphs(List<String> elementKeys) throws SQLException {
        if (elementKeys == null || elementKeys.isEmpty()) {
            return 0;
        }
        return deleteGraphRows(elementKeys, true);
    }

    /** Entities + values for these keys; {@code withDims} also drops their dimensions. */
    private int deleteGraphRows(List<String> elementKeys) throws SQLException {
        return deleteGraphRows(elementKeys, false);
    }

    private int deleteGraphRows(List<String> elementKeys, boolean withDims) throws SQLException {
        if (elementKeys == null || elementKeys.isEmpty()) {
            return 0;
        }
        final String in = elementKeys.stream().map(k -> "?").collect(java.util.stream.Collectors.joining(","));
        try (PreparedStatement vals = conn.prepareStatement(
                "DELETE FROM entity_values WHERE entity_id IN "
                + "(SELECT id FROM entities WHERE element_key IN (" + in + "))");
             PreparedStatement ents = conn.prepareStatement(
                "DELETE FROM entities WHERE element_key IN (" + in + ")");
             PreparedStatement dims = conn.prepareStatement(
                "DELETE FROM transaction_dims WHERE element_key IN (" + in + ")")) {
            for (int i = 0; i < elementKeys.size(); i++) {
                vals.setString(i + 1, elementKeys.get(i));
                ents.setString(i + 1, elementKeys.get(i));
                dims.setString(i + 1, elementKeys.get(i));
            }
            vals.executeUpdate();
            if (withDims) {
                dims.executeUpdate();
            }
            return ents.executeUpdate();
        }
    }

}

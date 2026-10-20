package com.zerobias.module.ocsf.buffer;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The durable buffer (DESIGN §5): a single SQLite file in WAL mode holding the {@code files}
 * (audit trail, one per event file) and {@code events} (one per OCSF event, stored as
 * written) tables.
 *
 * <p>Owns one JDBC connection; all methods are {@code synchronized} (single writer). The
 * consume path is one SQL transaction per file ({@link #consumeFile}) — the caller renames
 * the file {@code .done} only after that commit returns (rename is the ack).
 *
 * <p>Timestamps are stored as epoch-millis INTEGERs, not ISO text — integer comparison is
 * correct for ordering, whereas {@code Instant.toString()} varies in fractional precision.
 */
public final class BufferStore implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(BufferStore.class);

    static final String FILE_COLS = "id, file_id, file_path, file_name, source_name, current_path, size_bytes, "
        + "checksum, file_mtime, discovered_at, consumed_at, status, event_count, rejected_count, "
        + "duplicate_count, error_message, rename_failed, redelivery_count";

    private static final String SCHEMA_RESOURCE = "/buffer/schema.sql";

    /** {@code PRAGMA user_version} of the shape {@code schema.sql} creates; see {@link #migrate}. */
    static final int SCHEMA_VERSION = 1;

    /**
     * Rows per retention delete batch: one SQL transaction each, the monitor released between
     * batches so a long eviction never stalls ingest or a read.
     */
    static final int DELETE_BATCH = 500;

    /** Free pages handed back per {@code incremental_vacuum} step, for the same reason. */
    static final int VACUUM_BATCH_PAGES = 1024;

    private final Connection conn;
    private final Clock clock;
    private final String dbPath;

    public BufferStore(String dbPath, boolean fullDurability, Clock clock) throws SQLException {
        this.clock = clock;
        this.dbPath = dbPath;
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
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
        migrate(conn);
        // ackDurability=full -> fsync per commit; overrides the schema's NORMAL.
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA synchronous=" + (fullDurability ? "FULL" : "NORMAL"));
        }
    }

    /**
     * Bring an existing buffer up to {@link #SCHEMA_VERSION}. The buffer volume outlives every
     * redeploy and {@code CREATE TABLE IF NOT EXISTS} never alters an existing table, so every
     * future column change to {@code schema.sql} needs a step here. Version 1 is the first shape.
     */
    static void migrate(Connection conn) throws SQLException {
        SqlTransaction.run(conn, () -> {
            try (Statement st = conn.createStatement()) {
                final long version;
                try (ResultSet rs = st.executeQuery("PRAGMA user_version")) {
                    version = rs.next() ? rs.getLong(1) : 0L;
                }
                if (version < SCHEMA_VERSION) {
                    st.execute("PRAGMA user_version = " + SCHEMA_VERSION);
                }
            }
            return null;
        });
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

    // --- consume path (DESIGN §4) ----------------------------------------------

    /** What {@link #consumeFile} did with a file's events. */
    public record ConsumeResult(int stored, int duplicates, int conflicts) {
    }

    /**
     * Persist one file's events and its {@code files} row in ONE SQL transaction (DESIGN §4
     * step 4). Per event (DESIGN §2): a new {@code (class_uid, event_key)} is stored; the same
     * key with the same body is a duplicate (counted, not stored); the same key with a
     * different body is a conflict, stored under {@code <key>#<fileId>} — never an overwrite.
     * The file row is written with the resulting counts ({@code event_count},
     * {@code duplicate_count}); its {@code rejected_count} comes from the caller. On any
     * failure nothing is written and the exception propagates.
     */
    public synchronized ConsumeResult consumeFile(FileRow file, List<EventRow> events) throws SQLException {
        return SqlTransaction.run(conn, () -> {
            int stored = 0;
            int duplicates = 0;
            int conflicts = 0;
            for (EventRow e : events) {
                if (insertEventUnsynchronized(e)) {
                    stored++;
                    continue;
                }
                final Optional<String> existing = bodyUnsynchronized(e.classUid(), e.eventKey());
                if (existing.isPresent() && existing.get().equals(e.body())) {
                    duplicates++;
                } else if (insertEventUnsynchronized(e.withKey(e.eventKey() + "#" + e.fileId()))) {
                    stored++;
                    conflicts++;
                } else {
                    duplicates++;   // the conflict copy is already there: this file, delivered again
                }
            }
            insertFileUnsynchronized(new FileRow(file.id(), file.fileId(), file.filePath(), file.fileName(),
                file.sourceName(), file.currentPath(), file.sizeBytes(), file.checksum(), file.fileMtime(),
                file.discoveredAt(), file.consumedAt(), file.status(), stored, file.rejectedCount(),
                duplicates, file.errorMessage(), file.renameFailed(), file.redeliveryCount()));
            if (conflicts > 0) {
                LOG.warn("{} event(s) of {} reuse a metadata.uid with different content; stored under a suffixed key",
                    conflicts, file.fileId());
            }
            return new ConsumeResult(stored, duplicates, conflicts);
        });
    }

    /** True if inserted, false if {@code (class_uid, event_key)} is already taken. */
    private boolean insertEventUnsynchronized(EventRow e) throws SQLException {
        final String sql = "INSERT INTO events (event_key, class_uid, category_uid, time, severity_id, "
            + "metadata_version, file_id, source_name, line, received_at, body) VALUES (?,?,?,?,?,?,?,?,?,?,?) "
            + "ON CONFLICT(class_uid, event_key) DO NOTHING";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, e.eventKey());
            ps.setInt(i++, e.classUid());
            setNullableInt(ps, i++, e.categoryUid());
            setNullableLong(ps, i++, e.time());
            setNullableInt(ps, i++, e.severityId());
            ps.setString(i++, e.metadataVersion());
            ps.setString(i++, e.fileId());
            ps.setString(i++, e.sourceName());
            ps.setInt(i++, e.line());
            ps.setLong(i++, (e.receivedAt() == null ? Instant.now(clock) : e.receivedAt()).toEpochMilli());
            ps.setString(i, e.body());
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Insert a {@code files} row on its own (the {@code error} and {@code duplicate} paths).
     * {@code file_id} ({@code <path>@<hash>}) is UNIQUE: the same bytes re-landing at the same
     * path is a redelivery ({@link #bumpRedelivery}), never a second row.
     */
    public synchronized void insertFile(FileRow file) throws SQLException {
        insertFileUnsynchronized(file);
    }

    private void insertFileUnsynchronized(FileRow f) throws SQLException {
        final String sql = "INSERT INTO files (file_id, file_path, file_name, source_name, current_path, size_bytes, "
            + "checksum, file_mtime, discovered_at, consumed_at, status, event_count, rejected_count, "
            + "duplicate_count, error_message, rename_failed, redelivery_count) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
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
            setNullableInt(ps, i++, f.eventCount());
            setNullableInt(ps, i++, f.rejectedCount());
            setNullableInt(ps, i++, f.duplicateCount());
            ps.setString(i++, f.errorMessage());
            ps.setInt(i++, f.renameFailed() ? 1 : 0);
            ps.setInt(i, f.redeliveryCount());
            ps.executeUpdate();
        }
    }

    // --- files (audit trail) -----------------------------------------------------

    /**
     * The file row whose checksum matches, if any (duplicate detection). When several rows share
     * the bytes a {@code consumed}/{@code partial} one wins, then a {@code duplicate}, then the
     * newest {@code error}.
     */
    public synchronized Optional<FileRow> findFileByChecksum(String checksum) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT " + FILE_COLS + " FROM files WHERE checksum=? ORDER BY "
                + "CASE status WHEN 'consumed' THEN 0 WHEN 'partial' THEN 0 WHEN 'duplicate' THEN 1 ELSE 2 END ASC, "
                + "id DESC LIMIT 1")) {
            ps.setString(1, checksum);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapFile(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Delete one {@code files} row (the retry of an {@code error} row whose bytes re-landed at
     * the same path). An error row never has events (they were rolled back or all rejected).
     */
    public synchronized boolean deleteFile(String fileId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM files WHERE file_id=?")) {
            ps.setString(1, fileId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Record that the same bytes re-landed at the same path: {@code redelivery_count} + 1 and,
     * when {@code currentPath} is non-null, {@code current_path} moves to the newest copy.
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
     * Record that the post-commit rename failed: {@code rename_failed=1} and {@code current_path}
     * reset to the discovery path. The id keeps the next scan from re-consuming it: it re-hashes
     * the file, finds this row and treats it as a redelivery.
     */
    public synchronized boolean markRenameFailed(String fileId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE files SET rename_failed=1, current_path=file_path WHERE file_id=?")) {
            ps.setString(1, fileId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Update where a file's bytes live now. Returns true iff a row matched. */
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

    /** Files of one status within one source (health {@code sources[].errored}). */
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

    // --- events: the OCSF interface reads (DESIGN §2) ---------------------------------

    /** Every {@code class_uid} with at least one stored event, ascending ({@code listClasses}). */
    public synchronized List<Integer> distinctClassUids() throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT class_uid FROM events ORDER BY class_uid")) {
            List<Integer> out = new ArrayList<>();
            while (rs.next()) {
                out.add(rs.getInt(1));
            }
            return out;
        }
    }

    /**
     * Event bodies of one class with {@code startTime <= time < endTime}, newest first
     * ({@code list<Class>}). A null bound is open; an event without {@code time} only matches
     * when both bounds are null.
     */
    public synchronized List<String> eventBodies(int classUid, Long startTime, Long endTime, int limit, int offset)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT body FROM events" + timeWhere(startTime, endTime)
                + " ORDER BY time DESC, seq DESC LIMIT ? OFFSET ?")) {
            int i = bindTimeWhere(ps, classUid, startTime, endTime);
            ps.setInt(i++, limit);
            ps.setInt(i, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                List<String> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
                return out;
            }
        }
    }

    /** How many events {@link #eventBodies} would page through (the {@code count} of the page). */
    public synchronized long countEvents(int classUid, Long startTime, Long endTime) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM events" + timeWhere(startTime, endTime))) {
            bindTimeWhere(ps, classUid, startTime, endTime);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private static String timeWhere(Long startTime, Long endTime) {
        return " WHERE class_uid=?" + (startTime != null ? " AND time >= ?" : "") + (endTime != null ? " AND time < ?" : "");
    }

    private static int bindTimeWhere(PreparedStatement ps, int classUid, Long startTime, Long endTime) throws SQLException {
        int i = 1;
        ps.setInt(i++, classUid);
        if (startTime != null) {
            ps.setLong(i++, startTime);
        }
        if (endTime != null) {
            ps.setLong(i++, endTime);
        }
        return i;
    }

    /** The body of one event of one class ({@code get<Class>}); empty when absent. */
    public synchronized Optional<String> eventBody(int classUid, String eventKey) throws SQLException {
        return bodyUnsynchronized(classUid, eventKey);
    }

    private Optional<String> bodyUnsynchronized(int classUid, String eventKey) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT body FROM events WHERE class_uid=? AND event_key=?")) {
            ps.setInt(1, classUid);
            ps.setString(2, eventKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        }
    }

    public synchronized long eventCount() throws SQLException {
        return queryLong("SELECT count(*) FROM events");
    }

    // --- retention primitives (RetentionSweeper) -----------------------------------

    /** Every event ingested at or before {@code cutoffMillis}, in batches; returns the total. */
    int deleteEventsReceivedBefore(long cutoffMillis) throws SQLException {
        int total = 0;
        int n;
        do {
            n = deleteBatch("SELECT seq FROM events WHERE received_at <= ? ORDER BY received_at, seq LIMIT ?",
                cutoffMillis, DELETE_BATCH);
            total += n;
        } while (n == DELETE_BATCH);
        return total;
    }

    /** Up to {@code limit} of the earliest-ingested events, in batches; returns how many went. */
    int deleteOldestEvents(int limit) throws SQLException {
        int total = 0;
        while (total < limit) {
            final int want = Math.min(DELETE_BATCH, limit - total);
            final int n = deleteBatch("SELECT seq FROM events ORDER BY received_at, seq LIMIT ?", null, want);
            total += n;
            if (n < want) {
                break;
            }
        }
        return total;
    }

    private synchronized int deleteBatch(String selectSql, Long cutoffMillis, int limit) throws SQLException {
        return SqlTransaction.run(conn, () -> {
            final List<Long> seqs = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                int i = 1;
                if (cutoffMillis != null) {
                    ps.setLong(i++, cutoffMillis);
                }
                ps.setInt(i, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        seqs.add(rs.getLong(1));
                    }
                }
            }
            if (seqs.isEmpty()) {
                return 0;
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM events WHERE seq IN (" + String.join(",", java.util.Collections.nCopies(seqs.size(), "?")) + ")")) {
                for (int i = 0; i < seqs.size(); i++) {
                    ps.setLong(i + 1, seqs.get(i));
                }
                return ps.executeUpdate();
            }
        });
    }

    /** Size of the database file in bytes (page_count × page_size), free pages included; {@code /stats}. */
    public synchronized long dbSizeBytes() throws SQLException {
        return queryLong("PRAGMA page_count") * queryLong("PRAGMA page_size");
    }

    /**
     * Bytes held by live data ((page_count − freelist_count) × page_size) — the retention ceiling
     * and backpressure measure. Freed pages count as room at once.
     */
    public synchronized long usedBytes() throws SQLException {
        return (queryLong("PRAGMA page_count") - queryLong("PRAGMA freelist_count")) * queryLong("PRAGMA page_size");
    }

    /** Hand free pages back to the filesystem, {@link #VACUUM_BATCH_PAGES} per step. */
    void incrementalVacuum() throws SQLException {
        long free = freePages();
        while (free > 0) {
            vacuumPages(VACUUM_BATCH_PAGES);
            final long left = freePages();
            if (left >= free) {
                return;   // auto_vacuum is off: nothing to hand back
            }
            free = left;
        }
    }

    synchronized long freePages() throws SQLException {
        return queryLong("PRAGMA freelist_count");
    }

    private synchronized void vacuumPages(int pages) throws SQLException {
        // executeUpdate() runs the pragma to completion; execute() steps once (one page).
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("PRAGMA incremental_vacuum(" + pages + ")");
        }
    }

    // --- health / stats metrics ------------------------------------------------------

    /** Size of the WAL sidecar file in bytes, or 0 if it doesn't exist (e.g. fresh db). */
    public synchronized long walBytes() {
        try {
            java.nio.file.Path wal = java.nio.file.Path.of(dbPath + "-wal");
            return java.nio.file.Files.exists(wal) ? java.nio.file.Files.size(wal) : 0L;
        } catch (IOException e) {
            return 0L;
        }
    }

    /** Epoch-millis of the most recent file consumption, or empty if none yet. */
    public synchronized OptionalLong lastConsumedMillis() throws SQLException {
        return queryNullableLong("SELECT max(consumed_at) FROM files WHERE status IN ('consumed', 'partial')");
    }

    public Clock clock() {
        return clock;
    }

    @Override
    public synchronized void close() throws SQLException {
        conn.close();
    }

    private long queryLong(String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    /** Like {@link #queryLong} but distinguishes SQL NULL (e.g. max over no rows) from 0. */
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

    private static void setNullableLong(PreparedStatement ps, int idx, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setLong(idx, value);
        }
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
            nullableInt(rs, "event_count"),
            nullableInt(rs, "rejected_count"),
            nullableInt(rs, "duplicate_count"),
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

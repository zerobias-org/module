package com.zerobias.module.x12.producer;

import com.zerobias.module.x12.ModuleRuntimeConfig;
import com.zerobias.module.x12.SourceConfig;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.FileRow;
import com.zerobias.module.x12.buffer.Status;
import com.zerobias.module.x12.health.PollerStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Stream;

/**
 * The DataProducer object hierarchy (DESIGN §2.1): a tree of container / collection /
 * binary / function / document nodes addressed by path-style ids.
 *
 * <pre>
 * /                              container (root; id == name == "/")
 * └─ /x12-receiver               container
 *    ├─ /files                   container → /files/&lt;fileId&gt; ["container","document","binary"]
 *    │                                       → /files/&lt;fileId&gt;/transactions  collection (envelope)
 *    ├─ /inbox                   container → the live volume, never cached ({@link InboxFiles}, DESIGN §2.9)
 *    ├─ /transactions            collection (all rows, envelope schema)
 *    ├─ /by-type                 container → /by-type/&lt;TS&gt; container → /by-type/&lt;TS&gt;/&lt;GS08&gt; collection
 *    ├─ /by-version              container → /by-version/&lt;GS08&gt;     collection (envelope)
 *    ├─ /by-sender               container → /by-sender/&lt;ISA06&gt;     collection (envelope)
 *    ├─ /by-source               container → /by-source/&lt;sourceName&gt; collection (envelope)
 *    ├─ /stats                   document (schema:shared:x12.receiver-stats)
 *    └─ /ops                     container → /ops/&lt;fn&gt; function (DESIGN §2.5)
 * </pre>
 *
 * <p>A <em>transaction set is an atom</em> — a collection element keyed
 * {@code <fileId>:<ISA13>:<GS06>:<ST02>}, never a node. Folders are discriminators and their
 * children are <em>emergent</em>: read live from the buffer's DISTINCT values, so a node
 * appears the first time matching data lands. {@code /files/<fileId>} is the one
 * exception: a file is a folder (its transactions), a document (its {@code files} row) and a
 * binary (its bytes).
 *
 * <p>{@code /by-type/<TS>} is always a container of per-guide collections, even while a
 * type has arrived under one GS08 only: an id must never change class when a second guide
 * lands, or every consumer holding {@code /by-type/<TS>} as a collection breaks.
 *
 * <p><b>Id encoding.</b> {@code fileId} ({@code <absolute path>@<hash>}) contains {@code /},
 * and a sender id or source name may too. Discriminator values are embedded in object ids
 * percent-encoded for {@code /} and {@code %} only ({@link #encodeSegment}) so every id
 * round-trips through {@code getObject}/{@code getChildren}/{@code getCollectionElements}
 * verbatim; an un-encoded value without {@code %} decodes to itself.
 */
public final class ObjectTree {

    private static final Logger LOG = LoggerFactory.getLogger(ObjectTree.class);

    public static final String ROOT = "/";
    public static final String RECEIVER = "/x12-receiver";
    static final String FILES = RECEIVER + "/files";
    static final String TRANSACTIONS = RECEIVER + "/transactions";
    static final String BY_TYPE = RECEIVER + "/by-type";
    static final String BY_VERSION = RECEIVER + "/by-version";
    static final String BY_SENDER = RECEIVER + "/by-sender";
    static final String BY_SOURCE = RECEIVER + "/by-source";
    static final String STATS = RECEIVER + "/stats";
    static final String OPS = RECEIVER + "/ops";
    static final String TRANSACTIONS_LEAF = "transactions";

    static final String ENVELOPE_SCHEMA = SchemaRegistry.ENVELOPE_SCHEMA;
    static final String STATS_SCHEMA = "schema:shared:" + SchemaRegistry.CATALOG + ".receiver-stats";
    static final String FILE_SCHEMA = "schema:shared:" + SchemaRegistry.CATALOG + ".file";
    static final List<String> FILE_CLASSES = List.of("container", "document", "binary");

    /**
     * A collection's buffer scope (a WHERE fragment over {@code transactions}; null = all
     * rows) + its element schema id (DESIGN §2.1 homogeneity rule).
     */
    public record Collection(String id, String scopeWhere, String schemaId) {
    }

    /** One page of child objects and the total number of children. */
    public record Page(List<Map<String, Object>> items, long total) {
    }

    private final BufferStore buffer;
    private final SchemaRegistry schemas;
    private final PollerStatus poller;
    private final List<SourceConfig> sources;
    private final String consumedSuffix;
    private final InboxFiles inbox;

    /**
     * @param schemas used to pick a guide-bound {@code schema:table} for a {@code /by-type}
     *                collection (falls back to the envelope when that guide is not bundled)
     * @param poller  the live poller state behind {@code /stats}
     * @param config  the watched sources — {@code downloadBinary} serves only files inside
     *                them, and they become the live {@code /inbox/<source>} branch
     *                ({@link InboxFiles}) — and the consumed/error suffixes ({@code /stats}
     *                counts, the {@code ingest} field on live file nodes)
     */
    public ObjectTree(BufferStore buffer, SchemaRegistry schemas, PollerStatus poller, ModuleRuntimeConfig config) {
        this.buffer = Objects.requireNonNull(buffer, "buffer");
        this.schemas = Objects.requireNonNull(schemas, "schemas");
        this.poller = Objects.requireNonNull(poller, "poller");
        Objects.requireNonNull(config, "config");
        this.sources = config.sources();
        this.consumedSuffix = config.consumedSuffix();
        this.inbox = new InboxFiles(config.sources(), config.consumedSuffix(), config.errorSuffix());
    }

    // --- id encoding ---------------------------------------------------------

    /** Percent-encode {@code %} and {@code /} so a discriminator value can sit in one path segment. */
    public static String encodeSegment(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%') {
                sb.append("%25");
            } else if (c == '/') {
                sb.append("%2F");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Inverse of {@link #encodeSegment}; any other {@code %xx} sequence is left as-is. */
    public static String decodeSegment(String segment) {
        if (segment.indexOf('%') < 0) {
            return segment;
        }
        StringBuilder sb = new StringBuilder(segment.length());
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c == '%' && i + 2 < segment.length()) {
                String hex = segment.substring(i + 1, i + 3).toUpperCase();
                if ("2F".equals(hex)) {
                    sb.append('/');
                    i += 2;
                    continue;
                }
                if ("25".equals(hex)) {
                    sb.append('%');
                    i += 2;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    // --- discriminator helpers ---------------------------------------------

    private List<String> types() throws SQLException {
        return buffer.distinctValues("transaction_type");
    }

    private static String typeScope(String ts) {
        return "transaction_type = " + sql(ts);
    }

    private static String typeVersionScope(String ts, String gs08) {
        return "transaction_type = " + sql(ts) + " AND gs08 = " + sql(gs08);
    }

    private static String fileScope(String fileId) {
        return "file_id = " + sql(fileId);
    }

    /** Guide-bound table schema for a (GS08, TS) pair, or the envelope if that guide isn't bundled. */
    private String tableSchema(String gs08, String ts) {
        String tableId = "schema:table:" + SchemaRegistry.CATALOG + "." + gs08 + "." + ts;
        return schemas.has(tableId) ? tableId : ENVELOPE_SCHEMA;
    }

    private FileRow requireFile(String fileId, String objectId) throws SQLException {
        Optional<FileRow> f = buffer.fileById(fileId);
        if (f.isEmpty()) {
            throw ProducerException.noSuchObject(objectId);
        }
        return f.get();
    }

    /** {@code (fileId, isTransactionsLeaf)} for an id under {@code /files/}, or null when not one. */
    private static String[] parseFileId(String id) {
        if (!id.startsWith(FILES + "/")) {
            return null;
        }
        String rem = id.substring((FILES + "/").length());
        if (rem.isEmpty()) {
            return null;
        }
        String suffix = "/" + TRANSACTIONS_LEAF;
        if (rem.endsWith(suffix)) {
            return new String[] {decodeSegment(rem.substring(0, rem.length() - suffix.length())), TRANSACTIONS_LEAF};
        }
        return new String[] {decodeSegment(rem), null};
    }

    /**
     * {@code (TS, GS08)} for an id under {@code /by-type/}, GS08 null for the type container;
     * null when {@code id} is not under {@code /by-type/}.
     */
    private static String[] parseTypeId(String id) {
        if (!id.startsWith(BY_TYPE + "/")) {
            return null;
        }
        String rem = id.substring((BY_TYPE + "/").length());
        int slash = rem.indexOf('/');
        if (slash < 0) {
            return new String[] {decodeSegment(rem), null};
        }
        return new String[] {decodeSegment(rem.substring(0, slash)), decodeSegment(rem.substring(slash + 1))};
    }

    static String fileObjectId(String fileId) {
        return FILES + "/" + encodeSegment(fileId);
    }

    static String fileTransactionsId(String fileId) {
        return fileObjectId(fileId) + "/" + TRANSACTIONS_LEAF;
    }

    // --- collection resolution ---------------------------------------------

    /**
     * Resolve a collection id to its buffer scope, or throw: {@code noSuchObjectError} for
     * an unknown id or discriminator value, {@code UnsupportedOperationError} if the id is
     * a container, document or function rather than a collection. (§2.1, §2.6)
     */
    public Collection resolveCollection(String id) throws SQLException {
        if (TRANSACTIONS.equals(id)) {
            return new Collection(id, null, ENVELOPE_SCHEMA);
        }
        String[] file = parseFileId(id);
        if (file != null && file[1] != null) {
            requireFile(file[0], id);
            return new Collection(id, fileScope(file[0]), ENVELOPE_SCHEMA);
        }
        String[] type = parseTypeId(id);
        if (type != null && type[1] != null) {
            requireTypeVersion(type[0], type[1], id);
            return new Collection(id, typeVersionScope(type[0], type[1]), tableSchema(type[1], type[0]));
        }
        Collection facet = facetCollection(id, BY_VERSION, "gs08");
        if (facet == null) {
            facet = facetCollection(id, BY_SENDER, "sender_id");
        }
        if (facet == null) {
            facet = facetCollection(id, BY_SOURCE, "source_name");
        }
        if (facet != null) {
            return facet;
        }
        object(id); // throws noSuchObject if unknown
        throw ProducerException.unsupported("Object is not a collection: " + id);
    }

    private void requireTypeVersion(String ts, String gs08, String id) throws SQLException {
        if (!buffer.exists(typeVersionScope(ts, gs08))) {
            throw ProducerException.noSuchObject(id);
        }
    }

    /** A coarse (heterogeneous, envelope-schema) facet collection, or null when {@code id} isn't under {@code prefix}. */
    private Collection facetCollection(String id, String prefix, String column) throws SQLException {
        if (!id.startsWith(prefix + "/")) {
            return null;
        }
        String value = decodeSegment(id.substring((prefix + "/").length()));
        String scope = column + " = " + sql(value);
        if (!buffer.exists(scope)) {
            throw ProducerException.noSuchObject(id);
        }
        return new Collection(id, scope, ENVELOPE_SCHEMA);
    }

    // --- object metadata ----------------------------------------------------

    /** Object metadata for {@code id}, or throw {@code noSuchObjectError}. (§2.1) */
    public Map<String, Object> object(String id) throws SQLException {
        switch (id) {
            case ROOT:
                return container(ROOT, ROOT);
            case RECEIVER:
                return container(RECEIVER, "x12-receiver");
            case FILES:
                return container(FILES, "files");
            case TRANSACTIONS:
                return collection(TRANSACTIONS, "transactions", ENVELOPE_SCHEMA, buffer.count());
            case BY_TYPE:
                return container(BY_TYPE, "by-type");
            case BY_VERSION:
                return container(BY_VERSION, "by-version");
            case BY_SENDER:
                return container(BY_SENDER, "by-sender");
            case BY_SOURCE:
                return container(BY_SOURCE, "by-source");
            case STATS:
                return document(STATS, "stats", STATS_SCHEMA);
            case OPS:
                return container(OPS, "ops");
            default:
                return dynamicObject(id);
        }
    }

    private Map<String, Object> dynamicObject(String id) throws SQLException {
        if (InboxFiles.owns(id)) {
            return inbox.object(id);   // a fresh stat; never cached (DESIGN §2.9)
        }
        String[] file = parseFileId(id);
        if (file != null) {
            FileRow f = requireFile(file[0], id);
            if (file[1] == null) {
                return fileNode(f);
            }
            return collection(id, TRANSACTIONS_LEAF, ENVELOPE_SCHEMA, buffer.countWhere(fileScope(file[0])));
        }
        String[] type = parseTypeId(id);
        if (type != null) {
            if (type[1] == null) {
                if (!buffer.exists(typeScope(type[0]))) {
                    throw ProducerException.noSuchObject(id);
                }
                return container(id, type[0]);
            }
            requireTypeVersion(type[0], type[1], id);
            return collection(id, type[1], tableSchema(type[1], type[0]),
                buffer.countWhere(typeVersionScope(type[0], type[1])));
        }
        for (String[] facet : new String[][] {{BY_VERSION, "gs08"}, {BY_SENDER, "sender_id"}, {BY_SOURCE, "source_name"}}) {
            Collection c = facetCollection(id, facet[0], facet[1]);
            if (c != null) {
                String value = decodeSegment(id.substring((facet[0] + "/").length()));
                return collection(id, value, ENVELOPE_SCHEMA, buffer.countWhere(c.scopeWhere()));
            }
        }
        if (id.startsWith(OPS + "/")) {
            String fn = id.substring((OPS + "/").length());
            if (!SchemaRegistry.OPS_FUNCTIONS.contains(fn)) {
                throw ProducerException.noSuchObject(id);
            }
            return function(id, fn);
        }
        throw ProducerException.noSuchObject(id);
    }

    // --- children -----------------------------------------------------------

    /**
     * One page of the direct children of {@code id} (emergent from the buffer's DISTINCT
     * values), or throw {@code noSuchObjectError}. {@code /files} pages in SQL — the files
     * table is the never-evicted audit trail; every other child list is bounded by the
     * number of distinct discriminator values and is paged in memory. (§2.1)
     */
    public Page children(String id, int limit, int offset) throws SQLException {
        if (FILES.equals(id)) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (FileRow f : buffer.fileRows(null, limit, offset)) {
                items.add(fileNode(f));
            }
            return new Page(items, buffer.fileCount((String) null));
        }
        List<Map<String, Object>> all = allChildren(id);
        int from = Math.min(offset, all.size());
        int to = Math.min(all.size(), from + limit);
        return new Page(new ArrayList<>(all.subList(from, to)), all.size());
    }

    private List<Map<String, Object>> allChildren(String id) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        switch (id) {
            case ROOT:
                out.add(object(RECEIVER));
                return out;
            case RECEIVER:
                for (String child : List.of(FILES, InboxFiles.INBOX, TRANSACTIONS, BY_TYPE, BY_VERSION, BY_SENDER,
                        BY_SOURCE, STATS, OPS)) {
                    out.add(object(child));
                }
                return out;
            case BY_TYPE:
                for (String ts : types()) {
                    out.add(container(BY_TYPE + "/" + encodeSegment(ts), ts));
                }
                return out;
            case BY_VERSION:
                return facetChildren(BY_VERSION, "gs08");
            case BY_SENDER:
                return facetChildren(BY_SENDER, "sender_id");
            case BY_SOURCE:
                return facetChildren(BY_SOURCE, "source_name");
            case OPS:
                for (String fn : SchemaRegistry.OPS_FUNCTIONS) {
                    out.add(function(OPS + "/" + fn, fn));
                }
                return out;
            default:
                return dynamicChildren(id);
        }
    }

    private List<Map<String, Object>> facetChildren(String prefix, String column) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, Long> v : buffer.distinctCounts(column, null).entrySet()) {
            out.add(collection(prefix + "/" + encodeSegment(v.getKey()), v.getKey(), ENVELOPE_SCHEMA, v.getValue()));
        }
        return out;
    }

    /**
     * Children of a dynamic container: a {@code /files/<fileId>} node lists its
     * {@code transactions} collection; a {@code /by-type/<TS>} node lists its guides.
     */
    private List<Map<String, Object>> dynamicChildren(String id) throws SQLException {
        if (InboxFiles.owns(id)) {
            return inbox.children(id);   // a fresh readdir; never cached (DESIGN §2.9)
        }
        List<Map<String, Object>> out = new ArrayList<>();
        String[] file = parseFileId(id);
        if (file != null && file[1] == null) {
            requireFile(file[0], id);
            out.add(object(fileTransactionsId(file[0])));
            return out;
        }
        String[] type = parseTypeId(id);
        if (type != null && type[1] == null) {
            Map<String, Long> versions = buffer.distinctCounts("gs08", typeScope(type[0]));
            if (versions.isEmpty()) {
                throw ProducerException.noSuchObject(id);
            }
            for (Map.Entry<String, Long> v : versions.entrySet()) {
                out.add(collection(id + "/" + encodeSegment(v.getKey()), v.getKey(), tableSchema(v.getKey(), type[0]),
                    v.getValue()));
            }
            return out;
        }
        // leaf (collection/document/function) -> no children; unknown id -> 404 via object().
        object(id);
        return out;
    }

    // --- documents ----------------------------------------------------------

    /**
     * The document body of {@code /stats} or of a {@code /files/<fileId>} node, or throw
     * {@code noSuchObjectError} / {@code UnsupportedOperationError} for anything else.
     */
    public Map<String, Object> documentData(String id) throws SQLException {
        if (STATS.equals(id)) {
            return stats();
        }
        String[] file = parseFileId(id);
        if (file != null && file[1] == null) {
            return fileDocument(requireFile(file[0], id));
        }
        object(id);
        throw ProducerException.unsupported("Object is not a document: " + id);
    }

    /**
     * The {@code /stats} body ({@code schema:shared:x12.receiver-stats}): poller state from
     * the live {@link PollerStatus} + buffer counters + inbox hygiene ({@code .done} files
     * still in the watched dirs, DESIGN §11.2).
     */
    private Map<String, Object> stats() throws SQLException {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("up", poller.up());
        poller.lastScan().ifPresent(t -> out.put("lastScan", t.toString()));
        if (poller.lastConsumed().isPresent()) {
            out.put("lastConsumed", poller.lastConsumed().get().toString());
        } else {
            OptionalLong last = buffer.lastConsumedMillis();
            if (last.isPresent()) {
                out.put("lastConsumed", Instant.ofEpochMilli(last.getAsLong()).toString());
            }
        }
        long newCount = buffer.count(Status.NEW);
        long inFlight = buffer.count(Status.IN_FLIGHT);
        long acked = buffer.count(Status.ACKED);
        out.put("bufferDepth", newCount + inFlight);
        OptionalLong oldest = buffer.oldestUnackedSeconds();
        if (oldest.isPresent()) {
            out.put("oldestUnackedSec", oldest.getAsLong());
        }
        out.put("backpressure", poller.backpressure());
        out.put("newCount", newCount);
        out.put("inFlightCount", inFlight);
        out.put("ackedCount", acked);
        out.put("fileCount", buffer.fileCount());

        long[] done = doneFiles();
        out.put("doneFileCount", done[0]);
        if (done[0] > 0) {
            out.put("oldestDoneFileAgeSec", done[1]);
        }
        out.put("walBytes", buffer.walBytes());
        out.put("dbSizeBytes", buffer.dbSizeBytes());

        List<Map<String, Object>> sourceStats = new ArrayList<>();
        for (PollerStatus.SourceStatus s : poller.sources()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", s.name());
            m.put("path", s.path());
            m.put("writable", s.writable());
            m.put("pending", s.pending());
            m.put("errored", s.errored());
            if (s.lastScanCompleted() != null) {
                m.put("lastScanCompleted", s.lastScanCompleted().toString());
            }
            sourceStats.add(m);
        }
        out.put("sources", sourceStats);
        return out;
    }

    /** {@code {count, oldestAgeSec}} of {@code <consumedSuffix>} files across the watched dirs; IO problems count as none. */
    private long[] doneFiles() {
        long count = 0;
        long oldestMtime = Long.MAX_VALUE;
        for (SourceConfig s : sources) {
            Path dir = s.dir();
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> list = Files.list(dir)) {
                for (Path f : (Iterable<Path>) list::iterator) {
                    if (!f.getFileName().toString().endsWith(consumedSuffix) || !Files.isRegularFile(f)) {
                        continue;
                    }
                    count++;
                    try {
                        oldestMtime = Math.min(oldestMtime, Files.getLastModifiedTime(f).toMillis());
                    } catch (IOException ignored) {
                        // unreadable entry: counted, age unknown
                    }
                }
            } catch (IOException ignored) {
                // unreadable dir: reported by /healthz (writable=false), not here
            }
        }
        long age = oldestMtime == Long.MAX_VALUE ? 0
            : Math.max(0, (Instant.now(buffer.clock()).toEpochMilli() - oldestMtime) / 1000);
        return new long[] {count, age};
    }

    /** A {@code files} row as the {@code schema:shared:x12.file} document. */
    private static Map<String, Object> fileDocument(FileRow f) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("fileId", f.fileId());
        d.put("filePath", f.filePath() == null ? FileRow.pathOf(f.fileId()) : f.filePath());
        d.put("fileName", f.fileName());
        d.put("sourceName", f.sourceName());
        putIfPresent(d, "currentPath", f.currentPath());
        d.put("size", f.sizeBytes());
        d.put("mimeType", BinaryContent.MIME_X12);
        d.put("checksum", f.checksum());
        putIfPresent(d, "modified", f.fileMtime());
        putIfPresent(d, "created", f.discoveredAt());
        putIfPresent(d, "consumedAt", f.consumedAt());
        d.put("status", f.status().wire());
        d.put("tags", fileTags(f));
        putIfPresent(d, "isaCount", f.isaCount());
        putIfPresent(d, "transactionCount", f.transactionCount());
        putIfPresent(d, "errorMessage", f.errorMessage());
        d.put("renameFailed", f.renameFailed());
        d.put("redeliveryCount", f.redeliveryCount());
        return d;
    }

    private static void putIfPresent(Map<String, Object> m, String key, Object value) {
        if (value != null) {
            m.put(key, value instanceof Instant ? value.toString() : value);
        }
    }

    // --- binary -------------------------------------------------------------

    /**
     * Where the bytes of a {@code /files/<fileId>} node live, resolved through
     * {@code files.current_path} so download works after the {@code .done} rename
     * (DESIGN §2.8). The path must be a regular file directly inside the file's configured
     * source directory — a row pointing elsewhere (a tampered buffer, a source since removed
     * from config) or a symlink planted at the recorded name is never served. 404
     * {@code gone} when the bytes are unavailable; the transactions remain.
     */
    public BinaryContent downloadBinary(String id) throws SQLException {
        if (InboxFiles.owns(id)) {
            return inbox.downloadBinary(id);   // read straight off the volume, ingested or not
        }
        String[] file = parseFileId(id);
        if (file == null || file[1] != null) {
            object(id);
            throw ProducerException.unsupported("Object is not a binary: " + id);
        }
        FileRow f = requireFile(file[0], id);
        if (f.currentPath() == null) {
            throw ProducerException.fileGone(f.fileId());
        }
        Path current = Path.of(f.currentPath());
        Path sourceDir = sourceDir(f.sourceName());
        if (sourceDir == null || !SourceConfig.isEntryOf(sourceDir, current)) {
            LOG.warn("Refusing to serve {}: {} is not an entry of a configured source '{}'",
                f.fileId(), current, f.sourceName());
            throw ProducerException.fileGone(f.fileId());
        }
        BasicFileAttributes attrs;
        try {
            attrs = Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            throw ProducerException.fileGone(f.fileId());
        }
        if (!attrs.isRegularFile()) {
            LOG.warn("Refusing to serve {}: {} is not a regular file", f.fileId(), current);
            throw ProducerException.fileGone(f.fileId());
        }
        return new BinaryContent(f.fileId(), current, attrs.size(), BinaryContent.MIME_X12, f.fileName());
    }

    private Path sourceDir(String sourceName) {
        for (SourceConfig s : sources) {
            if (s.name().equals(sourceName)) {
                return s.dir();
            }
        }
        return null;
    }

    // --- write surface: the live /inbox branch only (DESIGN §2.9) -----------

    /**
     * {@code uploadBinaryContent}: write bytes into a live {@code /inbox} container. The
     * emergent branches reject it — {@code /files} is a projection of the buffer, so
     * "uploading" into it would mean inventing a consumed file that never arrived.
     */
    public Map<String, Object> uploadBinary(String id, String fileName, byte[] bytes) throws SQLException {
        if (InboxFiles.owns(id)) {
            return inbox.upload(id, fileName, bytes);
        }
        object(id);   // 404 an unknown id before reporting it unsupported
        throw ProducerException.unsupported(
            "Upload targets a directory under " + InboxFiles.INBOX + ", not " + id);
    }

    /** {@code createChildObject}: mkdir under a live {@code /inbox} container. */
    public Map<String, Object> createChildContainer(String id, String name) throws SQLException {
        if (InboxFiles.owns(id)) {
            return inbox.mkdir(id, name);
        }
        object(id);
        throw ProducerException.unsupported(
            "Directories can only be created under " + InboxFiles.INBOX + ", not " + id);
    }

    /** {@code deleteObject}: unlink a live file or remove an empty live directory. */
    public void deleteObject(String id) throws SQLException {
        if (InboxFiles.owns(id)) {
            inbox.delete(id);
            return;
        }
        object(id);
        throw ProducerException.unsupported(
            "Only objects under " + InboxFiles.INBOX + " are deletable; buffer rows leave via ops/purge: " + id);
    }

    // --- object builders ---------------------------------------------------

    private static Map<String, Object> container(String id, String name) {
        Map<String, Object> o = base(id, name);
        o.put("objectClass", List.of("container"));
        return o;
    }

    private static Map<String, Object> collection(String id, String name, String schemaId, long size) {
        Map<String, Object> o = base(id, name);
        o.put("objectClass", List.of("collection"));
        o.put("collectionSchema", schemaId);
        o.put("collectionSize", size);
        return o;
    }

    private static Map<String, Object> document(String id, String name, String schemaId) {
        Map<String, Object> o = base(id, name);
        o.put("objectClass", List.of("document"));
        o.put("documentSchema", schemaId);
        return o;
    }

    /**
     * The {@code /files/<fileId>} node: only interface {@code DataProducerObject} fields —
     * the rest of the {@code files} row (fileId, paths, status, counts) is its document.
     */
    private static Map<String, Object> fileNode(FileRow f) {
        Map<String, Object> o = base(fileObjectId(f.fileId()), f.fileName());
        o.put("objectClass", FILE_CLASSES);
        o.put("documentSchema", FILE_SCHEMA);
        o.put("fileName", f.fileName());
        o.put("size", f.sizeBytes());
        o.put("mimeType", BinaryContent.MIME_X12);
        o.put("checksum", f.checksum());
        putIfPresent(o, "modified", f.fileMtime());
        putIfPresent(o, "created", f.discoveredAt());
        o.put("tags", fileTags(f));
        return o;
    }

    private static List<String> fileTags(FileRow f) {
        return List.of("source:" + f.sourceName(), "status:" + f.status().wire());
    }

    private static Map<String, Object> function(String id, String fn) {
        Map<String, Object> o = base(id, fn);
        o.put("objectClass", List.of("function"));
        o.put("inputSchema", SchemaRegistry.functionInputId(fn));
        o.put("outputSchema", SchemaRegistry.functionOutputId(fn));
        o.put("throws", X12Operations.declaredErrors(fn));
        return o;
    }

    private static Map<String, Object> base(String id, String name) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", id);
        o.put("name", name);
        return o;
    }

    /** Single-quote-escaped SQL string literal (scope values are buffer-derived or decoded ids). */
    static String sql(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}

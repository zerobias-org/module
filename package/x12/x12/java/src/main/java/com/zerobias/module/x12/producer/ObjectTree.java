package com.zerobias.module.x12.producer;

import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.FileRow;
import com.zerobias.module.x12.buffer.Status;
import com.zerobias.module.x12.health.PollerStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The DataProducer object hierarchy (DESIGN §2.1): a tree of container / collection /
 * binary / function / document nodes addressed by path-style ids.
 *
 * <pre>
 * /                              container (root; id == name == "/")
 * └─ /x12-receiver               container
 *    ├─ /files                   container → /files/&lt;fileId&gt; ["container","binary"] (one per files row)
 *    │                                       → /files/&lt;fileId&gt;/transactions  collection (envelope)
 *    ├─ /transactions            collection (all rows, envelope schema)
 *    ├─ /by-type                 container → /by-type/&lt;TS&gt;  collection while a TS has ONE GS08;
 *    │                             ELSE a container whose /by-type/&lt;TS&gt;/&lt;GS08&gt; leaves are the
 *    │                             collections — the version level is interposed only when needed
 *    ├─ /by-version              container → /by-version/&lt;GS08&gt;     collection (envelope)
 *    ├─ /by-sender               container → /by-sender/&lt;ISA06&gt;     collection (envelope)
 *    ├─ /by-source               container → /by-source/&lt;sourceName&gt; collection (envelope)
 *    ├─ /stats                   document (schema:shared:x12.receiver-stats)
 *    └─ /ops                     container → /ops/&lt;fn&gt; function (DESIGN §2.5)
 * </pre>
 *
 * <p>A <em>transaction set is an atom</em> — a collection element keyed
 * {@code <fileId>:<GS06>:<ST02>}, never a node. Folders are discriminators and their
 * children are <em>emergent</em>: read live from the buffer's DISTINCT values, so a node
 * appears the first time matching data lands. {@code /files/<fileId>} is the one
 * exception: a file is both a folder (its transactions) and a binary (its bytes).
 *
 * <p><b>Id encoding.</b> {@code fileId} ({@code <absolute path>@<hash>}) contains {@code /},
 * and a sender id or source name may too. Discriminator values are embedded in object ids
 * percent-encoded for {@code /} and {@code %} only ({@link #encodeSegment}) so every id
 * round-trips through {@code getObject}/{@code getChildren}/{@code getCollectionElements}
 * verbatim; an un-encoded value without {@code %} decodes to itself.
 */
public final class ObjectTree implements ObjectTreeApi {

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
    static final List<String> OPS_FUNCTIONS = SchemaRegistry.OPS_FUNCTIONS;

    private static final String DEFAULT_CONSUMED_SUFFIX = ".done";

    private final BufferStore buffer;
    private final SchemaRegistryApi schemas;
    private final Supplier<PollerStatus> poller;
    private final String consumedSuffix;

    /** {@code poller} feeds {@code /stats}; it may yield null (treated as {@link PollerStatus#DOWN}). */
    public ObjectTree(BufferStore buffer, Supplier<PollerStatus> poller) {
        this(buffer, SchemaRegistryApi.EMPTY, poller, DEFAULT_CONSUMED_SUFFIX);
    }

    public ObjectTree(BufferStore buffer, SchemaRegistryApi schemas, Supplier<PollerStatus> poller) {
        this(buffer, schemas, poller, DEFAULT_CONSUMED_SUFFIX);
    }

    /**
     * @param schemas        used to pick a guide-bound {@code schema:table} for a homogeneous
     *                       {@code /by-type} collection (falls back to the envelope when unbundled)
     * @param consumedSuffix the {@code .done} suffix, for the {@code /stats} inbox-hygiene counters
     */
    public ObjectTree(BufferStore buffer, SchemaRegistryApi schemas, Supplier<PollerStatus> poller,
            String consumedSuffix) {
        this.buffer = buffer;
        this.schemas = schemas == null ? SchemaRegistryApi.EMPTY : schemas;
        this.poller = poller == null ? () -> PollerStatus.DOWN : poller;
        this.consumedSuffix = consumedSuffix == null || consumedSuffix.isBlank() ? DEFAULT_CONSUMED_SUFFIX : consumedSuffix;
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

    /** GS08 guides present for one transaction type (the {@code /by-type/<TS>} children). */
    private List<String> versionsForType(String ts) throws SQLException {
        return buffer.distinctValues("gs08", "transaction_type = " + sql(ts));
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

    static String fileObjectId(String fileId) {
        return FILES + "/" + encodeSegment(fileId);
    }

    static String fileTransactionsId(String fileId) {
        return fileObjectId(fileId) + "/" + TRANSACTIONS_LEAF;
    }

    // --- collection resolution ---------------------------------------------

    @Override
    public Collection resolveCollection(String id) throws SQLException {
        if (TRANSACTIONS.equals(id)) {
            return new Collection(id, null, ENVELOPE_SCHEMA);
        }
        String[] file = parseFileId(id);
        if (file != null) {
            requireFile(file[0], id);
            if (file[1] == null) {
                throw ProducerException.unsupported("Object is not a collection (drill into /transactions): " + id);
            }
            return new Collection(id, fileScope(file[0]), ENVELOPE_SCHEMA);
        }
        if (id.startsWith(BY_TYPE + "/")) {
            String rem = id.substring((BY_TYPE + "/").length());
            int slash = rem.indexOf('/');
            if (slash < 0) {
                String ts = decodeSegment(rem);
                List<String> vers = versionsForType(ts);
                if (vers.isEmpty()) {
                    throw ProducerException.noSuchObject(id);
                }
                if (vers.size() == 1) {
                    return new Collection(id, typeScope(ts), tableSchema(vers.get(0), ts));
                }
                throw ProducerException.unsupported("Object is not a collection (drill into a version): " + id);
            }
            String ts = decodeSegment(rem.substring(0, slash));
            String gs08 = decodeSegment(rem.substring(slash + 1));
            List<String> vers = versionsForType(ts);
            if (!vers.contains(gs08) || vers.size() == 1) {   // version node exists only when required
                throw ProducerException.noSuchObject(id);
            }
            return new Collection(id, typeVersionScope(ts, gs08), tableSchema(gs08, ts));
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

    /** A coarse (heterogeneous, envelope-schema) facet collection, or null when {@code id} isn't under {@code prefix}. */
    private Collection facetCollection(String id, String prefix, String column) throws SQLException {
        if (!id.startsWith(prefix + "/")) {
            return null;
        }
        String value = decodeSegment(id.substring((prefix + "/").length()));
        if (!buffer.distinctValues(column).contains(value)) {
            throw ProducerException.noSuchObject(id);
        }
        return new Collection(id, column + " = " + sql(value), ENVELOPE_SCHEMA);
    }

    // --- object metadata ----------------------------------------------------

    @Override
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
                return document(STATS, "stats");
            case OPS:
                return container(OPS, "ops");
            default:
                return dynamicObject(id);
        }
    }

    private Map<String, Object> dynamicObject(String id) throws SQLException {
        String[] file = parseFileId(id);
        if (file != null) {
            FileRow f = requireFile(file[0], id);
            if (file[1] == null) {
                return fileNode(f);
            }
            return collection(id, TRANSACTIONS_LEAF, ENVELOPE_SCHEMA, buffer.countWhere(fileScope(file[0])));
        }
        if (id.startsWith(BY_TYPE + "/")) {
            String rem = id.substring((BY_TYPE + "/").length());
            int slash = rem.indexOf('/');
            if (slash < 0) {
                String ts = decodeSegment(rem);
                List<String> vers = versionsForType(ts);
                if (vers.isEmpty()) {
                    throw ProducerException.noSuchObject(id);
                }
                if (vers.size() == 1) {
                    // homogeneous by type alone — no version discriminator needed
                    return collection(id, ts, tableSchema(vers.get(0), ts), buffer.countWhere(typeScope(ts)));
                }
                return container(id, ts);   // spans guides: drill in
            }
            String ts = decodeSegment(rem.substring(0, slash));
            String gs08 = decodeSegment(rem.substring(slash + 1));
            List<String> vers = versionsForType(ts);
            if (!vers.contains(gs08) || vers.size() == 1) {
                throw ProducerException.noSuchObject(id);
            }
            return collection(id, gs08, tableSchema(gs08, ts), buffer.countWhere(typeVersionScope(ts, gs08)));
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
            if (!OPS_FUNCTIONS.contains(fn)) {
                throw ProducerException.noSuchObject(id);
            }
            return function(id, fn);
        }
        throw ProducerException.noSuchObject(id);
    }

    // --- children -----------------------------------------------------------

    @Override
    public List<Map<String, Object>> children(String id) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        switch (id) {
            case ROOT:
                out.add(object(RECEIVER));
                return out;
            case RECEIVER:
                out.add(object(FILES));
                out.add(object(TRANSACTIONS));
                out.add(object(BY_TYPE));
                out.add(object(BY_VERSION));
                out.add(object(BY_SENDER));
                out.add(object(BY_SOURCE));
                out.add(object(STATS));
                out.add(object(OPS));
                return out;
            case FILES:
                // Every files row (they are never evicted — the audit trail), newest discovery first.
                for (FileRow f : buffer.fileRows(null, Integer.MAX_VALUE, 0)) {
                    out.add(fileNode(f));
                }
                return out;
            case BY_TYPE:
                for (String ts : types()) {
                    out.add(object(BY_TYPE + "/" + encodeSegment(ts)));
                }
                return out;
            case BY_VERSION:
                for (String v : buffer.distinctValues("gs08")) {
                    out.add(object(BY_VERSION + "/" + encodeSegment(v)));
                }
                return out;
            case BY_SENDER:
                for (String s : buffer.distinctValues("sender_id")) {
                    out.add(object(BY_SENDER + "/" + encodeSegment(s)));
                }
                return out;
            case BY_SOURCE:
                for (String s : buffer.distinctValues("source_name")) {
                    out.add(object(BY_SOURCE + "/" + encodeSegment(s)));
                }
                return out;
            case OPS:
                for (String fn : OPS_FUNCTIONS) {
                    out.add(object(OPS + "/" + fn));
                }
                return out;
            default:
                return dynamicChildren(id);
        }
    }

    /**
     * Children of a dynamic container: a {@code /files/<fileId>} node lists its
     * {@code transactions} collection; a multi-guide {@code /by-type/<TS>} lists its versions.
     */
    private List<Map<String, Object>> dynamicChildren(String id) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        String[] file = parseFileId(id);
        if (file != null && file[1] == null) {
            requireFile(file[0], id);
            out.add(object(fileTransactionsId(file[0])));
            return out;
        }
        if (id.startsWith(BY_TYPE + "/")) {
            String rem = id.substring((BY_TYPE + "/").length());
            if (rem.indexOf('/') < 0) {
                String ts = decodeSegment(rem);
                List<String> vers = versionsForType(ts);
                if (vers.size() > 1) {   // only a multi-guide type is a container
                    for (String v : vers) {
                        out.add(object(BY_TYPE + "/" + encodeSegment(ts) + "/" + encodeSegment(v)));
                    }
                    return out;
                }
            }
        }
        // leaf (collection/document/function) -> no children; unknown id -> 404 via object().
        object(id);
        return out;
    }

    // --- documents ----------------------------------------------------------

    /**
     * The {@code /stats} body ({@code schema:shared:x12.receiver-stats}): poller state from
     * the live {@link PollerStatus} + buffer counters + inbox hygiene ({@code .done} files
     * still in the watched dirs, DESIGN §11.2).
     */
    @Override
    public Map<String, Object> documentData(String id) throws SQLException {
        if (!STATS.equals(id)) {
            object(id);
            throw ProducerException.unsupported("Object is not a document: " + id);
        }
        PollerStatus p = poller.get();
        if (p == null) {
            p = PollerStatus.DOWN;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("up", p.up());
        p.lastScan().ifPresent(t -> out.put("lastScan", t.toString()));
        if (p.lastConsumed().isPresent()) {
            out.put("lastConsumed", p.lastConsumed().get().toString());
        } else {
            OptionalLong last = buffer.lastConsumedMillis();
            if (last.isPresent()) {
                out.put("lastConsumed", Instant.ofEpochMilli(last.getAsLong()).toString());
            }
        }
        long newCount = buffer.count(Status.NEW);
        long inFlight = buffer.count(Status.IN_FLIGHT);
        long acked = buffer.count(Status.ACKED);
        out.put("bufferDepth", newCount + inFlight);   // un-acked, per the schema's description
        OptionalLong oldest = buffer.oldestUnackedSeconds();
        if (oldest.isPresent()) {
            out.put("oldestUnackedSec", oldest.getAsLong());
        }
        out.put("backpressure", p.backpressure());
        out.put("newCount", newCount);
        out.put("inFlightCount", inFlight);
        out.put("ackedCount", acked);
        out.put("fileCount", buffer.fileCount());

        long[] done = doneFiles(p);
        out.put("doneFileCount", done[0]);
        if (done[0] > 0) {
            out.put("oldestDoneFileAgeSec", done[1]);
        }
        out.put("walBytes", buffer.walBytes());
        out.put("dbSizeBytes", buffer.dbSizeBytes());

        List<Map<String, Object>> sources = new ArrayList<>();
        for (PollerStatus.SourceStatus s : p.sources()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", s.name());
            m.put("path", s.path());
            m.put("writable", s.writable());
            m.put("pending", s.pending());
            m.put("errored", s.errored());
            sources.add(m);
        }
        out.put("sources", sources);
        return out;
    }

    /** {@code {count, oldestAgeSec}} of {@code <consumedSuffix>} files across the watched dirs; IO problems count as none. */
    private long[] doneFiles(PollerStatus p) {
        long count = 0;
        long oldestMtime = Long.MAX_VALUE;
        for (PollerStatus.SourceStatus s : p.sources()) {
            if (s.path() == null) {
                continue;
            }
            Path dir = Path.of(s.path());
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

    // --- binary -------------------------------------------------------------

    /**
     * The bytes of a {@code /files/<fileId>} node, read from {@code files.current_path}
     * (the post-rename location, so download works after consumption). 404 {@code gone}
     * when inbox hygiene has removed the file; the transactions remain (DESIGN §2.8).
     */
    @Override
    public BinaryContent downloadBinary(String id) throws SQLException {
        String[] file = parseFileId(id);
        if (file == null || file[1] != null) {
            object(id);
            throw ProducerException.unsupported("Object is not a binary: " + id);
        }
        FileRow f = requireFile(file[0], id);
        Path current = f.currentPath() == null ? null : Path.of(f.currentPath());
        if (current == null || !Files.isRegularFile(current)) {
            throw ProducerException.fileGone(f.fileId());
        }
        try {
            return new BinaryContent(Files.readAllBytes(current), BinaryContent.MIME_X12, f.fileName());
        } catch (IOException e) {
            throw ProducerException.fileGone(f.fileId());
        }
    }

    // --- object builders ---------------------------------------------------

    private Map<String, Object> container(String id, String name) {
        Map<String, Object> o = base(id, name);
        o.put("objectClass", List.of("container"));
        return o;
    }

    private Map<String, Object> collection(String id, String name, String schemaId, long size) {
        Map<String, Object> o = base(id, name);
        o.put("objectClass", List.of("collection"));
        o.put("collectionSchema", schemaId);
        o.put("collectionSize", size);
        return o;
    }

    private Map<String, Object> document(String id, String name) {
        Map<String, Object> o = base(id, name);
        o.put("objectClass", List.of("document"));
        o.put("documentSchema", STATS_SCHEMA);
        return o;
    }

    /**
     * The {@code /files/<fileId>} node: a container (its transactions) AND a binary (its
     * bytes) with the DESIGN §2.1 binary fields; {@code fileId} is {@code <path>@<hash>},
     * {@code filePath} the raw discovery path.
     */
    private Map<String, Object> fileNode(FileRow f) {
        Map<String, Object> o = base(fileObjectId(f.fileId()), f.fileName());
        o.put("objectClass", List.of("container", "binary"));
        o.put("binarySchema", FILE_SCHEMA);
        o.put("fileId", f.fileId());
        o.put("filePath", f.filePath() == null ? FileRow.pathOf(f.fileId()) : f.filePath());
        o.put("fileName", f.fileName());
        o.put("size", f.sizeBytes());
        o.put("mimeType", BinaryContent.MIME_X12);
        o.put("checksum", f.checksum());
        if (f.fileMtime() != null) {
            o.put("modified", f.fileMtime().toString());
        }
        if (f.discoveredAt() != null) {
            o.put("created", f.discoveredAt().toString());
        }
        List<String> tags = new ArrayList<>(2);
        tags.add("source:" + f.sourceName());
        tags.add("status:" + f.status().wire());
        o.put("tags", tags);
        o.put("redeliveryCount", f.redeliveryCount());
        return o;
    }

    private Map<String, Object> function(String id, String fn) {
        Map<String, Object> o = base(id, fn);
        o.put("objectClass", List.of("function"));
        o.put("inputSchema", SchemaRegistry.functionInputId(fn));
        o.put("outputSchema", SchemaRegistry.functionOutputId(fn));
        o.put("throws", throwsFor(fn));
        return o;
    }

    /** Declared error codes per function (DESIGN §2.5), all shaped {@code schema:shared:x12.ops-error}. */
    private static Map<String, Object> throwsFor(String fn) {
        Map<String, Object> t = new LinkedHashMap<>();
        switch (fn) {
            case "take":
                t.put("lease_capacity_exceeded", SchemaRegistry.OPS_ERROR_SCHEMA);
                t.put("backpressure", SchemaRegistry.OPS_ERROR_SCHEMA);
                break;
            case "ack":
            case "release":
                t.put("lease_expired", SchemaRegistry.OPS_ERROR_SCHEMA);
                t.put("not_found", SchemaRegistry.OPS_ERROR_SCHEMA);
                break;
            case "raw":
            case "validate":
            case "rescan":
                t.put("not_found", SchemaRegistry.OPS_ERROR_SCHEMA);
                break;
            default:
                break;
        }
        return t;
    }

    private static Map<String, Object> base(String id, String name) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", id);
        o.put("name", name);
        return o;
    }

    /** Single-quote-escaped SQL string literal (scope values are buffer-derived or decoded ids). */
    private static String sql(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}

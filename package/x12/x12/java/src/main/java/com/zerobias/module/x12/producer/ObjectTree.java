package com.zerobias.module.x12.producer;

import com.zerobias.module.x12.SourceConfig;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.FileRow;
import com.zerobias.module.x12.buffer.Status;
import com.zerobias.module.x12.health.PollerStatus;
import com.zerobias.module.x12.producer.mapping.EntityMapping;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 *    ├─ /inbox                   container → the live source directories ({@link InboxFiles})
 *    ├─ /transactions            collection (all rows, envelope schema)
 *    ├─ /by-type                 container → /by-type/&lt;TS&gt; container → /by-type/&lt;TS&gt;/&lt;GS08&gt;
 *    │                                       collection (the guide's table schema)
 *    ├─ /by-version              container → /by-version/&lt;GS08&gt;     collection (envelope)
 *    ├─ /by-sender               container → /by-sender/&lt;ISA06&gt;     collection (envelope)
 *    ├─ /by-source               container → /by-source/&lt;sourceName&gt; collection (envelope)
 *    ├─ /&lt;business&gt;             collection per business entity (DESIGN §8.5), e.g. /claims
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
 * <p><b>Ids are stable.</b> A node's class never depends on what else is in the buffer:
 * {@code /by-type/<TS>} is a container even while one guide carries that type, so the id a
 * caller saved as a collection does not turn into a container the day a second GS08 of the
 * same type arrives.
 *
 * <p><b>Id encoding.</b> {@code fileId} ({@code <absolute path>@<hash>}) contains {@code /},
 * and a sender id or source name may too. Discriminator values are embedded in object ids
 * percent-encoded for {@code /} and {@code %} only ({@link #encodeSegment}) so every id
 * round-trips through {@code getObject}/{@code getChildren}/{@code getCollectionElements}
 * verbatim; an un-encoded value without {@code %} decodes to itself.
 */
public final class ObjectTree {

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
    static final List<String> OPS_FUNCTIONS = SchemaRegistry.OPS_FUNCTIONS;

    /**
     * A collection's buffer scope (a WHERE fragment over {@code transactions}; null = all
     * rows) + its element schema id (DESIGN §2.1 homogeneity rule).
     */
    public record Collection(String id, String scopeWhere, String schemaId) {
    }

    /** One page of children plus the total. */
    public record ChildPage(List<Map<String, Object>> items, long total) {
    }

    private final BufferStore buffer;
    private final SchemaRegistry schemas;
    private final PollerStatus poller;
    private final String consumedSuffix;
    private final InboxFiles inbox;
    private final BusinessEntities business;

    /**
     * @param schemas        picks a guide-bound {@code schema:table} for a {@code /by-type}
     *                       collection (the envelope when the guide is unbundled), and receives the
     *                       business schemas generated from the mappings — a collection may not
     *                       advertise a schema the registry cannot serve
     * @param poller         feeds {@code /stats}
     * @param consumedSuffix the {@code .done} suffix, for the {@code /stats} inbox-hygiene counters
     * @param sources        the configured inbox directories, which become the live
     *                       {@code /inbox/<source>} branch ({@link InboxFiles})
     * @param errorSuffix    the {@code .error} suffix, for the {@code ingest} field on live file nodes
     */
    public ObjectTree(BufferStore buffer, SchemaRegistry schemas, PollerStatus poller,
            String consumedSuffix, List<SourceConfig> sources, String errorSuffix) {
        this.buffer = Objects.requireNonNull(buffer, "buffer");
        this.schemas = Objects.requireNonNull(schemas, "schemas");
        this.poller = Objects.requireNonNull(poller, "poller");
        this.consumedSuffix = Objects.requireNonNull(consumedSuffix, "consumedSuffix");
        this.inbox = new InboxFiles(Objects.requireNonNull(sources, "sources"), consumedSuffix,
            Objects.requireNonNull(errorSuffix, "errorSuffix"));
        final List<EntityMapping> mappings = BusinessEntities.mappingsFor(
            PackCatalog.fromClasspath().guides());
        this.business = new BusinessEntities(buffer, mappings);
        this.schemas.addMappingSchemas(mappings);
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

    /**
     * Resolve a collection id to its buffer scope, or throw: {@code noSuchObjectError} for an
     * unknown discriminator value, {@code UnsupportedOperationError} if the id is not a
     * collection. (§2.1, §2.6)
     */
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
                if (versionsForType(decodeSegment(rem)).isEmpty()) {
                    throw ProducerException.noSuchObject(id);
                }
                throw ProducerException.unsupported("Object is not a collection (drill into a version): " + id);
            }
            String ts = decodeSegment(rem.substring(0, slash));
            String gs08 = decodeSegment(rem.substring(slash + 1));
            if (!versionsForType(ts).contains(gs08)) {
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
                return document(STATS, "stats");
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
        final Map<String, Object> businessNode = businessObject(id);
        if (businessNode != null) {
            return businessNode;
        }
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
                if (versionsForType(ts).isEmpty()) {
                    throw ProducerException.noSuchObject(id);
                }
                return container(id, ts);
            }
            String ts = decodeSegment(rem.substring(0, slash));
            String gs08 = decodeSegment(rem.substring(slash + 1));
            if (!versionsForType(ts).contains(gs08)) {
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

    /** Direct children of {@code id} (emergent from the buffer's DISTINCT values), or throw {@code noSuchObjectError}. (§2.1) */
    public List<Map<String, Object>> children(String id) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        switch (id) {
            case ROOT:
                out.add(object(RECEIVER));
                return out;
            case RECEIVER:
                out.add(object(FILES));
                out.add(object(InboxFiles.INBOX));
                out.add(object(TRANSACTIONS));
                out.add(object(BY_TYPE));
                out.add(object(BY_VERSION));
                out.add(object(BY_SENDER));
                out.add(object(BY_SOURCE));
                for (String collection : business.collections()) {
                    out.add(object(RECEIVER + "/" + collection));
                }
                out.add(object(STATS));
                out.add(object(OPS));
                return out;
            case FILES:
                return childPage(FILES, Integer.MAX_VALUE, 0).items();
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
     * One page of {@code id}'s children and the total. {@code /files} is paged in SQL
     * ({@code LIMIT/OFFSET} + {@code count(*)}, newest discovery first): it is every files row
     * ever recorded — never evicted, the audit trail — so reading it whole to serve one page
     * would grow without limit. Every other branch is bounded (a fixed list, DISTINCT values,
     * one readdir) and is paged in memory.
     */
    public ChildPage childPage(String id, int limit, int offset) throws SQLException {
        if (FILES.equals(id)) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (FileRow f : buffer.fileRows(null, limit, offset)) {
                items.add(fileNode(f));
            }
            return new ChildPage(items, buffer.countFilesWhere(null));
        }
        List<Map<String, Object>> all = children(id);
        List<Map<String, Object>> page = offset >= all.size()
            ? List.of()
            : all.subList(offset, (int) Math.min(all.size(), (long) offset + limit));
        return new ChildPage(page, all.size());
    }

    /**
     * Children of a dynamic container: a {@code /files/<fileId>} node lists its
     * {@code transactions} collection; a {@code /by-type/<TS>} lists one collection per GS08.
     */
    private List<Map<String, Object>> dynamicChildren(String id) throws SQLException {
        if (InboxFiles.owns(id)) {
            return inbox.children(id);   // a fresh readdir; never cached (DESIGN §2.9)
        }
        final List<Map<String, Object>> businessKids = businessChildren(id);
        if (businessKids != null) {
            return businessKids;
        }
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
                for (String v : versionsForType(ts)) {
                    out.add(object(BY_TYPE + "/" + encodeSegment(ts) + "/" + encodeSegment(v)));
                }
                if (!out.isEmpty()) {
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
     * The body of a document node: {@code /stats}, or a {@code /files/<fileId>} node's
     * {@code files} row ({@code schema:shared:x12.file}). Throws {@code noSuchObjectError} for
     * an unknown id and {@code UnsupportedOperationError} for a node that is not a document.
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
     * still in the watched dirs, DESIGN §9).
     */
    private Map<String, Object> stats() throws SQLException {
        PollerStatus p = poller;
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
        out.put("bufferDepth", newCount + inFlight);   // un-acked, as on /healthz and the metadata
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
     * The bytes of a {@code /files/<fileId>} node, read from {@code files.current_path}
     * (the post-rename location, so download works after consumption). 404 {@code gone}
     * when inbox hygiene has removed the file; the transactions remain (DESIGN §2.8).
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
        Path current = f.currentPath() == null ? null : Path.of(f.currentPath());
        if (current == null) {
            throw ProducerException.fileGone(f.fileId());
        }
        // Stat'ed without following links, like the open: a symlink left at the consumed path
        // is not the file the receiver hashed, so it is "gone" rather than followed.
        final java.nio.file.attribute.BasicFileAttributes attrs;
        try {
            attrs = Files.readAttributes(current, java.nio.file.attribute.BasicFileAttributes.class,
                java.nio.file.LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            throw ProducerException.fileGone(f.fileId());
        }
        if (!attrs.isRegularFile()) {
            throw ProducerException.fileGone(f.fileId());
        }
        return new BinaryContent(id, current, attrs.size(), BinaryContent.MIME_X12, f.fileName());
    }

    // --- business entities (DESIGN §8.5) ------------------------------------

    /**
     * A parsed business id: the collection, and optionally the segment it is scoped by.
     * {@code /claims} → all; {@code /claims/by-payerName} → a container of payers;
     * {@code /claims/by-payerName/EXAMPLE HEALTH PLAN} → that payer's claims.
     */
    private record BusinessId(String collection, String segment, String value) {
    }

    private BusinessId parseBusiness(String id) {
        if (id == null || !id.startsWith(RECEIVER + "/")) {
            return null;
        }
        final String rem = id.substring((RECEIVER + "/").length());
        final String[] parts = rem.split("/", 3);
        if (business.mapping(parts[0]) == null) {
            return null;
        }
        if (parts.length == 1) {
            return new BusinessId(parts[0], null, null);
        }
        if (!parts[1].startsWith(BusinessEntities.BY)) {
            return null;
        }
        final String segment = parts[1].substring(BusinessEntities.BY.length());
        if (!business.segments(parts[0]).contains(segment)) {
            return null;
        }
        return new BusinessId(parts[0], segment,
            parts.length == 3 ? decodeSegment(parts[2]) : null);
    }

    private Map<String, Object> businessObject(String id) throws SQLException {
        final BusinessId b = parseBusiness(id);
        if (b == null) {
            return null;
        }
        final EntityMapping m = business.mapping(b.collection());
        if (b.segment() == null) {
            return collection(id, b.collection(), m.schemaId(), business.page(
                new BusinessEntities.Scope(m, null, null, null), null, 1, 1).total());
        }
        if (b.value() == null) {
            return container(id, BusinessEntities.BY + b.segment());
        }
        if (!business.segmentValues(b.collection(), b.segment()).contains(b.value())) {
            throw ProducerException.noSuchObject(id);
        }
        return collection(id, b.value(), m.schemaId(), business.page(scopeOf(m, b), null, 1, 1).total());
    }

    private List<Map<String, Object>> businessChildren(String id) throws SQLException {
        final BusinessId b = parseBusiness(id);
        if (b == null) {
            return null;
        }
        final List<Map<String, Object>> out = new ArrayList<>();
        if (b.segment() == null) {
            // a business collection is also a container of its segments
            for (String segment : business.segments(b.collection())) {
                out.add(object(RECEIVER + "/" + b.collection() + "/" + BusinessEntities.BY + segment));
            }
            return out;
        }
        if (b.value() == null) {
            for (String value : business.segmentValues(b.collection(), b.segment())) {
                out.add(object(RECEIVER + "/" + b.collection() + "/" + BusinessEntities.BY
                    + b.segment() + "/" + encodeSegment(value)));
            }
            return out;
        }
        businessObject(id);   // 404 an unknown value
        return out;           // a scoped collection is a leaf
    }

    /** The scope a business id resolves to: grain plus file or dimension equality. */
    private static BusinessEntities.Scope scopeOf(EntityMapping m, BusinessId b) {
        if (b.segment() == null || b.value() == null) {
            return new BusinessEntities.Scope(m, null, null, null);
        }
        return BusinessEntities.FILE_SEGMENT.equals(b.segment())
            ? new BusinessEntities.Scope(m, b.value(), null, null)
            : new BusinessEntities.Scope(m, null, b.segment(), b.value());
    }

    /** Resolve a business collection id to its scope, or null when the id is not one. */
    BusinessEntities.Scope businessScope(String id) throws SQLException {
        final BusinessId b = parseBusiness(id);
        if (b == null || (b.segment() != null && b.value() == null)) {
            return null;
        }
        final EntityMapping m = business.mapping(b.collection());
        if (b.value() != null && !business.segmentValues(b.collection(), b.segment()).contains(b.value())) {
            throw ProducerException.noSuchObject(id);
        }
        return scopeOf(m, b);
    }

    public BusinessEntities business() {
        return business;
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

    /**
     * {@code deleteObject}: unlink a live file or remove an empty live directory. The emergent
     * branches cannot be deleted — they are projections of the buffer, and {@code ops/purge} is
     * how buffer rows leave.
     */
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
     * The {@code /files/<fileId>} node: a container (its transactions), a document (its
     * {@code files} row, {@link #fileDocument}) and a binary (its bytes). It carries only
     * {@code DataProducerObject} fields: the interface has no {@code binarySchema}, so the
     * row's own fields — fileId, paths, status, counts — are the document, read with
     * {@code getDocumentData}.
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

    private Map<String, Object> function(String id, String fn) {
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
    private static String sql(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}

package com.zerobias.module.x12.producer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.FileRow;
import com.zerobias.module.x12.buffer.TransactionRow;
import com.zerobias.module.x12.filter.X12Filter;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements the DataProducer operations (DESIGN §2) over the durable buffer. All
 * methods return JSON strings (the HTTP layer passes them through verbatim) and raise
 * {@link ProducerException} for the standard error cases (DESIGN §2.7).
 *
 * <p>Foundation SKELETON: this class owns the two things every later phase depends
 * on — the mandatory {@link #pagedResults PagedResults} envelope and the
 * {@link #toElement envelope overlay} (DESIGN §5) — and delegates the tree, schemas
 * and functions to {@link ObjectTreeApi}, {@link SchemaRegistryApi} and
 * {@link OperationsApi}. The producer phase supplies {@code ObjectTree},
 * {@code SchemaRegistry} and {@code X12Operations}; until then the defaults serve the
 * root and 404 everything else.
 *
 * <p>The producer is <b>receive-only for data</b>: transactions arrive as inbox files, so
 * every collection/document mutation is rejected with {@code UnsupportedOperationError},
 * and draining is {@code ops/take}, not element mutation. The one exception is file
 * management over the volume itself — {@code uploadBinaryContent},
 * {@code createChildObject} (mkdir) and {@code deleteObject} under the live
 * {@code /inbox} branch — which is off unless the deployment sets
 * {@code config.allowFileManagement=true} (DESIGN §2.9).
 */
public final class X12ProducerFacade {

    private static final Gson GSON = new Gson();
    /** Function output keeps null-valued required fields (e.g. take's leaseId on an empty lease). */
    private static final Gson GSON_NULLS = new GsonBuilder().serializeNulls().create();
    private static final int MAX_PAGE_SIZE = 1000;

    private final BufferStore buffer;
    private final ObjectTreeApi tree;
    private final SchemaRegistryApi schemas;
    private final OperationsApi ops;
    private final boolean allowFileManagement;

    public X12ProducerFacade(BufferStore buffer, ObjectTreeApi tree, SchemaRegistryApi schemas,
            OperationsApi ops) {
        this(buffer, tree, schemas, ops, false);
    }

    /**
     * @param allowFileManagement {@code config.allowFileManagement} — the single gate on the
     *                            file-management write surface ({@code uploadBinaryContent},
     *                            {@code createChildObject}, {@code deleteObject} under
     *                            {@code /inbox}). Default false: production receivers take
     *                            files from the feed, not from API callers (DESIGN §2.9).
     */
    public X12ProducerFacade(BufferStore buffer, ObjectTreeApi tree, SchemaRegistryApi schemas,
            OperationsApi ops, boolean allowFileManagement) {
        this.buffer = buffer;
        this.tree = tree == null ? ObjectTreeApi.ROOT_ONLY : tree;
        this.schemas = schemas == null ? SchemaRegistryApi.EMPTY : schemas;
        this.ops = ops == null ? OperationsApi.NONE : ops;
        this.allowFileManagement = allowFileManagement;
        X12Filter.register();
    }

    /** The foundation skeleton: root only, no schemas, no functions. */
    public static X12ProducerFacade skeleton(BufferStore buffer) {
        return new X12ProducerFacade(buffer, ObjectTreeApi.ROOT_ONLY, SchemaRegistryApi.EMPTY, OperationsApi.NONE);
    }

    /** Whether the file-management write surface is enabled ({@code isSupported} reads this). */
    public boolean fileManagementEnabled() {
        return allowFileManagement;
    }

    public ObjectTreeApi tree() {
        return tree;
    }

    public SchemaRegistryApi schemas() {
        return schemas;
    }

    public OperationsApi ops() {
        return ops;
    }

    // --- Objects -----------------------------------------------------------

    public String getRootObject() throws SQLException {
        return GSON.toJson(tree.object(ObjectTreeApi.ROOT));
    }

    public String getObject(String objectId) throws SQLException {
        requireId(objectId);
        return GSON.toJson(tree.object(objectId));
    }

    public String getChildren(String objectId, int pageSize, int pageNumber) throws SQLException {
        requireId(objectId);
        List<Map<String, Object>> children = tree.children(objectId);
        int size = clampPageSize(pageSize);
        int from = Math.max(0, pageNumber - 1) * size;
        int total = children.size();
        List<Map<String, Object>> page = from >= total
            ? List.of()
            : children.subList(from, Math.min(total, from + size));
        return pagedResults(page, total, size, pageNumber);
    }

    // --- Collections (read-only browse) -----------------------------------

    public String getCollectionElements(String objectId, String filter, String sortBy,
            String sortDir, int pageSize, int pageNumber, String pageToken) throws SQLException {
        requireId(objectId);
        // A business collection projects rows out of the object graph (DESIGN §8.5) rather than
        // reading transaction rows, so it resolves before the buffer-backed path.
        if (tree instanceof ObjectTree) {
            final BusinessEntities.Scope scope = ((ObjectTree) tree).businessScope(objectId);
            if (scope != null) {
                final int size = clampPageSize(pageSize);
                final BusinessEntities.Page page = ((ObjectTree) tree).business()
                    .page(scope, businessFilter(scope, filter), size, pageNumber);
                return pagedResults(page.rows(), page.total(), size, pageNumber);
            }
        }
        ObjectTreeApi.Collection coll = tree.resolveCollection(objectId);
        int size = clampPageSize(pageSize);
        int offset = Math.max(0, pageNumber - 1) * size;
        String where = composeWhere(coll, filter);

        List<TransactionRow> rows = buffer.search(where, size, offset);
        // One batched graph read for the page, not one per row (DESIGN §8.4).
        final Map<String, Map<String, Object>> bodies = buffer.documentsFor(
            rows.stream().map(TransactionRow::elementKey).toList());
        List<Map<String, Object>> elements = new ArrayList<>(rows.size());
        for (TransactionRow r : rows) {
            elements.add(toElement(r, bodies.get(r.elementKey())));
        }
        long total = buffer.countWhere(where);
        return pagedResults(elements, total, size, pageNumber);
    }

    public String getCollectionElement(String objectId, String elementKey) throws SQLException {
        requireId(objectId);
        if (elementKey == null || elementKey.isBlank()) {
            throw ProducerException.illegalArgument("elementKey is required");
        }
        ObjectTreeApi.Collection coll = tree.resolveCollection(objectId);
        String where = and(coll.scopeWhere(), "element_key = " + sql(elementKey));
        List<TransactionRow> rows = buffer.search(where, 1, 0);
        if (rows.isEmpty()) {
            throw ProducerException.noSuchObject(objectId + " / " + elementKey);
        }
        return GSON.toJson(toElement(rows.get(0), buffer.documentFor(rows.get(0).elementKey())));
    }

    // --- Schemas -----------------------------------------------------------

    public String getSchema(String schemaId) {
        if (schemaId == null || schemaId.isBlank()) {
            throw ProducerException.illegalArgument("schemaId is required");
        }
        return schemas.getSchema(schemaId);
    }

    // --- Documents / binary ------------------------------------------------

    public String getDocumentData(String objectId) throws SQLException {
        requireId(objectId);
        return GSON.toJson(tree.documentData(objectId));
    }

    /** The bytes of a {@code /files/<fileId>} node (DESIGN §2.8); served raw by the HTTP layer. */
    public BinaryContent downloadBinary(String objectId) throws SQLException {
        requireId(objectId);
        return tree.downloadBinary(objectId);
    }

    /**
     * Compile an RFC4515 filter into a predicate over projected business rows. Business columns
     * can sit behind a qualifier predicate or inside a composite, which the value table cannot
     * express as SQL, so the comparison happens on the projected row — bounded by the segment,
     * never the whole buffer (DESIGN §8.5.1). Unknown column names are a 400, not an empty page:
     * a typo in a filter should say so.
     */
    private java.util.function.Predicate<Map<String, Object>> businessFilter(
            BusinessEntities.Scope scope, String filter) {
        if (filter == null || filter.isBlank()) {
            return null;
        }
        try {
            return BusinessFilter.compile(scope.mapping(), filter);
        } catch (IllegalArgumentException bad) {
            throw ProducerException.illegalArgument("Malformed filter: " + bad.getMessage());
        }
    }

    // --- File management: gated by config.allowFileManagement (DESIGN §2.9) --

    /**
     * {@code uploadBinaryContent} — write {@code bytes} as {@code fileName} into the live
     * {@code /inbox} container {@code objectId}. Returns the new file's object metadata
     * (the interface's {@code 201} body).
     */
    public String uploadBinary(String objectId, String fileName, byte[] bytes) throws SQLException {
        requireId(objectId);
        requireFileManagement("uploadBinaryContent");
        return GSON.toJson(tree.uploadBinary(objectId, fileName, bytes));
    }

    /**
     * {@code createChildObject} — mkdir under a live {@code /inbox} container. Only
     * containers can be created: there is no way to conjure a file without bytes, and
     * bytes arrive through {@code uploadBinaryContent}.
     */
    public String createChildObject(String objectId, String name, List<String> objectClass)
            throws SQLException {
        requireId(objectId);
        requireFileManagement("createChildObject");
        if (objectClass != null && !objectClass.isEmpty() && !objectClass.contains("container")) {
            throw ProducerException.illegalArgument(
                "Only container children can be created (a directory); got objectClass=" + objectClass);
        }
        return GSON.toJson(tree.createChildContainer(objectId, name));
    }

    /** {@code deleteObject} — unlink a live file or remove an empty live directory. */
    public String deleteObject(String objectId) throws SQLException {
        requireId(objectId);
        requireFileManagement("deleteObject");
        tree.deleteObject(objectId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "deleted");
        body.put("id", objectId);
        return GSON.toJson(body);
    }

    private void requireFileManagement(String operationId) {
        if (!allowFileManagement) {
            throw ProducerException.unsupported(operationId
                + " is disabled: the receiver is receive-only unless the deployment sets "
                + "config.allowFileManagement=true");
        }
    }

    // --- Write surface: rejected (receive-only) ---------------------------

    public String createCollectionElement(String objectId, String elementJson) {
        throw ProducerException.unsupported(
            "Collection is receive-only; transactions arrive as inbox files, not via addCollectionElement");
    }

    public String updateCollectionElement(String objectId, String elementKey, String elementJson) {
        throw ProducerException.unsupported("Collection is read-only");
    }

    public void deleteCollectionElement(String objectId, String elementKey) {
        throw ProducerException.unsupported("Collection is read-only; use ops/purge to evict acked rows");
    }

    // --- Functions: ops/* (DESIGN §2.5) ------------------------------------

    public String invokeFunction(String objectId, String inputJson) throws SQLException {
        requireId(objectId);
        Map<String, Object> obj = tree.object(objectId); // 404 if unknown
        @SuppressWarnings("unchecked")
        List<String> classes = (List<String>) obj.get("objectClass");
        if (classes == null || !classes.contains("function")) {
            throw ProducerException.unsupported("Object is not a function: " + objectId);
        }
        String fn = objectId.substring(objectId.lastIndexOf('/') + 1);

        @SuppressWarnings("unchecked")
        Map<String, Object> input = (inputJson == null || inputJson.isBlank())
            ? Map.of()
            : GSON.fromJson(inputJson, Map.class);
        return GSON_NULLS.toJson(ops.invoke(fn, input == null ? Map.of() : input));
    }

    // --- element mapping (DESIGN §5 envelope overlay) ----------------------

    /**
     * Build a collection element from a row and the document reassembled from its object graph
     * overlaid with the authoritative envelope (DESIGN §5): {@code elementKey, fileId,
     * fileName, sourceName, isaControlNumber, gsControlNumber, stControlNumber, gs08,
     * transactionType, senderId, receiverId, interchangeDate, receivedAt, status, leaseId,
     * envelope, parserErrorCount}. Static so {@code X12Operations} can use it as its
     * element mapper without a facade reference.
     */
    public static Map<String, Object> toElement(TransactionRow r, Map<String, Object> body) {
        Map<String, Object> element = new LinkedHashMap<>();
        if (body != null) {
            element.putAll(body);
        }
        element.put("elementKey", r.elementKey());
        element.put("fileId", r.fileId());
        element.put("fileName", fileName(r.fileId()));
        element.put("sourceName", r.sourceName());
        element.put("isaControlNumber", r.isaControl());
        element.put("gsControlNumber", r.gsControl());
        element.put("stControlNumber", r.stControl());
        element.put("gs08", r.gs08());
        element.put("transactionType", r.transactionType());
        element.put("senderId", r.senderId());
        element.put("receiverId", r.receiverId());
        element.put("interchangeDate", r.interchangeAt() == null ? null : r.interchangeAt().toString());
        element.put("receivedAt", r.receivedAt() == null ? null : r.receivedAt().toString());
        element.put("status", r.status() == null ? null : r.status().wire());
        element.put("leaseId", r.leaseId());
        element.put("envelope", r.envelope());
        element.put("parserErrorCount", r.parserErrorCount());
        return element;
    }

    private static String fileName(String fileId) {
        return FileRow.fileNameOf(fileId);
    }

    // --- helpers -----------------------------------------------------------

    private String composeWhere(ObjectTreeApi.Collection coll, String filter) {
        String userFilter = null;
        if (filter != null && !filter.isBlank()) {
            try {
                userFilter = X12Filter.toWhereClause(filter);
            } catch (RuntimeException e) {
                throw ProducerException.illegalArgument("Malformed filter: " + e.getMessage());
            }
        }
        return and(coll.scopeWhere(), userFilter);
    }

    static String and(String a, String b) {
        if (a == null || a.isBlank()) {
            return b;
        }
        if (b == null || b.isBlank()) {
            return a;
        }
        return "(" + a + ") AND (" + b + ")";
    }

    /**
     * The DataProducer {@code PagedResults} envelope. Paginated operations
     * ({@code getChildren}/{@code searchChildObjects},
     * {@code getCollectionElements}/{@code searchCollectionElements}) MUST return this
     * shape — the platform's generated producer client deserializes the RPC body into a
     * paged bag and raises "Producers must return 'items' for PagedResults queries" when
     * {@code items} is absent. A bare array (the OpenAPI response schema, but not the
     * runtime contract) breaks the data-explorer tree. {@code count} is the total
     * matching rows, not the page; {@code pageNumber} is 1-based on the wire.
     */
    public static String pagedResults(List<Map<String, Object>> items, long count,
            int pageSize, int pageNumber) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("items", items);
        envelope.put("count", count);
        envelope.put("pageSize", pageSize);
        envelope.put("pageNumber", pageNumber);
        return GSON.toJson(envelope);
    }

    private int clampPageSize(int pageSize) {
        if (pageSize <= 0) {
            return 100;
        }
        if (pageSize > MAX_PAGE_SIZE) {
            throw ProducerException.illegalArgument("pageSize exceeds maximum of " + MAX_PAGE_SIZE);
        }
        return pageSize;
    }

    private static void requireId(String objectId) {
        if (objectId == null || objectId.isBlank()) {
            throw ProducerException.illegalArgument("objectId is required");
        }
    }

    private static String sql(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}

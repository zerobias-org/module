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
 * <p>The producer is <b>receive-only</b>: files arrive in the inbox, never through the
 * DataProducer write surface — every mutating op is rejected with
 * {@code UnsupportedOperationError}. Draining is {@code ops/take}, not element mutation.
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

    public X12ProducerFacade(BufferStore buffer, ObjectTreeApi tree, SchemaRegistryApi schemas,
            OperationsApi ops) {
        this.buffer = buffer;
        this.tree = tree == null ? ObjectTreeApi.ROOT_ONLY : tree;
        this.schemas = schemas == null ? SchemaRegistryApi.EMPTY : schemas;
        this.ops = ops == null ? OperationsApi.NONE : ops;
        X12Filter.register();
    }

    /** The foundation skeleton: root only, no schemas, no functions. */
    public static X12ProducerFacade skeleton(BufferStore buffer) {
        return new X12ProducerFacade(buffer, ObjectTreeApi.ROOT_ONLY, SchemaRegistryApi.EMPTY, OperationsApi.NONE);
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
        ObjectTreeApi.Collection coll = tree.resolveCollection(objectId);
        int size = clampPageSize(pageSize);
        int offset = Math.max(0, pageNumber - 1) * size;
        String where = composeWhere(coll, filter);

        List<TransactionRow> rows = buffer.search(where, size, offset);
        List<Map<String, Object>> elements = new ArrayList<>(rows.size());
        for (TransactionRow r : rows) {
            elements.add(toElement(r));
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
        return GSON.toJson(toElement(rows.get(0)));
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
     * Build a collection element from a row: the typed body ({@code mapped_json})
     * overlaid with the authoritative envelope (DESIGN §5): {@code elementKey, fileId,
     * fileName, sourceName, isaControlNumber, gsControlNumber, stControlNumber, gs08,
     * transactionType, senderId, receiverId, interchangeDate, receivedAt, status, leaseId,
     * envelope, parserErrorCount}. Static so {@code X12Operations} can use it as its
     * element mapper without a facade reference.
     */
    public static Map<String, Object> toElement(TransactionRow r) {
        Map<String, Object> element = new LinkedHashMap<>();
        JsonObject body = r.mappedJson() == null ? null : GSON.fromJson(r.mappedJson(), JsonObject.class);
        if (body != null) {
            for (String k : body.keySet()) {
                element.put(k, GSON.fromJson(body.get(k), Object.class));
            }
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

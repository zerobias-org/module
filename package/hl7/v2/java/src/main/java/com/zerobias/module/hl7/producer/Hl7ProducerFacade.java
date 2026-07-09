package com.zerobias.module.hl7.producer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.zerobias.module.hl7.buffer.BufferRow;
import com.zerobias.module.hl7.buffer.BufferStore;
import com.zerobias.module.hl7.filter.Hl7Filter;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements the read-only DataProducer operations (DESIGN §2) over the durable
 * buffer. All methods return JSON strings (the HTTP layer passes them through
 * verbatim) and raise {@link ProducerException} for the standard error cases.
 *
 * <p>The producer is <b>receive-only</b>: messages arrive over MLLP (Phase 4),
 * never through the DataProducer write surface — so every mutating op
 * ({@code addCollectionElement}, update/delete) is rejected with
 * {@code UnsupportedOperationError} (DESIGN §2.7). Draining is the {@code ops/take}
 * function (Phase 7), not element mutation.
 */
public final class Hl7ProducerFacade {

    private static final Gson GSON = new Gson();
    /** Function output keeps null-valued required fields (e.g. take's leaseId on an empty lease). */
    private static final Gson GSON_NULLS = new GsonBuilder().serializeNulls().create();
    private static final int MAX_PAGE_SIZE = 1000;

    private final BufferStore buffer;
    private final ObjectTree tree;
    private final SchemaRegistry schemas;
    private final Hl7Operations ops;

    public Hl7ProducerFacade(BufferStore buffer, ObjectTree tree, SchemaRegistry schemas) {
        this(buffer, tree, schemas, null);
    }

    public Hl7ProducerFacade(BufferStore buffer, ObjectTree tree, SchemaRegistry schemas,
            Recaster recaster) {
        this.buffer = buffer;
        this.tree = tree;
        this.schemas = schemas;
        this.ops = new Hl7Operations(buffer, this::toElement, recaster, new SchemaValidator(schemas));
        Hl7Filter.register();
    }

    // --- Objects -----------------------------------------------------------

    public String getRootObject() throws SQLException {
        return GSON.toJson(tree.object(ObjectTree.ROOT));
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
        ObjectTree.Collection coll = tree.resolveCollection(objectId);
        int size = clampPageSize(pageSize);
        int offset = Math.max(0, pageNumber - 1) * size;
        String where = composeWhere(coll, filter);

        List<BufferRow> rows = buffer.search(where, size, offset);
        List<Map<String, Object>> elements = new ArrayList<>(rows.size());
        for (BufferRow r : rows) {
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
        ObjectTree.Collection coll = tree.resolveCollection(objectId);
        String where = and(coll.scopeWhere(), "control_id = " + sql(elementKey));
        List<BufferRow> rows = buffer.search(where, 1, 0);
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

    // --- Write surface: rejected (receive-only) ---------------------------

    public String createCollectionElement(String objectId, String elementJson) {
        throw ProducerException.unsupported(
            "Collection is receive-only; messages arrive over MLLP, not via addCollectionElement");
    }

    public String updateCollectionElement(String objectId, String elementKey, String elementJson) {
        throw ProducerException.unsupported("Collection is read-only");
    }

    public void deleteCollectionElement(String objectId, String elementKey) {
        throw ProducerException.unsupported("Collection is read-only; use ops/purge to evict acked rows");
    }

    // --- Functions: ops/take|ack|release|replay|recast|purge (DESIGN §2.5) -------

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

    // --- helpers -----------------------------------------------------------

    /** Build a buffer element from a row: typed body overlaid with authoritative envelope. */
    private Map<String, Object> toElement(BufferRow r) {
        Map<String, Object> element = new LinkedHashMap<>();
        JsonObject body = GSON.fromJson(r.mappedJson(), JsonObject.class);
        if (body != null) {
            for (String k : body.keySet()) {
                element.put(k, GSON.fromJson(body.get(k), Object.class));
            }
        }
        // Envelope columns are authoritative (DESIGN §2.3 shared schema).
        element.put("controlId", r.controlId());
        element.put("receivedAt", r.receivedAt().toString());
        element.put("sourcePort", r.sourcePort());
        element.put("status", r.status().wire());
        element.put("leaseId", r.leaseId());
        return element;
    }

    private String composeWhere(ObjectTree.Collection coll, String filter) {
        String userFilter = null;
        if (filter != null && !filter.isBlank()) {
            try {
                userFilter = Hl7Filter.toWhereClause(filter);
            } catch (RuntimeException e) {
                throw ProducerException.illegalArgument("Malformed filter: " + e.getMessage());
            }
        }
        return and(coll.scopeWhere(), userFilter);
    }

    private static String and(String a, String b) {
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
     * {@code getCollectionElements}/{@code searchCollectionElements}) MUST return
     * this shape — the platform's generated producer client deserializes the RPC
     * body into a paged bag and raises "Producers must return 'items' for
     * PagedResults queries" when {@code items} is absent. A bare array (the
     * OpenAPI response schema, but not the runtime contract) breaks the
     * data-explorer tree. {@code count} is the total matching rows, not the page.
     */
    private static String pagedResults(List<Map<String, Object>> items, long count,
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

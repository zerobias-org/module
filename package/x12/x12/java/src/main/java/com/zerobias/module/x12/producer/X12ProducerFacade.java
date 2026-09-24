package com.zerobias.module.x12.producer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.FileRow;
import com.zerobias.module.x12.buffer.TransactionRow;
import com.zerobias.module.x12.filter.X12Filter;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Implements the DataProducer read operations (DESIGN §2) over the durable buffer: the
 * {@link ObjectTree} for objects and documents, the {@link SchemaRegistry} for schemas,
 * {@link X12Operations} for the {@code /ops/*} functions. All methods return JSON strings
 * (the HTTP layer passes them through verbatim) and raise {@link ProducerException} for
 * the standard error cases (DESIGN §2.7).
 *
 * <p>The producer is <b>receive-only for data</b>: transactions arrive as inbox files, so
 * {@link OperationRouter} rejects every collection/document/object mutation with
 * {@code UnsupportedOperationError}, and draining is {@code ops/take}, not element mutation.
 * The one exception is file management over the volume itself —
 * {@code uploadBinaryContent}, {@code createChildObject} (mkdir) and {@code deleteObject}
 * under the live {@code /inbox} branch — which is refused unless the deployment sets
 * {@code config.allowFileManagement=true} (DESIGN §2.9).
 */
public final class X12ProducerFacade {

    static final int DEFAULT_PAGE_SIZE = 100;
    static final int MAX_PAGE_SIZE = 1000;

    private static final Gson GSON = new Gson();
    /** Function output keeps null-valued required fields (e.g. take's leaseId on an empty lease). */
    private static final Gson GSON_NULLS = new GsonBuilder().serializeNulls().create();
    private static final Set<String> VALIDATE_REQUEST_KEYS = Set.of("input", "strict");

    private final BufferStore buffer;
    private final ObjectTree tree;
    private final SchemaRegistry schemas;
    private final X12Operations ops;
    private final boolean allowFileManagement;

    /** A receive-only producer: the file-management write surface is shut. */
    public X12ProducerFacade(BufferStore buffer, ObjectTree tree, SchemaRegistry schemas, X12Operations ops) {
        this(buffer, tree, schemas, ops, false);
    }

    /**
     * @param allowFileManagement {@code config.allowFileManagement} — the single gate on the
     *                            file-management write surface ({@code uploadBinaryContent},
     *                            {@code createChildObject}, {@code deleteObject} under
     *                            {@code /inbox}). Production receivers take files from the
     *                            feed, not from API callers (DESIGN §2.9).
     */
    public X12ProducerFacade(BufferStore buffer, ObjectTree tree, SchemaRegistry schemas, X12Operations ops,
            boolean allowFileManagement) {
        this.buffer = Objects.requireNonNull(buffer, "buffer");
        this.tree = Objects.requireNonNull(tree, "tree");
        this.schemas = Objects.requireNonNull(schemas, "schemas");
        this.ops = Objects.requireNonNull(ops, "ops");
        this.allowFileManagement = allowFileManagement;
    }

    /** Whether the file-management write surface is enabled ({@code isSupported} answers from this). */
    public boolean fileManagementEnabled() {
        return allowFileManagement;
    }

    // --- Objects -----------------------------------------------------------

    public String getRootObject() throws SQLException {
        return GSON.toJson(tree.object(ObjectTree.ROOT));
    }

    public String getObject(String objectId) throws SQLException {
        requireId(objectId);
        return GSON.toJson(tree.object(objectId));
    }

    /** One page of {@code objectId}'s children; {@code pageNumber} is 1-based. */
    public String getChildren(String objectId, int pageNumber, int pageSize) throws SQLException {
        requireId(objectId);
        requirePage(pageNumber, pageSize);
        ObjectTree.Page page = tree.children(objectId, pageSize, offset(pageNumber, pageSize));
        return pagedResults(page.items(), page.total(), pageSize, pageNumber);
    }

    // --- Collections (read-only browse) -----------------------------------

    /** One page of a collection's elements, newest first, narrowed by an optional RFC4515 {@code filter}. */
    public String getCollectionElements(String objectId, String filter, int pageNumber, int pageSize)
            throws SQLException {
        requireId(objectId);
        requirePage(pageNumber, pageSize);
        ObjectTree.Collection coll = tree.resolveCollection(objectId);
        String where = composeWhere(coll, filter);

        List<TransactionRow> rows = buffer.search(where, pageSize, offset(pageNumber, pageSize));
        List<Map<String, Object>> elements = new ArrayList<>(rows.size());
        for (TransactionRow r : rows) {
            elements.add(toElement(r));
        }
        return pagedResults(elements, buffer.countWhere(where), pageSize, pageNumber);
    }

    public String getCollectionElement(String objectId, String elementKey) throws SQLException {
        requireId(objectId);
        if (elementKey == null || elementKey.isBlank()) {
            throw ProducerException.illegalArgument("elementKey is required");
        }
        ObjectTree.Collection coll = tree.resolveCollection(objectId);
        String where = and(coll.scopeWhere(), "element_key = " + ObjectTree.sql(elementKey));
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

    /**
     * Where the bytes of a {@code /files/<fileId>} or live {@code /inbox} file node live
     * (DESIGN §2.8/§2.9); streamed by the HTTP layer.
     */
    public BinaryContent downloadBinary(String objectId) throws SQLException {
        requireId(objectId);
        return tree.downloadBinary(objectId);
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

    /** The gate: {@code UnsupportedOperationError} for {@code operationId} unless file management is on. */
    void requireFileManagement(String operationId) {
        if (!allowFileManagement) {
            throw ProducerException.unsupported(operationId
                + " is disabled: the receiver is receive-only unless the deployment sets "
                + "config.allowFileManagement=true");
        }
    }

    // --- Functions: ops/* (DESIGN §2.5) ------------------------------------

    /**
     * Run {@code /ops/<fn>} on {@code inputJson} (the {@code requestBody}: a JSON object,
     * or blank/null for none). Malformed JSON, a non-object body or input that fails the
     * function's schema is a 400 and nothing runs.
     */
    public String invokeFunction(String objectId, String inputJson) throws SQLException {
        String fn = requireFunction(objectId);
        return GSON_NULLS.toJson(ops.invoke(fn, toMap(parseObject(inputJson, "requestBody"))));
    }

    /**
     * {@code validateFunctionInput}: check {@code {input, strict}} against {@code /ops/<fn>}'s
     * input schema without running it; the interface {@code ValidationResult}.
     */
    public String validateFunctionInput(String objectId, String requestJson) throws SQLException {
        String fn = requireFunction(objectId);
        JsonObject request = parseObject(requestJson, "validateFunctionInputRequest");
        for (String key : request.keySet()) {
            if (!VALIDATE_REQUEST_KEYS.contains(key)) {
                throw ProducerException.illegalArgument("Unknown validateFunctionInputRequest property: " + key);
            }
        }
        boolean strict = false;
        JsonElement s = request.get("strict");
        if (s != null && !s.isJsonNull()) {
            if (!s.isJsonPrimitive() || !s.getAsJsonPrimitive().isBoolean()) {
                throw ProducerException.illegalArgument("strict must be a boolean");
            }
            strict = s.getAsBoolean();
        }
        JsonElement input = request.get("input");
        Object in = input == null || input.isJsonNull() ? null : GSON.fromJson(input, Object.class);
        return GSON.toJson(ops.validateInput(fn, in, strict));
    }

    /** The function name behind {@code objectId}: 404 when unknown, 400 when not a function. */
    private String requireFunction(String objectId) throws SQLException {
        requireId(objectId);
        Map<String, Object> obj = tree.object(objectId);
        Object classes = obj.get("objectClass");
        if (!(classes instanceof List) || !((List<?>) classes).contains("function")) {
            throw ProducerException.unsupported("Object is not a function: " + objectId);
        }
        return objectId.substring(objectId.lastIndexOf('/') + 1);
    }

    /** A request body as a JSON object; blank or JSON null is an empty object. */
    private static JsonObject parseObject(String json, String what) {
        if (json == null || json.isBlank()) {
            return new JsonObject();
        }
        JsonElement el;
        try {
            el = JsonParser.parseString(json);
        } catch (JsonParseException e) {
            throw ProducerException.illegalArgument(what + " is not valid JSON");
        }
        if (el.isJsonNull()) {
            return new JsonObject();
        }
        if (!el.isJsonObject()) {
            throw ProducerException.illegalArgument(what + " must be a JSON object");
        }
        return el.getAsJsonObject();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(JsonObject obj) {
        return GSON.fromJson(obj, Map.class);
    }

    // --- element mapping (DESIGN §5 envelope overlay) ----------------------

    /**
     * Build a collection element from a row: the typed body ({@code mapped_json})
     * overlaid with the authoritative envelope (DESIGN §5): {@code elementKey, fileId,
     * fileName, sourceName, isaControlNumber, gsControlNumber, stControlNumber, gs08,
     * transactionType, senderId, receiverId, interchangeDate, receivedAt, status, leaseId,
     * envelope, parserErrorCount}.
     *
     * <p>Body values stay {@link JsonElement}s, which Gson writes back verbatim: converting
     * them to Java objects would push every number through {@code double}, turning a
     * {@code 300.00} amount into {@code 300.0} and rounding a long {@code N0} value.
     */
    public static Map<String, Object> toElement(TransactionRow r) {
        Map<String, Object> element = new LinkedHashMap<>();
        JsonObject body = r.mappedJson() == null ? null : GSON.fromJson(r.mappedJson(), JsonObject.class);
        if (body != null) {
            for (Map.Entry<String, JsonElement> e : body.entrySet()) {
                element.put(e.getKey(), e.getValue());
            }
        }
        element.put("elementKey", r.elementKey());
        element.put("fileId", r.fileId());
        element.put("fileName", FileRow.fileNameOf(r.fileId()));
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

    // --- helpers -----------------------------------------------------------

    private static String composeWhere(ObjectTree.Collection coll, String filter) {
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

    /** The interface's paging bounds: {@code pageNumber >= 1}, {@code 1 <= pageSize <= 1000}. */
    private static void requirePage(int pageNumber, int pageSize) {
        if (pageNumber < 1) {
            throw ProducerException.illegalArgument("pageNumber must be at least 1, got " + pageNumber);
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw ProducerException.illegalArgument(
                "pageSize must be between 1 and " + MAX_PAGE_SIZE + ", got " + pageSize);
        }
    }

    /** Row offset of a validated page; long arithmetic so a huge pageNumber cannot wrap negative. */
    private static int offset(int pageNumber, int pageSize) {
        return (int) Math.min(Integer.MAX_VALUE, (long) (pageNumber - 1) * pageSize);
    }

    private static void requireId(String objectId) {
        if (objectId == null || objectId.isBlank()) {
            throw ProducerException.illegalArgument("objectId is required");
        }
    }
}

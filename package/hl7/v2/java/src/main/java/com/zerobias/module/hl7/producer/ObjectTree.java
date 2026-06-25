package com.zerobias.module.hl7.producer;

import com.zerobias.module.hl7.buffer.BufferStore;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The DataProducer object hierarchy (DESIGN §2.1): a tree of container /
 * collection / function / document nodes addressed by path-style ids.
 *
 * <pre>
 * /                              container (root)
 * └─ /hl7-v2-receiver            container
 *    ├─ /messages                collection (all rows, message-envelope schema)
 *    ├─ /by-type                 container
 *    │  └─ /by-type/&lt;MSG&gt;        collection (one per configured structure)
 *    ├─ /by-sender               container
 *    │  └─ /by-sender/&lt;APP&gt;       collection (one per distinct MSH-3)
 *    ├─ /by-port                 container
 *    │  └─ /by-port/&lt;NAME&gt;        collection (one per distinct listener port name)
 *    ├─ /stats                   document
 *    └─ /ops                     container
 *       └─ /ops/&lt;fn&gt;             function (take/ack/release/replay/purge)
 * </pre>
 *
 * This class owns node metadata and classification; the buffer-backed element
 * reads live in {@link Hl7ProducerFacade}. Collection sizes and the dynamic
 * {@code /by-sender/<app>} children are read live from the buffer.
 */
public final class ObjectTree {

    static final String ROOT = "/";
    static final String RECEIVER = "/hl7-v2-receiver";
    static final String MESSAGES = RECEIVER + "/messages";
    static final String BY_TYPE = RECEIVER + "/by-type";
    static final String BY_SENDER = RECEIVER + "/by-sender";
    static final String BY_PORT = RECEIVER + "/by-port";
    static final String STATS = RECEIVER + "/stats";
    static final String OPS = RECEIVER + "/ops";

    static final String ENVELOPE_SCHEMA = "schema:shared:hl7v2.message-envelope";
    static final List<String> OPS_FUNCTIONS = List.of("take", "ack", "release", "replay", "purge");

    private final BufferStore buffer;
    private final SchemaRegistry schemas;
    private final String version;
    /** Extension structures → buffer scope (discriminator WHERE clause), DESIGN §7.2. */
    private final Map<String, String> extensionScopes;

    public ObjectTree(BufferStore buffer, SchemaRegistry schemas, String version) {
        this(buffer, schemas, version, Map.of());
    }

    public ObjectTree(BufferStore buffer, SchemaRegistry schemas, String version,
            Map<String, String> extensionScopes) {
        this.buffer = buffer;
        this.schemas = schemas;
        this.version = version;
        this.extensionScopes = extensionScopes;
    }

    /** A collection's buffer scope (a WHERE fragment over the buffer; null = all rows) + element schema. */
    public record Collection(String id, String scopeWhere, String schemaId) {
    }

    /** by-type structures (all namespaces) → collection schema id. */
    private Map<String, String> byTypeStructures() {
        return schemas.messageStructureIds();
    }

    /** The buffer scope for a by-type structure: discriminator WHERE for extensions, else message_structure. */
    private String byTypeScope(String struct) {
        String ext = extensionScopes.get(struct);
        return ext != null ? ext : "message_structure = " + sql(struct);
    }

    /**
     * Resolve a collection id to its buffer scope, or throw: {@code noSuchObjectError}
     * if a {@code /by-type|/by-sender} discriminator value is unknown,
     * {@code UnsupportedOperationError} if the id isn't a collection at all.
     */
    public Collection resolveCollection(String id) throws SQLException {
        if (MESSAGES.equals(id)) {
            return new Collection(id, null, ENVELOPE_SCHEMA);
        }
        if (id.startsWith(BY_TYPE + "/")) {
            String struct = id.substring((BY_TYPE + "/").length());
            String schemaId = byTypeStructures().get(struct);
            if (schemaId == null) {
                throw ProducerException.noSuchObject(id);
            }
            return new Collection(id, byTypeScope(struct), schemaId);
        }
        if (id.startsWith(BY_SENDER + "/")) {
            String app = id.substring((BY_SENDER + "/").length());
            if (!buffer.distinctValues("sending_app").contains(app)) {
                throw ProducerException.noSuchObject(id);
            }
            return new Collection(id, "sending_app = " + sql(app), ENVELOPE_SCHEMA);
        }
        if (id.startsWith(BY_PORT + "/")) {
            String port = id.substring((BY_PORT + "/").length());
            if (!buffer.distinctValues("source_port").contains(port)) {
                throw ProducerException.noSuchObject(id);
            }
            return new Collection(id, "source_port = " + sql(port), ENVELOPE_SCHEMA);
        }
        // exists but isn't a collection, or doesn't exist
        object(id); // throws noSuchObject if unknown
        throw ProducerException.unsupported("Object is not a collection: " + id);
    }

    /** Object metadata for {@code id}, or throw {@code noSuchObjectError}. */
    public Map<String, Object> object(String id) throws SQLException {
        switch (id) {
            case ROOT:
                return container(ROOT, "/");
            case RECEIVER:
                return container(RECEIVER, "hl7-v2-receiver");
            case MESSAGES:
                return collection(MESSAGES, "messages", ENVELOPE_SCHEMA, buffer.count());
            case BY_TYPE:
                return container(BY_TYPE, "by-type");
            case BY_SENDER:
                return container(BY_SENDER, "by-sender");
            case BY_PORT:
                return container(BY_PORT, "by-port");
            case STATS:
                return document(STATS, "stats");
            case OPS:
                return container(OPS, "ops");
            default:
                return dynamicObject(id);
        }
    }

    private Map<String, Object> dynamicObject(String id) throws SQLException {
        if (id.startsWith(BY_TYPE + "/")) {
            String struct = id.substring((BY_TYPE + "/").length());
            String schemaId = byTypeStructures().get(struct);
            if (schemaId == null) {
                throw ProducerException.noSuchObject(id);
            }
            long size = buffer.countWhere(byTypeScope(struct));
            return collection(id, struct, schemaId, size);
        }
        if (id.startsWith(BY_SENDER + "/")) {
            String app = id.substring((BY_SENDER + "/").length());
            if (!buffer.distinctValues("sending_app").contains(app)) {
                throw ProducerException.noSuchObject(id);
            }
            long size = buffer.countWhere("sending_app = " + sql(app));
            return collection(id, app, ENVELOPE_SCHEMA, size);
        }
        if (id.startsWith(BY_PORT + "/")) {
            String port = id.substring((BY_PORT + "/").length());
            if (!buffer.distinctValues("source_port").contains(port)) {
                throw ProducerException.noSuchObject(id);
            }
            long size = buffer.countWhere("source_port = " + sql(port));
            return collection(id, port, ENVELOPE_SCHEMA, size);
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

    /** Direct children of {@code id}, or throw {@code noSuchObjectError}. */
    public List<Map<String, Object>> children(String id) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        switch (id) {
            case ROOT:
                out.add(object(RECEIVER));
                return out;
            case RECEIVER:
                out.add(object(MESSAGES));
                out.add(object(BY_TYPE));
                out.add(object(BY_SENDER));
                out.add(object(BY_PORT));
                out.add(object(STATS));
                out.add(object(OPS));
                return out;
            case BY_TYPE:
                // Sort for a stable listing — the registry's structure map has no
                // guaranteed iteration order (it varies by classpath/platform), and
                // by-sender (SQL ORDER BY) / ops (fixed list) are already deterministic.
                for (String struct : new java.util.TreeSet<>(byTypeStructures().keySet())) {
                    out.add(object(BY_TYPE + "/" + struct));
                }
                return out;
            case BY_SENDER:
                for (String app : buffer.distinctValues("sending_app")) {
                    out.add(object(BY_SENDER + "/" + app));
                }
                return out;
            case BY_PORT:
                for (String port : buffer.distinctValues("source_port")) {
                    out.add(object(BY_PORT + "/" + port));
                }
                return out;
            case OPS:
                for (String fn : OPS_FUNCTIONS) {
                    out.add(object(OPS + "/" + fn));
                }
                return out;
            default:
                // leaf (collection/document/function) -> no children; unknown -> 404
                Map<String, Object> obj = object(id);
                @SuppressWarnings("unchecked")
                List<String> classes = (List<String>) obj.get("objectClass");
                if (classes.contains("container")) {
                    return out; // a container with no static children
                }
                return out;
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
        o.put("documentSchema", "schema:shared:hl7v2.receiver-stats");
        return o;
    }

    private Map<String, Object> function(String id, String fn) {
        Map<String, Object> o = base(id, fn);
        o.put("objectClass", List.of("function"));
        o.put("inputSchema", "schema:function:hl7v2.ops." + fn + ":input");
        o.put("outputSchema", "schema:function:hl7v2.ops." + fn + ":output");
        o.put("throws", throwsFor(fn));
        return o;
    }

    /** Declared error codes per function (DESIGN §2.5); schemas land in Phase 7. */
    private Map<String, Object> throwsFor(String fn) {
        Map<String, Object> t = new LinkedHashMap<>();
        switch (fn) {
            case "take":
                t.put("lease_capacity_exceeded", "schema:shared:hl7v2.ops-error");
                t.put("backpressure", "schema:shared:hl7v2.ops-error");
                break;
            case "ack":
            case "release":
                t.put("lease_expired", "schema:shared:hl7v2.ops-error");
                t.put("not_found", "schema:shared:hl7v2.ops-error");
                break;
            default:
                break;
        }
        return t;
    }

    private Map<String, Object> base(String id, String name) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", id);
        o.put("name", name);
        return o;
    }

    /** Single-quote-escaped SQL string literal (scope values are buffer-derived). */
    private static String sql(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}

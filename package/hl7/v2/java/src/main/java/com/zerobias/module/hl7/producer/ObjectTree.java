package com.zerobias.module.hl7.producer;

import com.zerobias.module.hl7.buffer.BufferStore;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The DataProducer object hierarchy (DESIGN §2.1): a tree of container /
 * collection / function / document nodes addressed by path-style ids.
 *
 * <pre>
 * /                              container (root)
 * └─ /hl7-v2-receiver            container
 *    ├─ /messages                collection (all rows, message-envelope schema)
 *    ├─ /by-type                 container
 *    │  └─ /by-type/&lt;type&gt;       collection while a type has ONE version (already
 *    │     └─ /by-type/&lt;type&gt;/&lt;v&gt; homogeneous); ELSE a container whose per-version
 *    │                             leaves are the collections — the version level is
 *    │                             interposed only when a type spans versions
 *    ├─ /by-version              container
 *    │  └─ /by-version/&lt;v&gt;       collection (one per distinct HL7 version, envelope)
 *    ├─ /by-sender               container
 *    │  └─ /by-sender/&lt;APP&gt;       collection (one per distinct MSH-3)
 *    ├─ /by-port                 container
 *    │  └─ /by-port/&lt;NAME&gt;        collection (one per distinct listener port name)
 *    ├─ /stats                   document
 *    └─ /ops                     container
 *       └─ /ops/&lt;fn&gt;             function (take/ack/release/replay/recast/purge)
 * </pre>
 *
 * <p>An individual HL7 <em>message is an atom</em> — a collection <em>element</em>
 * (fetched by control-id), never a node in this tree. The only folders are the
 * discriminators, and their children are <em>emergent</em> — read live from the
 * buffer's distinct values, so a node appears the first time a matching message
 * lands. A collection must be <b>homogeneous</b> (one schema), and a discriminator
 * level is interposed only when required to reach that: {@code /by-type/<type>} is
 * itself the collection while a type has a single version, and splits into
 * {@code /by-type/<type>/<v>} only once it spans versions. Coarser facets
 * ({@code /by-version}, {@code /by-sender}, {@code /by-port}) are heterogeneous
 * (envelope schema). Extension structures (DESIGN §7) surface as discriminator-scoped
 * {@code /by-type/<X>} collections alongside the base ones.
 */
public final class ObjectTree {

    static final String ROOT = "/";
    static final String RECEIVER = "/hl7-v2-receiver";
    static final String MESSAGES = RECEIVER + "/messages";
    static final String BY_TYPE = RECEIVER + "/by-type";
    static final String BY_VERSION = RECEIVER + "/by-version";
    static final String BY_SENDER = RECEIVER + "/by-sender";
    static final String BY_PORT = RECEIVER + "/by-port";
    static final String STATS = RECEIVER + "/stats";
    static final String OPS = RECEIVER + "/ops";

    static final String ENVELOPE_SCHEMA = "schema:shared:hl7v2.message-envelope";
    static final List<String> OPS_FUNCTIONS =
        List.of("take", "ack", "release", "replay", "recast", "purge", "er7", "validate");

    private final BufferStore buffer;
    private final SchemaRegistry schemas;
    /** Extension structures → buffer scope (discriminator WHERE clause), DESIGN §7.2. */
    private final Map<String, String> extensionScopes;

    /** {@code version} (the configured default slot) is retained in the signature for
     *  callers but no longer used: a collection's schema is now derived per row from
     *  the message's own HL7 version ({@code /by-type/<MSG>/<v>}). */
    public ObjectTree(BufferStore buffer, SchemaRegistry schemas, String version) {
        this(buffer, schemas, version, Map.of());
    }

    public ObjectTree(BufferStore buffer, SchemaRegistry schemas, String version,
            Map<String, String> extensionScopes) {
        this.buffer = buffer;
        this.schemas = schemas;
        this.extensionScopes = extensionScopes;
    }

    /** A collection's buffer scope (a WHERE fragment over the buffer; null = all rows) + element schema. */
    public record Collection(String id, String scopeWhere, String schemaId) {
    }

    // --- version / structure helpers ---------------------------------------

    /** HAPI structure slot for an HL7 version string ({@code "2.4" -> "v24"}). */
    private static String slot(String hl7Version) {
        return "v" + hl7Version.replace(".", "");
    }

    private boolean isExtensionStructure(String struct) {
        return extensionScopes.containsKey(struct);
    }

    /** Base (non-extension) message structures actually present in the buffer. */
    private List<String> baseStructures() throws SQLException {
        return buffer.distinctValues("message_structure");
    }

    /** HL7 versions present for one base structure (the {@code /by-type/<MSG>} children). */
    private List<String> versionsForStructure(String struct) throws SQLException {
        return buffer.distinctValues("hl7_version", "message_structure = " + sql(struct));
    }

    private String baseTypeScope(String struct, String ver) {
        return "message_structure = " + sql(struct) + " AND hl7_version = " + sql(ver);
    }

    /** Version-correct table schema for a structure, or the envelope if that slot isn't bundled. */
    private String tableSchema(String struct, String ver) {
        String tableId = "schema:table:hl7v2." + slot(ver) + "." + struct;
        return schemas.has(tableId) ? tableId : ENVELOPE_SCHEMA;
    }

    /** Extension collection schema id (namespace-scoped table), from the merged catalog. */
    private String extensionSchema(String struct) {
        String id = schemas.messageStructureIds().get(struct);
        return id != null ? id : ENVELOPE_SCHEMA;
    }

    // --- collection resolution ---------------------------------------------

    /**
     * Resolve a collection id to its buffer scope, or throw: {@code noSuchObjectError}
     * for an unknown discriminator value, {@code UnsupportedOperationError} if the id
     * is a container (e.g. a bare {@code /by-type/<MSG>}) rather than a collection.
     */
    public Collection resolveCollection(String id) throws SQLException {
        if (MESSAGES.equals(id)) {
            return new Collection(id, null, ENVELOPE_SCHEMA);
        }
        if (id.startsWith(BY_TYPE + "/")) {
            String rem = id.substring((BY_TYPE + "/").length());
            int slash = rem.indexOf('/');
            if (slash < 0) {
                if (isExtensionStructure(rem)) {
                    return new Collection(id, extensionScopes.get(rem), extensionSchema(rem));
                }
                List<String> vers = versionsForStructure(rem);
                if (vers.isEmpty()) {
                    throw ProducerException.noSuchObject(id);
                }
                if (vers.size() == 1) {
                    // homogeneous by type alone — no version discriminator needed
                    return new Collection(id, "message_structure = " + sql(rem), tableSchema(rem, vers.get(0)));
                }
                throw ProducerException.unsupported("Object is not a collection (drill into a version): " + id);
            }
            String struct = rem.substring(0, slash);
            String ver = rem.substring(slash + 1);
            List<String> vers = versionsForStructure(struct);
            if (!vers.contains(ver) || vers.size() == 1) {   // version node exists only when required
                throw ProducerException.noSuchObject(id);
            }
            return new Collection(id, baseTypeScope(struct, ver), tableSchema(struct, ver));
        }
        if (id.startsWith(BY_VERSION + "/")) {
            String ver = id.substring((BY_VERSION + "/").length());
            if (!buffer.distinctValues("hl7_version").contains(ver)) {
                throw ProducerException.noSuchObject(id);
            }
            return new Collection(id, "hl7_version = " + sql(ver), ENVELOPE_SCHEMA);
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
        object(id); // throws noSuchObject if unknown
        throw ProducerException.unsupported("Object is not a collection: " + id);
    }

    // --- object metadata ----------------------------------------------------

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
            case BY_VERSION:
                return container(BY_VERSION, "by-version");
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
            String rem = id.substring((BY_TYPE + "/").length());
            int slash = rem.indexOf('/');
            if (slash < 0) {
                if (isExtensionStructure(rem)) {
                    long size = buffer.countWhere(extensionScopes.get(rem));
                    return collection(id, rem, extensionSchema(rem), size);
                }
                List<String> vers = versionsForStructure(rem);
                if (vers.isEmpty()) {
                    throw ProducerException.noSuchObject(id);
                }
                if (vers.size() == 1) {
                    long size = buffer.countWhere("message_structure = " + sql(rem));
                    return collection(id, rem, tableSchema(rem, vers.get(0)), size);   // homogeneous: single version
                }
                return container(id, rem);   // spans versions: drill in
            }
            String struct = rem.substring(0, slash);
            String ver = rem.substring(slash + 1);
            List<String> vers = versionsForStructure(struct);
            if (!vers.contains(ver) || vers.size() == 1) {
                throw ProducerException.noSuchObject(id);
            }
            long size = buffer.countWhere(baseTypeScope(struct, ver));
            return collection(id, ver, tableSchema(struct, ver), size);
        }
        if (id.startsWith(BY_VERSION + "/")) {
            String ver = id.substring((BY_VERSION + "/").length());
            if (!buffer.distinctValues("hl7_version").contains(ver)) {
                throw ProducerException.noSuchObject(id);
            }
            long size = buffer.countWhere("hl7_version = " + sql(ver));
            return collection(id, ver, ENVELOPE_SCHEMA, size);
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

    // --- children -----------------------------------------------------------

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
                out.add(object(BY_VERSION));
                out.add(object(BY_SENDER));
                out.add(object(BY_PORT));
                out.add(object(STATS));
                out.add(object(OPS));
                return out;
            case BY_TYPE:
                // Received base structures + extension structures, sorted for a stable
                // listing (distinct-value order is platform-dependent).
                for (String struct : byTypeChildren()) {
                    out.add(object(BY_TYPE + "/" + struct));
                }
                return out;
            case BY_VERSION:
                for (String ver : buffer.distinctValues("hl7_version")) {
                    out.add(object(BY_VERSION + "/" + ver));
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
                return dynamicChildren(id);
        }
    }

    /** Children of a dynamic container — a multi-version {@code /by-type/<type>} lists its versions. */
    private List<Map<String, Object>> dynamicChildren(String id) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        if (id.startsWith(BY_TYPE + "/")) {
            String rem = id.substring((BY_TYPE + "/").length());
            if (rem.indexOf('/') < 0 && !isExtensionStructure(rem)) {
                List<String> vers = versionsForStructure(rem);
                if (vers.size() > 1) {   // only a multi-version type is a container
                    for (String ver : vers) {
                        out.add(object(BY_TYPE + "/" + rem + "/" + ver));
                    }
                    return out;
                }
            }
        }
        // leaf (collection/document/function) or an empty container -> no children;
        // unknown id -> 404 via object().
        object(id);
        return out;
    }

    /** Sorted union of received base structures and merged extension structures. */
    private List<String> byTypeChildren() throws SQLException {
        TreeSet<String> names = new TreeSet<>(baseStructures());
        names.addAll(extensionScopes.keySet());
        return new ArrayList<>(names);
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
            case "er7":
            case "validate":
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

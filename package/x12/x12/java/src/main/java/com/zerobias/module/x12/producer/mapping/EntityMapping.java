package com.zerobias.module.x12.producer.mapping;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zerobias.module.x12.materializer.EntityGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A business entity projected out of the object graph (DESIGN §8.5).
 *
 * <p>The graph stores X12 structure: {@code loop2100} instances whose {@code clp} child has a
 * {@code clp04}. A mapping says what that <em>is</em> — a Claim whose {@code paidAmount} is
 * {@code clp.clp04} — and, critically, at what <b>grain</b>: one row per instance of the
 * {@code anchor} schema. Choosing the anchor chooses the grain, which is why it is declared
 * rather than inferred.
 *
 * <pre>
 * Remittance   anchor = the transaction root      1 row per transaction set
 * Claim        anchor = loop2100                  1 row per claim
 * ServiceLine  anchor = loop2110                  1 row per service line
 * </pre>
 *
 * <p>Column paths are relative to the anchor and may carry a qualifier predicate, which is how
 * the semantics X12 hides in code positions become names: {@code nm1[nm101=QC].nm103} is the
 * patient's last name, {@code amt[amt01=AU].amt02} the allowed amount. {@code dimensions} are
 * transaction-level values (payer, payee) resolved once per transaction set so segmentation —
 * "claims for this payer" — is an indexed lookup instead of a walk up the graph per row.
 *
 * <p>A path step may also be {@code ^property}: the nearest ANCESTOR of the current instance
 * that sits under {@code property}. That is how a row reaches data its grain inherits — a
 * service line's claim id ({@code ^loop2300.clm.clm01}), an 837 claim's subscriber
 * ({@code ^loop2000B.loop2010BA.nm1.nm109}) — exactly, per instance, instead of through a
 * transaction-level dimension that would have to pick one of several.
 *
 * <p><b>Dimension grain.</b> An entity may instead declare {@code "grain": "dimension"}: one
 * row per distinct party named by a pair of dimensions ({@code identity.name} /
 * {@code identity.id}), e.g. a Payer. There is no anchor instance; its rows are derived from
 * {@code transaction_dims} (see {@code BusinessEntities}) and its columns name derived fields
 * ({@code key}, {@code name}, {@code id}, {@code transactionCount}, {@code firstSeen},
 * {@code lastSeen}, {@code transactionTypes}) rather than graph paths. Several guides may
 * declare the same dimension-grain collection; they are merged into one entity spanning them.
 *
 * <p>Mappings are content, not code: they ship in the pack format, so a trading-partner
 * variant or a new business entity does not need a module release.
 */
public final class EntityMapping {

    private static final Logger LOG = LoggerFactory.getLogger(EntityMapping.class);
    private static final Gson GSON = new Gson();

    /**
     * One named column: where to read it from the anchor, and how it is typed. {@code part}
     * ({@code from}/{@code to}, optional) takes one end of a date range: a {@code DTP} with
     * format {@code RD8} carries {@code CCYYMMDD-CCYYMMDD} in one element, and a single
     * {@code D8} date is both its own start and end.
     */
    public record Column(String name, String path, String dataType, boolean primaryKey,
            String enumSchemaId, String description, String part) {
        public Column(String name, String path, String dataType, boolean primaryKey,
                String enumSchemaId, String description) {
            this(name, path, dataType, primaryKey, enumSchemaId, description, null);
        }
    }

    /** Anchor grain: one row per instance of the anchor schema. The default. */
    public static final String GRAIN_ANCHOR = "anchor";
    /** Dimension grain: one row per distinct party named by a (name, id) dimension pair. */
    public static final String GRAIN_DIMENSION = "dimension";

    /** The derived fields a dimension-grain column may name in its {@code path}. */
    public static final List<String> DIMENSION_FIELDS = List.of("key", "name", "id", "transactionCount",
        "firstSeen", "lastSeen", "transactionTypes");

    /** The (name, id) dimension pair that identifies a dimension-grain row. */
    public record Identity(String nameDimension, String idDimension) {
    }

    /** A transaction-level value carried onto every row anchored under it. */
    public record Dimension(String name, String path, String dataType, String description) {
    }

    private final String name;
    private final String collection;
    private final String gs08;
    private final String anchorSchemaId;
    private final String schemaId;
    private final String description;
    private final List<Column> columns;
    private final List<Dimension> dimensions;
    private final String grain;
    private final Identity identity;
    private final List<String> guides;

    EntityMapping(String name, String collection, String gs08, String anchorSchemaId, String schemaId,
            String description, List<Column> columns, List<Dimension> dimensions) {
        this(name, collection, gs08, anchorSchemaId, schemaId, description, columns, dimensions,
            GRAIN_ANCHOR, null, gs08 == null ? List.of() : List.of(gs08));
    }

    private EntityMapping(String name, String collection, String gs08, String anchorSchemaId, String schemaId,
            String description, List<Column> columns, List<Dimension> dimensions, String grain,
            Identity identity, List<String> guides) {
        this.name = name;
        this.collection = collection;
        this.gs08 = gs08;
        this.anchorSchemaId = anchorSchemaId;
        this.schemaId = schemaId;
        this.description = description;
        this.columns = List.copyOf(columns);
        this.dimensions = List.copyOf(dimensions);
        this.grain = grain;
        this.identity = identity;
        this.guides = List.copyOf(guides);
    }

    /** {@link #GRAIN_ANCHOR} or {@link #GRAIN_DIMENSION}. */
    public String grain() {
        return grain;
    }

    public boolean isDimensionGrain() {
        return GRAIN_DIMENSION.equals(grain);
    }

    /** The identifying dimension pair of a dimension-grain entity; null for anchor grain. */
    public Identity identity() {
        return identity;
    }

    /** The guides (canonical GS08) this entity draws rows from. */
    public List<String> guides() {
        return guides;
    }

    /**
     * Fold the declarations of one dimension-grain collection from several guides into one
     * entity spanning all of them. They must agree on schema, identity and columns — one
     * collection, one schema — otherwise the later declaration is dropped with a warning and
     * the first one stands. Anchor-grain mappings are never merged: their grain is one
     * guide's loop.
     */
    public static EntityMapping merge(EntityMapping first, EntityMapping other) {
        if (!first.isDimensionGrain() || !other.isDimensionGrain()) {
            return null;
        }
        if (!java.util.Objects.equals(first.schemaId, other.schemaId)
                || !java.util.Objects.equals(first.identity, other.identity)
                || !first.columns.equals(other.columns)) {
            LOG.warn("mapping {} for {} disagrees with the {} declaration of collection '{}'; ignoring it",
                other.name, other.gs08, first.guides, first.collection);
            return first;
        }
        final List<String> guides = new ArrayList<>(first.guides);
        for (String g : other.guides) {
            if (!guides.contains(g)) {
                guides.add(g);
            }
        }
        return new EntityMapping(first.name, first.collection, first.gs08, first.anchorSchemaId,
            first.schemaId, first.description, first.columns, first.dimensions, first.grain,
            first.identity, guides);
    }

    public String name() {
        return name;
    }

    /** The tree segment this entity is browsable under, e.g. {@code claims}. */
    public String collection() {
        return collection;
    }

    public String gs08() {
        return gs08;
    }

    /** The schema whose instances are the rows — the grain. */
    public String anchorSchemaId() {
        return anchorSchemaId;
    }

    /** The business element schema id this mapping produces. */
    public String schemaId() {
        return schemaId;
    }

    public String description() {
        return description;
    }

    public List<Column> columns() {
        return columns;
    }

    public List<Dimension> dimensions() {
        return dimensions;
    }

    public Column primaryKey() {
        for (Column c : columns) {
            if (c.primaryKey()) {
                return c;
            }
        }
        return null;
    }

    /**
     * Everything filterable and sortable on this entity: its columns, the dimensions it
     * carries, and the provenance fields every row has. One definition, so a filter and a sort
     * can never disagree about what exists.
     */
    public Map<String, String> attributes() {
        final Map<String, String> out = new LinkedHashMap<>();
        for (Column c : columns) {
            out.put(c.name(), c.dataType());
        }
        if (isDimensionGrain()) {
            return out;   // a party is not projected from one transaction: no provenance fields
        }
        for (Dimension d : dimensions) {
            out.put(d.name(), d.dataType());
        }
        out.put("elementKey", "string");
        out.put("fileId", "string");
        return out;
    }

    /** The declared type of an attribute, or null when the entity has no such attribute. */
    public String typeOf(String attribute) {
        return attributes().get(attribute);
    }

    public Column column(String columnName) {
        for (Column c : columns) {
            if (c.name().equals(columnName)) {
                return c;
            }
        }
        return null;
    }

    /**
     * The guide's declared dimensions, read off the transaction root (DESIGN §8.5). Shared by
     * ingest and the startup graph backfill so both resolve a transaction set identically.
     */
    public static Map<String, EntityGraph.Value> dimensions(String gs08, List<EntityGraph.Entity> graph) {
        if (graph == null || graph.isEmpty()) {
            return Map.of();
        }
        final List<EntityMapping> mappings = GUIDE_CACHE.computeIfAbsent(gs08 == null ? "" : gs08,
            EntityMapping::forGuide);
        List<Dimension> declared = List.of();
        for (EntityMapping m : mappings) {
            if (!m.dimensions().isEmpty()) {
                declared = m.dimensions();
                break;
            }
        }
        final EntityGraph.Entity root = graph.get(0);
        final Map<String, EntityGraph.Value> out = new LinkedHashMap<>();
        for (Dimension d : declared) {
            final EntityGraph.Value v = unambiguous(readAll(graph, root, d.path()));
            if (v != null) {
                out.put(d.name(), v);
            }
        }
        return out;
    }

    /**
     * The one value every match agrees on, or null. A dimension is a fact about the whole
     * transaction set, so it only exists when the transaction states one: an 835 has one
     * {@code N1*PR}, but an 837 batch may carry several billing providers or payers under its
     * HL loops, and attributing all of it to whichever came first would put claims in the
     * wrong payer's segment. Ambiguous means absent; the per-claim value is still a column.
     */
    private static EntityGraph.Value unambiguous(List<EntityGraph.Value> values) {
        EntityGraph.Value found = null;
        for (EntityGraph.Value v : values) {
            if (v == null || v.text() == null) {
                continue;
            }
            if (found == null) {
                found = v;
            } else if (!found.text().equals(v.text())) {
                return null;
            }
        }
        return found;
    }

    private static final Map<String, List<EntityMapping>> GUIDE_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    // --- parsing ------------------------------------------------------------

    /**
     * Every mapping on the classpath for a guide, or empty when none ships. Any accepted
     * spelling resolves to the canonical guide ({@code 005010X223A1} and {@code 005010X223}
     * are materialized with the {@code 005010X223A2} structure, so they share its mapping and
     * its anchor schema ids).
     */
    public static List<EntityMapping> forGuide(String gs08) {
        if (gs08 == null) {
            return List.of();
        }
        gs08 = com.zerobias.module.x12.parser.TransactionTypes.canonical(gs08).orElse(gs08.trim());
        final String resource = "/mappings/" + gs08 + ".json";
        try (InputStream in = EntityMapping.class.getResourceAsStream(resource)) {
            if (in == null) {
                return List.of();
            }
            return parse(gs08, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            LOG.warn("cannot read {} ({}); no business entities for that guide", resource, e.toString());
            return List.of();
        }
    }

    /** Parse a mapping document; malformed input yields no entities rather than a failure. */
    public static List<EntityMapping> parse(String gs08, String json) {
        final JsonElement root;
        try {
            root = GSON.fromJson(json, JsonElement.class);
        } catch (RuntimeException malformed) {
            LOG.warn("mapping for {} is not valid JSON ({})", gs08, malformed.toString());
            return List.of();
        }
        if (root == null || !root.isJsonObject() || !root.getAsJsonObject().has("entities")) {
            return List.of();
        }
        final JsonObject doc = root.getAsJsonObject();
        final List<Dimension> dims = new ArrayList<>();
        if (doc.has("dimensions") && doc.get("dimensions").isJsonArray()) {
            for (JsonElement el : doc.getAsJsonArray("dimensions")) {
                final JsonObject d = el.getAsJsonObject();
                dims.add(new Dimension(str(d, "name"), str(d, "path"),
                    orDefault(str(d, "dataType"), "string"), str(d, "description")));
            }
        }
        final List<EntityMapping> out = new ArrayList<>();
        for (JsonElement el : doc.getAsJsonArray("entities")) {
            if (!el.isJsonObject()) {
                continue;
            }
            final JsonObject e = el.getAsJsonObject();
            final List<Column> columns = new ArrayList<>();
            final JsonArray cols = e.has("columns") && e.get("columns").isJsonArray()
                ? e.getAsJsonArray("columns") : new JsonArray();
            for (JsonElement cel : cols) {
                final JsonObject c = cel.getAsJsonObject();
                columns.add(new Column(str(c, "name"), str(c, "path"),
                    orDefault(str(c, "dataType"), "string"),
                    c.has("primaryKey") && c.get("primaryKey").getAsBoolean(),
                    str(c, "enumSchemaId"), str(c, "description"), str(c, "part")));
            }
            final String name = str(e, "name");
            final String collection = name == null ? null
                : orDefault(str(e, "collection"), name.toLowerCase(Locale.ROOT));
            if (GRAIN_DIMENSION.equals(str(e, "grain"))) {
                final JsonObject id = e.has("identity") && e.get("identity").isJsonObject()
                    ? e.getAsJsonObject("identity") : new JsonObject();
                final Identity identity = new Identity(str(id, "name"), str(id, "id"));
                final boolean fieldsKnown = columns.stream()
                    .allMatch(c -> DIMENSION_FIELDS.contains(c.path()));
                if (name == null || columns.isEmpty() || identity.nameDimension() == null
                        || identity.idDimension() == null || !fieldsKnown) {
                    LOG.warn("skipping dimension-grain entry without name/identity/columns, or naming a "
                        + "field outside {}: {}", DIMENSION_FIELDS, e);
                    continue;
                }
                out.add(new EntityMapping(name, collection, gs08, null, str(e, "schemaId"),
                    str(e, "description"), columns, List.of(), GRAIN_DIMENSION, identity,
                    gs08 == null ? List.of() : List.of(gs08)));
                continue;
            }
            if (name == null || str(e, "anchorSchemaId") == null || columns.isEmpty()) {
                LOG.warn("skipping mapping entry without name/anchorSchemaId/columns: {}", e);
                continue;
            }
            out.add(new EntityMapping(name, collection, gs08, str(e, "anchorSchemaId"),
                str(e, "schemaId"), str(e, "description"), columns, dims));
        }
        return out;
    }

    // --- path resolution ----------------------------------------------------

    /**
     * Read a column from an anchor instance's subtree.
     *
     * <p>A path is dot-separated steps, each {@code property} or {@code property[prop=value]},
     * ending in the scalar property to read: {@code clp.clp04}, {@code nm1[nm101=QC].nm103}.
     * The last step names a value on the entity the earlier steps reached; a one-step path
     * reads a value on the anchor itself. Anything unmatched is {@code null} — a missing
     * optional segment is normal in X12 and must not fail the row.
     */
    public static EntityGraph.Value read(List<EntityGraph.Entity> subtree, EntityGraph.Entity anchor,
            String path) {
        if (path == null || path.isBlank() || anchor == null) {
            return null;
        }
        final String[] steps = path.split("\\.");
        EntityGraph.Entity current = anchor;
        for (int i = 0; i < steps.length - 1; i++) {
            current = steps[i].startsWith("^")
                ? ancestor(subtree, current, steps[i].substring(1))
                : child(subtree, current, steps[i]);
            if (current == null) {
                return null;
            }
        }
        return value(current, steps[steps.length - 1]);
    }

    /**
     * Every value the path reaches, following EVERY matching child at each step rather than
     * the first — so a dimension can tell "the transaction says one thing" from "it says
     * several" (see {@link #dimensions}).
     */
    public static List<EntityGraph.Value> readAll(List<EntityGraph.Entity> subtree,
            EntityGraph.Entity anchor, String path) {
        if (path == null || path.isBlank() || anchor == null) {
            return List.of();
        }
        final String[] steps = path.split("\\.");
        List<EntityGraph.Entity> frontier = List.of(anchor);
        for (int i = 0; i < steps.length - 1 && !frontier.isEmpty(); i++) {
            final List<EntityGraph.Entity> next = new ArrayList<>();
            for (EntityGraph.Entity e : frontier) {
                if (steps[i].startsWith("^")) {
                    final EntityGraph.Entity a = ancestor(subtree, e, steps[i].substring(1));
                    if (a != null && !next.contains(a)) {
                        next.add(a);
                    }
                } else {
                    next.addAll(children(subtree, e, steps[i], Integer.MAX_VALUE));
                }
            }
            frontier = next;
        }
        final List<EntityGraph.Value> out = new ArrayList<>();
        for (EntityGraph.Entity e : frontier) {
            final EntityGraph.Value v = value(e, steps[steps.length - 1]);
            if (v != null) {
                out.add(v);
            }
        }
        return out;
    }

    /** The nearest ancestor (not self) that sits under {@code property}, if any. */
    private static EntityGraph.Entity ancestor(List<EntityGraph.Entity> subtree, EntityGraph.Entity from,
            String property) {
        EntityGraph.Entity current = from;
        while (current != null && current.parentLocalId != null) {
            current = byLocalId(subtree, current.parentLocalId);
            if (current != null && property.equals(current.property)) {
                return current;
            }
        }
        return null;
    }

    private static EntityGraph.Entity byLocalId(List<EntityGraph.Entity> subtree, int localId) {
        // flatten order puts an entity at its own local id; a graph read back from the buffer
        // uses row ids instead, so fall back to a scan when the fast path misses
        if (localId >= 0 && localId < subtree.size() && subtree.get(localId).localId == localId) {
            return subtree.get(localId);
        }
        for (EntityGraph.Entity e : subtree) {
            if (e.localId == localId) {
                return e;
            }
        }
        return null;
    }

    /** The first child under {@code property} whose qualifier predicate holds, if any. */
    private static EntityGraph.Entity child(List<EntityGraph.Entity> subtree, EntityGraph.Entity parent,
            String step) {
        final List<EntityGraph.Entity> found = children(subtree, parent, step, 1);
        return found.isEmpty() ? null : found.get(0);
    }

    /** Up to {@code limit} children under {@code property} whose qualifier predicate holds. */
    private static List<EntityGraph.Entity> children(List<EntityGraph.Entity> subtree,
            EntityGraph.Entity parent, String step, int limit) {
        final List<EntityGraph.Entity> out = new ArrayList<>();
        final int open = step.indexOf('[');
        final String property = open < 0 ? step : step.substring(0, open);
        String predProperty = null;
        String predValue = null;
        if (open > 0 && step.endsWith("]")) {
            final String pred = step.substring(open + 1, step.length() - 1);
            final int eq = pred.indexOf('=');
            if (eq > 0) {
                predProperty = pred.substring(0, eq).trim();
                predValue = pred.substring(eq + 1).trim();
            }
        }
        for (EntityGraph.Entity e : subtree) {
            if (e.parentLocalId == null || e.parentLocalId != parent.localId) {
                continue;
            }
            if (!property.equals(e.property)) {
                continue;
            }
            final boolean matches;
            if (predProperty == null) {
                matches = true;
            } else {
                final EntityGraph.Value v = value(e, predProperty);
                matches = v != null && predValue != null && predValue.equals(v.text());
            }
            if (matches) {
                out.add(e);
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }

    private static EntityGraph.Value value(EntityGraph.Entity entity, String property) {
        if (entity == null) {
            return null;
        }
        for (EntityGraph.Value v : entity.values) {
            if (property.equals(v.property())) {
                return v;
            }
        }
        return null;
    }

    /**
     * Project one anchor instance into a business row: declared column names, values typed by
     * the column's dataType. Amounts stay {@link BigDecimal}; a column the transaction does
     * not carry is present and null, so every row of a collection has the same shape.
     */
    public Map<String, Object> project(List<EntityGraph.Entity> subtree, EntityGraph.Entity anchor) {
        final Map<String, Object> row = new LinkedHashMap<>();
        for (Column c : columns) {
            row.put(c.name(), typed(c, read(subtree, anchor, c.path())));
        }
        return row;
    }

    private static Object typed(Column c, EntityGraph.Value v) {
        if (v != null && c.part() != null && v.text() != null) {
            // RD8 "CCYYMMDD-CCYYMMDD": one end of the range; a lone D8 date is both ends
            final String[] ends = v.text().split("-", 2);
            final String picked = "to".equals(c.part()) && ends.length == 2 ? ends[1] : ends[0];
            v = new EntityGraph.Value(v.property(), v.dataType(), picked.trim(), null, null);
        }
        return typed(c.dataType(), v);
    }

    /**
     * A stored value in its declared business type. Shared by columns and dimensions so a
     * decimal dimension is a number on the row exactly like a decimal column.
     *
     * <p>Numbers are built from {@code value_text}, the exact lexical form, never from
     * {@code num}: {@code num} is the comparison key (CLAUDE.md "value_text is the value") and
     * whatever produced it may have dropped the scale — {@code 300.00} must serialize as
     * {@code 300.00}, not {@code 300}. {@code num} is only the fallback for text that does not
     * parse, so a malformed element degrades rather than failing the row.
     */
    public static Object typed(String dataType, EntityGraph.Value v) {
        if (v == null) {
            return null;
        }
        switch (dataType == null ? "string" : dataType) {
            case "decimal": {
                final BigDecimal exact = EntityGraph.exactNumber("decimal", v.text());
                return exact != null ? exact : v.num();
            }
            case "integer": {
                BigDecimal n = EntityGraph.exactNumber("integer", v.text());
                if (n == null) {
                    n = v.num();
                }
                if (n == null) {
                    return v.text();
                }
                try {
                    return n.longValueExact();
                } catch (ArithmeticException notAnInteger) {
                    return n;
                }
            }
            case "boolean":
                return v.text() != null
                    ? EntityGraph.exactNumber("boolean", v.text()).signum() != 0
                    : v.num() != null && v.num().signum() != 0;
            case "date":
                return isoDate(v.text());
            default:
                return v.text();
        }
    }

    /**
     * A date column is an ISO {@code YYYY-MM-DD} string. A {@code DT} element is already
     * normalized by the materializer; a {@code DTP03}/{@code DMG02} is {@code AN} on the wire
     * (its format lives in the qualifier), so a bare {@code CCYYMMDD} is rewritten here. Any
     * other shape is returned as-is rather than guessed at.
     */
    static String isoDate(String text) {
        if (text != null && text.length() == 8 && text.chars().allMatch(Character::isDigit)) {
            return text.substring(0, 4) + "-" + text.substring(4, 6) + "-" + text.substring(6);
        }
        return text;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }

    private static String orDefault(String v, String dflt) {
        return v == null || v.isBlank() ? dflt : v;
    }
}

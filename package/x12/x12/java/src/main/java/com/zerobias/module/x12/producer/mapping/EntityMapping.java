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
 * <p>Mappings are content, not code: they ship in the pack format, so a trading-partner
 * variant or a new business entity does not need a module release.
 */
public final class EntityMapping {

    private static final Logger LOG = LoggerFactory.getLogger(EntityMapping.class);
    private static final Gson GSON = new Gson();

    /** One named column: where to read it from the anchor, and how it is typed. */
    public record Column(String name, String path, String dataType, boolean primaryKey,
            String enumSchemaId, String description) {
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

    EntityMapping(String name, String collection, String gs08, String anchorSchemaId, String schemaId,
            String description, List<Column> columns, List<Dimension> dimensions) {
        this.name = name;
        this.collection = collection;
        this.gs08 = gs08;
        this.anchorSchemaId = anchorSchemaId;
        this.schemaId = schemaId;
        this.description = description;
        this.columns = List.copyOf(columns);
        this.dimensions = List.copyOf(dimensions);
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

    public Column column(String columnName) {
        for (Column c : columns) {
            if (c.name().equals(columnName)) {
                return c;
            }
        }
        return null;
    }

    // --- parsing ------------------------------------------------------------

    /** Every mapping on the classpath for a guide, or empty when none ships. */
    public static List<EntityMapping> forGuide(String gs08) {
        if (gs08 == null) {
            return List.of();
        }
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
                    str(c, "enumSchemaId"), str(c, "description")));
            }
            if (str(e, "name") == null || str(e, "anchorSchemaId") == null || columns.isEmpty()) {
                LOG.warn("skipping mapping entry without name/anchorSchemaId/columns: {}", e);
                continue;
            }
            out.add(new EntityMapping(str(e, "name"), orDefault(str(e, "collection"),
                str(e, "name").toLowerCase(Locale.ROOT)), gs08, str(e, "anchorSchemaId"),
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
            current = child(subtree, current, steps[i]);
            if (current == null) {
                return null;
            }
        }
        return value(current, steps[steps.length - 1]);
    }

    /** The first child under {@code property} whose qualifier predicate holds, if any. */
    private static EntityGraph.Entity child(List<EntityGraph.Entity> subtree, EntityGraph.Entity parent,
            String step) {
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
            if (predProperty == null) {
                return e;
            }
            final EntityGraph.Value v = value(e, predProperty);
            if (v != null && predValue != null && predValue.equals(v.text())) {
                return e;
            }
        }
        return null;
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
        if (v == null) {
            return null;
        }
        switch (c.dataType()) {
            case "decimal":
                return v.num() != null ? v.num() : EntityGraph.exactNumber("decimal", v.text());
            case "integer":
                final BigDecimal n = v.num() != null ? v.num() : EntityGraph.exactNumber("integer", v.text());
                if (n == null) {
                    return v.text();
                }
                try {
                    return n.longValueExact();
                } catch (ArithmeticException notAnInteger) {
                    return n;
                }
            case "boolean":
                return v.num() != null && v.num().signum() != 0;
            default:
                return v.text();
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }

    private static String orDefault(String v, String dflt) {
        return v == null || v.isBlank() ? dflt : v;
    }
}

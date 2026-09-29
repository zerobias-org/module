package com.zerobias.module.x12.materializer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A materialized transaction set flattened into an addressable object graph (DESIGN §8.4).
 *
 * <p>Every loop, segment and composite instance becomes an {@link Entity} carrying the
 * schema id that describes it — the same {@code schema:type:x12.<GS08>.<xid>} ids the packs
 * emit and {@code getSchema} serves — plus a parent edge and its position among repeats.
 * Every scalar field becomes a {@link Value} typed by that schema's core dataType, so a
 * filter compares money as a number and a date as a date instead of as text.
 *
 * <p>This is what makes the DataProducer surface queryable rather than a document store: a
 * claim is a row with a schema, not a path inside a blob. {@link #assemble} walks the graph
 * back into the nested form, which is how the drain path and {@code download} keep working
 * and how the round-trip test proves the flattening is lossless.
 *
 * <p>Structure comes from the {@link StructureIndex} the codegen emitted, not from guessing:
 * a loop's {@code structures} say which property is a loop and which is a segment, and a
 * segment's {@code fields} carry the core type per element. Properties the index does not
 * describe (a trading partner's extra segment) are still captured — by reversing the
 * materializer's own naming — so nothing is silently dropped.
 */
public final class EntityGraph {

    public static final String KIND_LOOP = "loop";
    public static final String KIND_SEGMENT = "segment";
    public static final String KIND_COMPOSITE = "composite";

    /** One loop / segment / composite instance. {@code id} is assigned by the caller on insert. */
    public static final class Entity {
        public final int localId;
        public final Integer parentLocalId;
        public final String schemaId;
        public final String xid;
        public final String kind;
        /** The property this instance sits under in its parent; null at the root. */
        public final String property;
        public final String path;
        public final int ordinal;
        public final List<Value> values = new ArrayList<>();
        /**
         * The parent-side property order as materialized. Field order is the wire order
         * (DESIGN §5), and a composite is a child entity rather than a value, so without
         * this the reassembled segment would list every scalar before every composite.
         */
        public final List<String> propertyOrder = new ArrayList<>();

        /**
         * Rebuild an instance read back from the buffer. {@code localId}/{@code parentLocalId}
         * are the caller's own numbering — {@link EntityGraph#assemble} only needs them to be
         * internally consistent, so a read path can pass the database ids directly.
         */
        public static Entity of(int localId, Integer parentLocalId, String schemaId, String xid,
                String kind, String property, String path, int ordinal) {
            return new Entity(localId, parentLocalId, schemaId, xid, kind, property, path, ordinal);
        }

        Entity(int localId, Integer parentLocalId, String schemaId, String xid, String kind,
                String property, String path, int ordinal) {
            this.localId = localId;
            this.parentLocalId = parentLocalId;
            this.schemaId = schemaId;
            this.xid = xid;
            this.kind = kind;
            this.property = property;
            this.path = path;
            this.ordinal = ordinal;
        }
    }

    /**
     * One scalar field, typed for comparison: {@code num} for integer/decimal, {@code date}
     * (epoch-millis) for date/date-time, {@code text} otherwise. {@code text} is always set
     * too, so reassembly never has to reconstruct a value from a number.
     */
    public record Value(String property, String dataType, String text, BigDecimal num, Long date) {
    }

    private final StructureIndex index;
    private final List<Entity> entities = new ArrayList<>();

    private EntityGraph(StructureIndex index) {
        this.index = index;
    }

    /** Flatten a materialized transaction set ({@code materializeTransaction} output). */
    public static List<Entity> flatten(StructureIndex index, Map<String, Object> tree) {
        EntityGraph g = new EntityGraph(index);
        StructureIndex.LoopEntry root = index == null ? null : index.transactionEntry();
        String rootSchema = index == null ? null : index.tableSchemaId;
        String rootXid = index == null || index.transactionXid == null ? "ST_LOOP" : index.transactionXid;
        Entity e = g.entity(null, rootSchema, rootXid, KIND_LOOP, null, "", 0);
        g.walkLoop(tree, root, e);
        return List.copyOf(g.entities);
    }

    // --- flattening ---------------------------------------------------------

    private Entity entity(Integer parent, String schemaId, String xid, String kind,
            String property, String path, int ordinal) {
        Entity e = new Entity(entities.size(), parent, schemaId, xid, kind, property, path, ordinal);
        entities.add(e);
        return e;
    }

    private void walkLoop(Map<String, Object> tree, StructureIndex.LoopEntry entry, Entity parent) {
        if (tree == null) {
            return;
        }
        for (Map.Entry<String, Object> field : tree.entrySet()) {
            final String property = field.getKey();
            final Ref ref = resolve(entry, property);
            if (ref == null) {
                continue;   // not a structure: a loop tree only holds loops and segments
            }
            parent.propertyOrder.add(property);
            int ordinal = 0;
            for (Object instance : instances(field.getValue())) {
                if (!(instance instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) instance;
                final String path = childPath(parent.path, property, ordinal, repeats(field.getValue()));
                if (ref.loop) {
                    StructureIndex.LoopEntry child = index == null ? null : index.loop(ref.xid);
                    Entity e = entity(parent.localId, child == null ? null : child.schemaId,
                        ref.xid, KIND_LOOP, property, path, ordinal);
                    walkLoop(m, child, e);
                } else {
                    StructureIndex.SegmentEntry seg = index == null ? null : index.segment(ref.xid);
                    Entity e = entity(parent.localId, seg == null ? null : seg.schemaId,
                        ref.xid, KIND_SEGMENT, property, path, ordinal);
                    walkSegment(m, seg, e);
                }
                ordinal++;
            }
        }
    }

    private void walkSegment(Map<String, Object> seg, StructureIndex.SegmentEntry entry, Entity self) {
        for (Map.Entry<String, Object> field : seg.entrySet()) {
            final String property = field.getKey();
            final StructureIndex.FieldEntry fe = fieldFor(entry, property);
            final Object value = field.getValue();
            self.propertyOrder.add(property);
            if (value instanceof Map) {
                composite(property, (Map<?, ?>) value, fe, self, 0, false);
            } else if (value instanceof List) {
                int ordinal = 0;
                for (Object v : (List<?>) value) {
                    if (v instanceof Map) {
                        composite(property, (Map<?, ?>) v, fe, self, ordinal, true);
                    } else {
                        // a repeating scalar keeps its wire form; the schema marks it multi
                        self.values.add(value(property + "[" + ordinal + "]", fe, v));
                    }
                    ordinal++;
                }
            } else if (value != null) {
                self.values.add(value(property, fe, value));
            }
        }
    }

    private void composite(String property, Map<?, ?> value, StructureIndex.FieldEntry fe,
            Entity parent, int ordinal, boolean repeating) {
        final String dataEle = fe == null ? null : fe.composite;
        StructureIndex.CompositeEntry ce = dataEle == null || index == null ? null : index.composite(dataEle);
        Entity e = entity(parent.localId, ce == null ? null : ce.schemaId,
            dataEle == null ? property.toUpperCase(Locale.ROOT) : dataEle, KIND_COMPOSITE,
            property, childPath(parent.path, property, ordinal, repeating), ordinal);
        for (Map.Entry<?, ?> sub : value.entrySet()) {
            final String subProperty = String.valueOf(sub.getKey());
            if (sub.getValue() != null && !(sub.getValue() instanceof Map) && !(sub.getValue() instanceof List)) {
                e.values.add(value(subProperty, fieldFor(ce, subProperty), sub.getValue()));
            }
        }
    }

    /** Type the value by the schema's core dataType; unknown fields fall back to text. */
    private static Value value(String property, StructureIndex.FieldEntry fe, Object raw) {
        final String type = fe == null || fe.coreType == null ? "string" : fe.coreType;
        final String text = String.valueOf(raw);
        BigDecimal num = null;
        Long date = null;
        switch (type) {
            case "integer":
            case "decimal":
                num = decimal(raw);
                break;
            case "date":
                date = epochOfDate(text);
                break;
            case "date-time":
                date = epochOfDateTime(text);
                break;
            case "boolean":
                num = Boolean.parseBoolean(text) || "1".equals(text) ? BigDecimal.ONE : BigDecimal.ZERO;
                break;
            default:
                break;
        }
        return new Value(property, type, text, num, date);
    }

    /**
     * Money and counts as exact decimals. {@code BigDecimal} on the way in, so an implied-decimal
     * N2 element never becomes a binary float before it reaches the column (CLAUDE.md).
     */
    private static BigDecimal decimal(Object raw) {
        if (raw instanceof BigDecimal) {
            return (BigDecimal) raw;
        }
        if (raw instanceof Number) {
            return new BigDecimal(raw.toString());
        }
        try {
            return new BigDecimal(String.valueOf(raw).trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static Object asLong(Value v) {
        final BigDecimal n = v.num() != null ? v.num() : decimal(v.text());
        if (n == null) {
            return v.text();
        }
        try {
            return n.longValueExact();
        } catch (ArithmeticException notAnInteger) {
            return n;
        }
    }

    private static Long epochOfDate(String text) {
        try {
            return LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        } catch (DateTimeParseException notADate) {
            return null;
        }
    }

    private static Long epochOfDateTime(String text) {
        try {
            return Instant.parse(text).toEpochMilli();
        } catch (DateTimeParseException notATimestamp) {
            return epochOfDate(text);
        }
    }

    // --- structure resolution ----------------------------------------------

    /** A property's structure: its xid and whether it is a loop (else a segment). */
    private record Ref(String xid, boolean loop) {
    }

    /**
     * Resolve a materialized property to its structure. The index's {@code structures} are
     * authoritative; anything it does not list is matched by reversing the materializer's
     * naming ({@code loop2100} → loop {@code 2100}, {@code clp} → segment {@code CLP}), which
     * is how a partner's undeclared segment still lands in the graph instead of vanishing.
     */
    private Ref resolve(StructureIndex.LoopEntry entry, String property) {
        if (entry != null) {
            for (StructureIndex.StructureRef ref : entry.structures) {
                if (property.equals(ref.name)) {
                    return new Ref(ref.xid, ref.isLoop());
                }
            }
        }
        if (index == null) {
            return null;
        }
        final String upper = property.toUpperCase(Locale.ROOT);
        if (index.segment(upper) != null) {
            return new Ref(upper, false);
        }
        final String loopXid = property.startsWith("loop") ? property.substring(4) : upper;
        if (index.loop(loopXid) != null) {
            return new Ref(loopXid, true);
        }
        if (index.loop(upper) != null) {
            return new Ref(upper, true);
        }
        return null;
    }

    private static StructureIndex.FieldEntry fieldFor(StructureIndex.SegmentEntry entry, String property) {
        if (entry == null) {
            return null;
        }
        for (StructureIndex.FieldEntry fe : entry.fields) {
            if (property.equals(propertyOf(entry.xid, fe))) {
                return fe;
            }
        }
        return null;
    }

    private static StructureIndex.FieldEntry fieldFor(StructureIndex.CompositeEntry entry, String property) {
        if (entry == null) {
            return null;
        }
        for (StructureIndex.FieldEntry fe : entry.fields) {
            if (property.equalsIgnoreCase(fe.xid) || property.endsWith(String.format("%02d", fe.seq))) {
                return fe;
            }
        }
        return null;
    }

    private static String propertyOf(String xid, StructureIndex.FieldEntry fe) {
        return xid.toLowerCase(Locale.ROOT) + String.format("%02d", fe.seq);
    }

    private static List<?> instances(Object value) {
        if (value instanceof List) {
            return (List<?>) value;
        }
        return value == null ? List.of() : List.of(value);
    }

    private static boolean repeats(Object value) {
        return value instanceof List;
    }

    private static String childPath(String parentPath, String property, int ordinal, boolean repeating) {
        final String leaf = repeating ? property + "[" + ordinal + "]" : property;
        return parentPath == null || parentPath.isEmpty() ? leaf : parentPath + "." + leaf;
    }

    // --- reassembly ---------------------------------------------------------

    /**
     * Walk the graph back into the nested materialized form. Single instances collapse to a
     * map and repeats stay a list, matching what the materializer produced — the round-trip
     * test asserts equality against it, which is what licenses storing rows instead of the
     * document.
     */
    public static Map<String, Object> assemble(List<Entity> entities) {
        if (entities == null || entities.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<Integer, List<Entity>> byParent = new LinkedHashMap<>();
        Entity root = null;
        for (Entity e : entities) {
            if (e.parentLocalId == null) {
                root = e;
            } else {
                byParent.computeIfAbsent(e.parentLocalId, k -> new ArrayList<>()).add(e);
            }
        }
        return root == null ? new LinkedHashMap<>() : assemble(root, byParent);
    }

    private static Map<String, Object> assemble(Entity self, Map<Integer, List<Entity>> byParent) {
        Map<String, List<Object>> children = new LinkedHashMap<>();
        Map<String, Boolean> repeating = new LinkedHashMap<>();
        for (Entity child : byParent.getOrDefault(self.localId, List.of())) {
            children.computeIfAbsent(child.property, k -> new ArrayList<>())
                .add(assemble(child, byParent));
            // a path leaf of "prop[n]" is how flatten recorded a repeat
            repeating.merge(child.property, child.path.endsWith("]"), (a, b) -> a || b);
        }
        Map<String, Value> scalars = new LinkedHashMap<>();
        for (Value v : self.values) {
            scalars.put(v.property(), v);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        for (String property : self.propertyOrder) {
            if (scalars.containsKey(property)) {
                put(out, scalars.remove(property));
            } else if (children.containsKey(property)) {
                putChild(out, property, children.remove(property), repeating);
            } else {
                // a repeating scalar was stored as "prop[n]" values, not under the bare name
                for (Value v : new ArrayList<>(scalars.values())) {
                    if (v.property().startsWith(property + "[")) {
                        put(out, v);
                        scalars.remove(v.property());
                    }
                }
            }
        }
        // anything the order list missed still has to come back
        for (Value v : scalars.values()) {
            put(out, v);
        }
        for (Map.Entry<String, List<Object>> e : children.entrySet()) {
            putChild(out, e.getKey(), e.getValue(), repeating);
        }
        return out;
    }

    private static void putChild(Map<String, Object> out, String property, List<Object> list,
            Map<String, Boolean> repeating) {
        out.put(property, Boolean.TRUE.equals(repeating.get(property)) || list.size() > 1
            ? list : list.get(0));
    }

    /**
     * The exact value for a stored type — parsed from the lexical form, so a decimal keeps
     * its scale ({@code 450.00}, not {@code 450.0}) and an integer stays an integer.
     * {@code null} for non-numeric types.
     */
    public static BigDecimal exactNumber(String dataType, String text) {
        if (dataType == null || text == null) {
            return null;
        }
        switch (dataType) {
            case "integer":
            case "decimal":
                return decimal(text);
            case "boolean":
                return Boolean.parseBoolean(text) || "1".equals(text) ? BigDecimal.ONE : BigDecimal.ZERO;
            default:
                return null;
        }
    }

    /** Scalars come back from {@code text} in their materialized Java type. */
    private static void put(Map<String, Object> out, Value v) {
        final Object value;
        switch (v.dataType()) {
            case "integer":
                // an integer element stays an integer; BigDecimal here would print 42.0
                value = asLong(v);
                break;
            case "decimal":
                value = v.num() != null ? v.num() : decimal(v.text());
                break;
            case "boolean":
                value = v.num() != null && v.num().signum() != 0;
                break;
            default:
                value = v.text();
                break;
        }
        final String property = v.property();
        final int bracket = property.indexOf('[');
        if (bracket < 0) {
            out.put(property, value);
            return;
        }
        final String base = property.substring(0, bracket);
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) out.computeIfAbsent(base, k -> new ArrayList<>());
        list.add(value);
    }
}

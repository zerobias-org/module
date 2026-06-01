package com.zerobias.module.hl7.materializer;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Composite;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Primitive;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Structure;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.Varies;
import ca.uhn.hl7v2.util.Terser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The full structure-index-driven materializer (DESIGN §5): walks a
 * <em>generic-parsed</em> message against the {@link StructureIndex} and emits the
 * complete typed JSON, keyed by HAPI-bean field names, with composites recursed
 * and primitives normalized.
 *
 * <p>Runtime uses generic parsing (no typed HAPI structure jars, DESIGN §4.1), so
 * we cannot reflect over typed getters — instead we read the index for the
 * naming/typing and read the actual parsed {@link Type} tree for what is present.
 * Output shape mirrors the §2.3 message schema: one property per segment
 * (lowercased code: {@code msh}, {@code pid}, …), each an object of its fields;
 * the buffer envelope ({@code controlId}/{@code receivedAt}/{@code status}/
 * {@code leaseId}) is overlaid later from the authoritative buffer columns.
 *
 * <p>v1 scope: group nesting is flattened — a message's segments are keyed at the
 * top level by segment code (repeating segments/segments in repeating groups
 * become arrays). The per-segment field/component graph is fully materialized.
 */
public final class Materializer implements MessageMaterializer {

    /** Sentinel: this field/component is absent (omit the key), distinct from explicit null. */
    private static final Object ABSENT = new Object();

    /** serializeNulls so an explicit-null component ({@code ""}) survives as JSON null. */
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private final StructureIndex index;
    private final StructureResolver resolver;

    public Materializer(StructureIndex index) {
        this(index, StructureResolver.DEFAULT);
    }

    public Materializer(StructureIndex index, StructureResolver resolver) {
        this.index = index;
        this.resolver = resolver;
    }

    @Override
    public String toTypedJson(Message message) throws HL7Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        for (SegmentSlot slot : segmentSlots(message)) {
            List<Object> occurrences = new ArrayList<>();
            for (Segment seg : segmentsNamed(message, slot.code)) {
                Object obj = materializeSegment(seg, index.segment(slot.code));
                if (obj != ABSENT) {
                    occurrences.add(obj);
                }
            }
            if (occurrences.isEmpty()) {
                continue;
            }
            out.put(slot.name, (slot.multi || occurrences.size() > 1) ? occurrences : occurrences.get(0));
        }
        return GSON.toJson(out);
    }

    // --- which segments, in what order, with what cardinality --------------

    private record SegmentSlot(String code, String name, boolean multi) {
    }

    /**
     * The ordered, de-duplicated segment slots to emit. Driven by the message's
     * structure entry when known (flattening groups, preserving order and OR-ing
     * multi); otherwise falls back to the segments actually present (occurrence
     * count decides array-ness).
     */
    private List<SegmentSlot> segmentSlots(Message message) throws HL7Exception {
        String structure = messageStructure(message);
        StructureIndex.MessageEntry entry = structure == null ? null : index.message(structure);

        Map<String, SegmentSlot> slots = new LinkedHashMap<>();
        if (entry != null) {
            collectSlots(entry, false, slots, new LinkedHashSet<>());
        }
        if (slots.isEmpty()) {
            // unknown structure (or empty entry): emit every present segment we can name
            for (String code : message.getNames()) {
                if (index.segment(code) != null && !slots.containsKey(code)) {
                    slots.put(code, new SegmentSlot(code, lower(code), false));
                }
            }
        }
        return new ArrayList<>(slots.values());
    }

    /** Flatten a message/group entry into segment slots, descending nested groups. */
    private void collectSlots(StructureIndex.MessageEntry entry, boolean inheritedMulti,
            Map<String, SegmentSlot> slots, Set<String> visitingGroups) {
        for (StructureIndex.StructureRef ref : entry.structures) {
            boolean multi = inheritedMulti || ref.multi;
            if (ref.group) {
                if (visitingGroups.add(ref.structure)) {           // cycle-safe
                    StructureIndex.MessageEntry g = index.groups.get(ref.structure);
                    if (g != null) {
                        collectSlots(g, multi, slots, visitingGroups);
                    }
                    visitingGroups.remove(ref.structure);
                }
            } else {
                SegmentSlot existing = slots.get(ref.structure);
                if (existing == null) {
                    slots.put(ref.structure, new SegmentSlot(ref.structure, ref.name, multi));
                } else if (multi && !existing.multi()) {
                    slots.put(ref.structure, new SegmentSlot(ref.structure, existing.name(), true));
                }
            }
        }
    }

    /**
     * Message structure name: the base from MSH-9 (prefer MSH-9-3, else
     * {@code <code>_<trigger>}), then run it through the {@link StructureResolver} so
     * an extension discriminator can route to an augmented structure (DESIGN §7.2).
     */
    private String messageStructure(Message message) throws HL7Exception {
        Terser t = new Terser(message);
        String code = t.get("/MSH-9-1");
        String trigger = t.get("/MSH-9-2");
        String struct = t.get("/MSH-9-3");
        String base = (struct != null && !struct.isEmpty()) ? struct
            : (code != null && !code.isEmpty() && trigger != null && !trigger.isEmpty())
                ? code + "_" + trigger : null;
        return resolver.resolve(code, t.get("/MSH-3"), base);
    }

    private List<Segment> segmentsNamed(Message message, String code) throws HL7Exception {
        if (!Arrays.asList(message.getNames()).contains(code)) {
            return List.of();
        }
        List<Segment> out = new ArrayList<>();
        for (Structure s : message.getAll(code)) {
            if (s instanceof Segment) {
                out.add((Segment) s);
            }
        }
        return out;
    }

    // --- segment / field / component walk ----------------------------------

    private Object materializeSegment(Segment seg, StructureIndex.SegmentEntry entry) throws HL7Exception {
        if (entry == null) {
            return ABSENT;   // unnamed (e.g. an unknown Z-segment with no schema) — skip
        }
        Map<String, Object> obj = new LinkedHashMap<>();
        int numFields = seg.numFields();
        for (StructureIndex.FieldEntry fe : entry.fields) {
            if (fe.field < 1 || fe.field > numFields) {
                continue;
            }
            Type[] reps = seg.getField(fe.field);
            if (reps.length == 0) {
                continue;
            }
            if (fe.multi) {
                List<Object> values = new ArrayList<>();
                for (Type rep : reps) {
                    Object v = materializeType(rep, fe.type);
                    if (v != ABSENT) {
                        values.add(v);
                    }
                }
                if (!values.isEmpty()) {
                    obj.put(fe.name, values);
                }
            } else {
                Object v = materializeType(reps[0], fe.type);
                if (v != ABSENT) {
                    obj.put(fe.name, v);
                }
            }
        }
        return obj.isEmpty() ? ABSENT : obj;
    }

    /**
     * Materialize a field/component value. Composite-ness is decided by the
     * <em>index type</em>, not by what the generic parser produced: an
     * under-delimited composite (e.g. {@code SMITH} for an FN-typed field, with no
     * {@code &} subcomponents) parses as a bare primitive, but the schema says it
     * is a composite — so we still emit the nested object, mapping the bare value
     * to the composite's first component.
     */
    private Object materializeType(Type type, String typeName) {
        Type t = type instanceof Varies ? ((Varies) type).getData() : type;
        StructureIndex.DatatypeEntry de = typeName == null ? null : index.datatype(typeName);

        if (de != null) {
            return materializeComposite(t, de);
        }
        // Index says primitive. Usually a Primitive; if the parser over-delimited
        // it into a composite, take the first component's value (no data loss).
        if (t instanceof Primitive) {
            return normalizePrimitive(((Primitive) t).getValue(), typeName);
        }
        if (t instanceof Composite) {
            Type[] cs = ((Composite) t).getComponents();
            return cs.length == 0 ? ABSENT : materializeType(cs[0], typeName);
        }
        return ABSENT;
    }

    /** Walk a composite's components by the index, naming each and recursing. */
    private Object materializeComposite(Type t, StructureIndex.DatatypeEntry de) {
        // A bare primitive in a composite-typed slot maps to component 1 (HL7 allows
        // writing only the leading component of a composite).
        Type[] components = (t instanceof Composite)
            ? ((Composite) t).getComponents()
            : new Type[] { t };

        Map<String, Object> obj = new LinkedHashMap<>();
        for (StructureIndex.ComponentEntry ce : de.components) {
            int idx = ce.component - 1;
            if (idx < 0 || idx >= components.length) {
                continue;
            }
            Object v = materializeType(components[idx], ce.type);
            if (v != ABSENT) {
                obj.put(ce.name, v);
            }
        }
        return obj.isEmpty() ? ABSENT : obj;
    }

    /**
     * Normalize a primitive value (DESIGN §5): date/time types → ISO 8601
     * (precision-preserving via {@link Hl7Normalizer}), {@code ""} → explicit null
     * (emitted), unset/empty → {@link #ABSENT} (key omitted), everything else →
     * escape-decoded string.
     */
    private Object normalizePrimitive(String raw, String typeName) {
        if (raw == null || raw.isEmpty()) {
            return ABSENT;
        }
        if (Hl7Normalizer.isExplicitNull(raw)) {
            return null;   // explicit HL7 null ("") — JSON null, key present
        }
        if (typeName == null) {
            return Hl7Normalizer.decodeEscapes(raw);
        }
        switch (typeName) {
            case "DT":
                return Hl7Normalizer.normalizeDate(raw);
            case "DTM":
            case "TS":
                return Hl7Normalizer.normalizeDateTime(raw);
            case "TM":
                return Hl7Normalizer.normalizeTime(raw);
            default:
                return Hl7Normalizer.decodeEscapes(raw);
        }
    }

    private static String lower(String s) {
        return s.toLowerCase(java.util.Locale.ROOT);
    }
}

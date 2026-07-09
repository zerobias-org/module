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
 * <p>Group nesting is preserved: the walk consumes the message's document-ordered
 * segment stream against the structure grammar, emitting nested group objects
 * (e.g. {@code patient_result -> patient / order_observation}) so group membership
 * — which OBX belongs to which OBR — survives, and every repeat is captured even
 * when HAPI's generic parse splits a non-contiguous repeat into a numeric-suffixed
 * slot ({@code OBX2}). The per-segment field/component graph is fully materialized.
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
        List<Seg> stream = segmentStream(message);
        String structure = messageStructure(message);
        StructureIndex.MessageEntry entry = structure == null ? null : index.message(structure);

        Map<String, Object> out;
        int[] cursor = {0};
        if (entry != null) {
            out = consume(entry, stream, cursor, new LinkedHashSet<>());
        } else {
            out = new LinkedHashMap<>();
        }
        // Any segments the grammar didn't place (unknown structure, unexpected order,
        // trailing Z-segments) are still emitted by code so nothing is dropped.
        appendLeftover(out, stream, cursor[0]);
        return GSON.toJson(out);
    }

    // --- document-ordered segment stream -----------------------------------

    /** A parsed segment plus its true HL7 code (HAPI suffixes non-contiguous repeats: {@code OBX2}). */
    private record Seg(String code, Segment segment) {
    }

    /**
     * The message's segments in document order, every occurrence included. HAPI's
     * generic parse gives a repeated segment that is interrupted by another segment
     * its own numeric-suffixed slot ({@code OBX2}); we recover the real code and keep
     * the occurrence, so no repeat is lost and group boundaries stay reconstructable.
     * Each slot's reps are themselves contiguous, so flattening
     * {@code getNames() × getAll()} reproduces true document order.
     *
     * <p>HL7 segment IDs are always exactly three characters and HAPI appends its
     * rep-suffix <em>after</em> the code, so the real code is the first three chars —
     * NOT the name with trailing digits stripped (that would mangle legitimate codes
     * ending in a digit, e.g. {@code PV1}, {@code PV2}, {@code OM1}).
     */
    private List<Seg> segmentStream(Message message) throws HL7Exception {
        List<Seg> stream = new ArrayList<>();
        for (String name : message.getNames()) {
            String code = name.length() > 3 ? name.substring(0, 3) : name;
            for (Structure s : message.getAll(name)) {
                if (s instanceof Segment) {
                    stream.add(new Seg(code, (Segment) s));
                }
            }
        }
        return stream;
    }

    // --- grammar-driven group assembly -------------------------------------

    /**
     * Consume the ordered segment stream against a message/group entry, emitting
     * nested group objects (DESIGN §5). A segment ref consumes leading occurrences of
     * its code up to cardinality; a group ref consumes as many group instances as
     * begin at the cursor (decided by the group's FIRST-set), each a nested object.
     * {@code cursor[0]} advances as segments are consumed. Preserves group membership
     * and every repeat.
     */
    private Map<String, Object> consume(StructureIndex.MessageEntry entry, List<Seg> stream,
            int[] cursor, Set<String> visiting) throws HL7Exception {
        Map<String, Object> obj = new LinkedHashMap<>();
        for (StructureIndex.StructureRef ref : entry.structures) {
            if (ref.group) {
                StructureIndex.MessageEntry g = index.groups.get(ref.structure);
                if (g == null || !visiting.add(ref.structure)) {
                    continue;   // missing definition or cyclic grammar — skip safely
                }
                List<Object> instances = new ArrayList<>();
                while (cursor[0] < stream.size()
                        && groupStarts(g, stream.get(cursor[0]).code(), new LinkedHashSet<>())) {
                    int before = cursor[0];
                    Map<String, Object> inst = consume(g, stream, cursor, visiting);
                    if (cursor[0] == before) {
                        break;   // consumed nothing this pass — don't spin
                    }
                    if (!inst.isEmpty()) {
                        instances.add(inst);
                    }
                    if (!ref.multi) {
                        break;
                    }
                }
                visiting.remove(ref.structure);
                if (!instances.isEmpty()) {
                    obj.put(ref.name, ref.multi ? instances : instances.get(0));
                }
            } else {
                List<Object> occ = new ArrayList<>();
                while (cursor[0] < stream.size() && stream.get(cursor[0]).code().equals(ref.structure)) {
                    Object v = materializeSegment(stream.get(cursor[0]).segment(), index.segment(ref.structure));
                    cursor[0]++;
                    if (v != ABSENT) {
                        occ.add(v);
                    }
                    if (!ref.multi) {
                        break;
                    }
                }
                if (!occ.isEmpty()) {
                    obj.put(ref.name, (ref.multi || occ.size() > 1) ? occ : occ.get(0));
                }
            }
        }
        return obj;
    }

    /**
     * Whether {@code code} may legally begin {@code entry} — its FIRST-set: leading
     * segment codes, descending into leading groups, stopping at the first required
     * element (nothing after a required element can be the entry's start).
     */
    private boolean groupStarts(StructureIndex.MessageEntry entry, String code, Set<String> visiting) {
        for (StructureIndex.StructureRef ref : entry.structures) {
            if (ref.group) {
                StructureIndex.MessageEntry g = index.groups.get(ref.structure);
                if (g != null && visiting.add(ref.structure) && groupStarts(g, code, visiting)) {
                    return true;
                }
            } else if (ref.structure.equals(code)) {
                return true;
            }
            if (ref.required) {
                return false;
            }
        }
        return false;
    }

    /** Emit not-yet-consumed segments by code (arrays for repeats), without clobbering placed keys. */
    private void appendLeftover(Map<String, Object> out, List<Seg> stream, int from) throws HL7Exception {
        Map<String, List<Object>> byCode = new LinkedHashMap<>();
        for (int i = from; i < stream.size(); i++) {
            Seg s = stream.get(i);
            Object v = materializeSegment(s.segment(), index.segment(s.code()));
            if (v != ABSENT) {
                byCode.computeIfAbsent(lower(s.code()), k -> new ArrayList<>()).add(v);
            }
        }
        for (Map.Entry<String, List<Object>> e : byCode.entrySet()) {
            out.putIfAbsent(e.getKey(), e.getValue().size() == 1 ? e.getValue().get(0) : e.getValue());
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

package com.zerobias.module.x12.codegen;

import com.zerobias.module.x12.codegen.mapping.Mapping;
import com.zerobias.module.x12.codegen.model.Property;
import com.zerobias.module.x12.codegen.model.Reference;
import com.zerobias.module.x12.codegen.model.Schema;
import com.zerobias.module.x12.codegen.model.StructureIndex;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Walks one pyx12 transaction map for one guide and accumulates the
 * DataProducer schemas (DESIGN §2.3/§2.4) plus the materializer structure index
 * (DESIGN §5).
 *
 * <p>Composition all the way down: the transaction table references the
 * children of {@code ST_LOOP}; a loop references its segments and nested loops;
 * a segment references its composites; elements map to core dataTypes and, when
 * the IG codes that position ({@code valid_codes} / {@code external=}), carry a
 * {@code schema:enum:} reference to its code set ({@link CodeRegistry} names it).
 *
 * <p><b>Merging.</b> A guide reuses xids: {@code REF} appears several times in
 * one loop with different qualifiers, {@code NM1} appears in every name loop,
 * and 837 nests loop {@code 2300} under both {@code 2000B} and {@code 2000C}.
 * Schema ids are per xid per guide (DESIGN §2.2), so each emitted loop/segment/
 * composite is the <em>union</em> of every use in the guide: children/fields
 * merged by property name (loops) or position (segments, composites);
 * {@code required} only when required in every use; {@code multi} when any use
 * repeats or the same xid occurs more than once under one parent.
 *
 * <p>Call {@link #walk(Mapping.Transaction)} once, then {@link #emit()} after
 * every guide has been walked (the code registry is global, so enum references
 * are decided at emit time).
 */
public final class StructureWalker {

    /** Loops whose content is the interchange/group envelope, not the atom. */
    public static final Set<String> ENVELOPE_LOOPS = Set.of("ISA_LOOP", "GS_LOOP");
    /** The loop whose subtree is the transaction-set atom (ST..SE). */
    public static final String TRANSACTION_LOOP = "ST_LOOP";

    /** Data element 1251: its wire format is named by the 1250 qualifier beside it, not by its type (AN). */
    static final String DATE_TIME_PERIOD = "1251";
    /** How the receiver's materializer writes a 1251 (X12Normalizer.dateTimePeriod). */
    static final String DATE_TIME_PERIOD_FORMAT = "ISO 8601 when the Date Time Period Format Qualifier (1250) beside"
        + " it is D8 (YYYY-MM-DD), RD8 (YYYY-MM-DD/YYYY-MM-DD) or DT (YYYY-MM-DDTHH:MM:SS); as sent otherwise";

    private final String gs08;
    private final String transactionType;
    private final String mapFile;
    private final Map<String, Mapping.DataElement> dataElements;
    private final CodeRegistry codes;

    private final Map<String, LoopAcc> loopAccs = new LinkedHashMap<>();
    private final Map<String, SegmentAcc> segmentAccs = new LinkedHashMap<>();
    private final Map<String, CompositeAcc> compositeAccs = new LinkedHashMap<>();
    private final Set<String> warnings = new LinkedHashSet<>();
    private Mapping.Transaction transaction;

    public StructureWalker(String gs08, String transactionType, String mapFile,
                           Map<String, Mapping.DataElement> dataElements, CodeRegistry codes) {
        this.gs08 = gs08;
        this.transactionType = transactionType;
        this.mapFile = mapFile;
        this.dataElements = dataElements;
        this.codes = codes;
    }

    public String gs08() {
        return gs08;
    }

    public String transactionType() {
        return transactionType;
    }

    // ---- accumulation -------------------------------------------------------

    public void walk(Mapping.Transaction t) {
        if (transaction != null) {
            throw new IllegalStateException("walker already used for " + transaction.xid());
        }
        transaction = t;
        walkLoop(t.root());
        if (!loopAccs.containsKey(TRANSACTION_LOOP)) {
            throw new IllegalStateException(gs08 + ": map " + mapFile + " has no " + TRANSACTION_LOOP);
        }
    }

    private void walkLoop(Mapping.Loop loop) {
        final LoopAcc acc = loopAccs.computeIfAbsent(loop.xid(), LoopAcc::new);
        acc.names.add(loop.name());
        acc.occurrences++;

        // Per-occurrence view first (same-parent duplicates fold here), then merge.
        final Map<String, ChildAcc> here = new LinkedHashMap<>();
        for (Mapping.Structure child : loop.children()) {
            final boolean isLoop = child instanceof Mapping.Loop;
            final String prop = isLoop ? Names.loopProperty(child.xid()) : Names.segmentProperty(child.xid());
            final String cardinality = isLoop ? ((Mapping.Loop) child).repeat() : ((Mapping.Segment) child).maxUse();
            final ChildAcc c = here.computeIfAbsent(prop, p -> new ChildAcc(p, child.xid(), isLoop ? "loop" : "segment",
                child.pos(), cardinality));
            c.count++;
            c.requiredAny |= "R".equals(child.usage());
            c.multiAny |= isMulti(cardinality);
            c.names.add(child.name());
        }
        for (ChildAcc c : here.values()) {
            final ChildAcc merged = acc.children.computeIfAbsent(c.name, p -> new ChildAcc(p, c.xid, c.kind, c.pos, c.repeat));
            merged.seenIn++;
            merged.requiredIn += c.requiredAny ? 1 : 0;
            merged.multiAny |= c.multiAny || c.count > 1;
            merged.names.addAll(c.names);
            if (!merged.xid.equals(c.xid)) {
                warn("loop " + loop.xid() + " property " + c.name + " maps both " + merged.xid + " and " + c.xid);
            }
        }
        for (Mapping.Structure child : loop.children()) {
            if (child instanceof Mapping.Loop l) {
                walkLoop(l);
            } else {
                walkSegment((Mapping.Segment) child);
            }
        }
    }

    private void walkSegment(Mapping.Segment seg) {
        final SegmentAcc acc = segmentAccs.computeIfAbsent(seg.xid(), SegmentAcc::new);
        acc.names.add(seg.name());
        acc.occurrences++;
        for (Mapping.Field f : seg.fields()) {
            mergeField(acc.fields, f, "segment " + seg.xid());
            if (f instanceof Mapping.Composite comp) {
                walkComposite(comp);
            }
        }
    }

    private void walkComposite(Mapping.Composite comp) {
        final CompositeAcc acc = compositeAccs.computeIfAbsent(comp.dataEle(), CompositeAcc::new);
        acc.names.add(comp.name());
        acc.occurrences++;
        for (Mapping.Element e : comp.elements()) {
            mergeField(acc.fields, e, "composite " + comp.dataEle());
        }
    }

    private void mergeField(Map<Integer, FieldAcc> fields, Mapping.Field f, String where) {
        final FieldAcc acc = fields.computeIfAbsent(f.seq(), s -> new FieldAcc(s, f.xid(), f.dataEle()));
        if (!acc.dataEle.equals(f.dataEle())) {
            warn(where + " seq " + f.seq() + " maps both data element " + acc.dataEle + " and " + f.dataEle()
                + " (keeping " + acc.dataEle + ")");
        }
        acc.seenIn++;
        acc.requiredIn += "R".equals(f.usage()) ? 1 : 0;
        acc.usedIn += "N".equals(f.usage()) ? 0 : 1;
        acc.names.add(f.name());
        if (f.repeat() != null) {
            acc.repeat = acc.repeat == null ? f.repeat() : Math.max(acc.repeat, f.repeat());
        }
        if (f instanceof Mapping.Composite comp) {
            acc.composite = comp.dataEle();
        } else {
            acc.codeSources.addAll(codes.register(gs08, (Mapping.Element) f));
        }
    }

    // ---- emission -----------------------------------------------------------

    /** Everything generated for one guide. */
    public static final class Generated {
        public final Schema table;
        public final Map<String, Schema> loops = new LinkedHashMap<>();
        public final Map<String, Schema> segments = new LinkedHashMap<>();
        public final Map<String, Schema> composites = new LinkedHashMap<>();
        public final StructureIndex index = new StructureIndex();

        Generated(Schema table) {
            this.table = table;
        }

        /** Every schema, table first. */
        public List<Schema> all() {
            final List<Schema> out = new ArrayList<>();
            out.add(table);
            out.addAll(loops.values());
            out.addAll(segments.values());
            out.addAll(composites.values());
            return out;
        }
    }

    /** Build the schemas + index from the accumulated map. Envelope properties are NOT appended here. */
    public Generated emit() {
        if (transaction == null) {
            throw new IllegalStateException("walk() first");
        }
        final Generated g = new Generated(new Schema(SchemaIds.requireValid(SchemaIds.table(gs08, transactionType))));
        final StructureIndex index = g.index;
        index.gs08 = gs08;
        index.transactionType = transactionType;
        index.transactionXid = transaction.xid();
        index.mapFile = com.zerobias.module.x12.codegen.mapping.MappingLoader.RESOURCE_DIR + mapFile;
        index.transactionLoop = TRANSACTION_LOOP;
        index.tableSchemaId = g.table.id;

        for (LoopAcc acc : loopAccs.values()) {
            final boolean envelope = ENVELOPE_LOOPS.contains(acc.xid);
            final boolean root = TRANSACTION_LOOP.equals(acc.xid);
            final Schema target;
            if (root) {
                target = g.table;
            } else if (envelope) {
                target = null;
            } else {
                target = new Schema(SchemaIds.requireValid(SchemaIds.type(gs08, acc.xid)));
                g.loops.put(acc.xid, target);
            }
            final StructureIndex.LoopEntry entry = new StructureIndex.LoopEntry();
            entry.xid = acc.xid;
            entry.name = joinNames(acc.names);
            entry.schemaId = target == null ? null : target.id;
            for (ChildAcc c : acc.children.values()) {
                final boolean required = c.seenIn == acc.occurrences && c.requiredIn == acc.occurrences;
                entry.structures.add(new StructureIndex.StructureRef(c.name, c.xid, c.kind, required, c.multiAny, c.pos, c.repeat));
                if (target != null) {
                    target.properties.add(new Property(c.name, CoreTypes.STRING)
                        .description(joinNames(c.names))
                        .required(required)
                        .multi(c.multiAny)
                        .references(new Reference(SchemaIds.type(gs08, c.xid))));
                }
            }
            index.loops.put(acc.xid, entry);
        }

        for (SegmentAcc acc : segmentAccs.values()) {
            final Schema s = new Schema(SchemaIds.requireValid(SchemaIds.type(gs08, acc.xid)));
            final StructureIndex.SegmentEntry entry = new StructureIndex.SegmentEntry();
            entry.xid = acc.xid;
            entry.name = joinNames(acc.names);
            entry.schemaId = s.id;
            for (FieldAcc f : acc.fields.values()) {
                final String prop = Names.segmentProperty(acc.xid) + String.format("%02d", f.seq);
                emitField(s, entry.fields, f, prop, acc.occurrences);
            }
            g.segments.put(acc.xid, s);
            index.segments.put(acc.xid, entry);
        }

        for (CompositeAcc acc : compositeAccs.values()) {
            final Schema s = new Schema(SchemaIds.requireValid(SchemaIds.type(gs08, acc.dataEle)));
            final StructureIndex.CompositeEntry entry = new StructureIndex.CompositeEntry();
            entry.dataEle = acc.dataEle;
            entry.name = joinNames(acc.names);
            entry.schemaId = s.id;
            for (FieldAcc f : acc.fields.values()) {
                emitField(s, entry.fields, f, Names.compositeElementProperty(acc.dataEle, f.seq), acc.occurrences);
            }
            g.composites.put(acc.dataEle, s);
            index.composites.put(acc.dataEle, entry);
        }

        for (String w : warnings) {
            System.err.println("  warn " + gs08 + ": " + w);
        }
        return g;
    }

    private void emitField(Schema schema, List<StructureIndex.FieldEntry> fields, FieldAcc f, String prop, int occurrences) {
        final boolean required = f.seenIn == occurrences && f.requiredIn == occurrences;
        final boolean multi = f.repeat != null && f.repeat > 1;
        final StructureIndex.FieldEntry e = new StructureIndex.FieldEntry();
        e.seq = f.seq;
        e.name = prop;
        e.xid = f.xid;
        e.dataEle = f.dataEle;
        e.required = required;
        e.multi = multi;
        e.repeat = f.repeat;
        e.usage = required ? "R" : (f.usedIn > 0 ? "S" : "N");

        final Property p;
        if (f.composite != null) {
            e.composite = f.composite;
            p = new Property(prop, CoreTypes.STRING)
                .references(new Reference(SchemaIds.type(gs08, f.composite)));
        } else {
            final Mapping.DataElement de = dataElements.get(f.dataEle);
            if (de == null) {
                warn("data element " + f.dataEle + " (" + f.xid + ") missing from dataele.xml; treated as AN");
            }
            final String x12Type = de == null ? "AN" : de.type();
            final boolean controlNumber = CoreTypes.CONTROL_NUMBERS.contains(f.dataEle);
            final String core = controlNumber ? CoreTypes.STRING : CoreTypes.forX12(x12Type);
            if (de != null && !isKnownType(x12Type)) {
                warn("data element " + f.dataEle + " has unknown data_type '" + x12Type + "'; treated as string");
            }
            e.x12Type = x12Type;
            e.coreType = core;
            e.impliedDecimals = controlNumber ? null : CoreTypes.impliedDecimals(x12Type);
            e.minLen = de == null ? null : de.minLen();
            e.maxLen = de == null ? null : de.maxLen();
            p = new Property(prop, core).format(CoreTypes.formatHint(x12Type));
            // Enum reference only where the IG codes THIS position (valid_codes / external=),
            // never just because the data element has a value set somewhere else.
            if (!f.codeSources.isEmpty() && CoreTypes.STRING.equals(core)) {
                final String key = codes.bind(f.dataEle, f.codeSources);
                if (key != null) {
                    e.codes = key;
                    p.references(new Reference(SchemaIds.codes(key)));
                }
            }
        }
        String description = describe(f);
        if (DATE_TIME_PERIOD.equals(f.dataEle)) {
            description = (description == null ? "" : description + ". ") + DATE_TIME_PERIOD_FORMAT;
        }
        p.description(description).required(required).multi(multi);
        schema.properties.add(p);
        fields.add(e);
    }

    /** The IG name when all uses agree, else the generic dataele.xml name (fallback: first IG name). */
    private String describe(FieldAcc f) {
        if (f.names.size() == 1) {
            return f.names.iterator().next();
        }
        final Mapping.DataElement de = dataElements.get(f.dataEle);
        if (de != null && de.name() != null && !de.name().isBlank()) {
            return de.name();
        }
        return f.names.isEmpty() ? null : f.names.iterator().next();
    }

    private static boolean isKnownType(String t) {
        return "AN".equals(t) || "ID".equals(t) || "R".equals(t) || "DT".equals(t) || "TM".equals(t) || "B".equals(t)
            || CoreTypes.isImpliedDecimal(t);
    }

    private static boolean isMulti(String cardinality) {
        return cardinality != null && !"1".equals(cardinality.trim());
    }

    private static String joinNames(Set<String> names) {
        final List<String> ns = new ArrayList<>();
        for (String n : names) {
            if (n != null && !n.isBlank()) {
                ns.add(n);
            }
        }
        return ns.isEmpty() ? null : String.join(" | ", ns);
    }

    private void warn(String msg) {
        warnings.add(msg);
    }

    // ---- accumulators -------------------------------------------------------

    private static final class LoopAcc {
        final String xid;
        final Set<String> names = new LinkedHashSet<>();
        final Map<String, ChildAcc> children = new LinkedHashMap<>();
        int occurrences;

        LoopAcc(String xid) {
            this.xid = xid;
        }
    }

    private static final class ChildAcc {
        final String name;
        final String xid;
        final String kind;
        final String pos;
        final String repeat;
        final Set<String> names = new LinkedHashSet<>();
        int count;          // occurrences within one parent occurrence
        boolean requiredAny;
        boolean multiAny;
        int seenIn;         // parent occurrences containing this child
        int requiredIn;     // parent occurrences where the child is required

        ChildAcc(String name, String xid, String kind, String pos, String repeat) {
            this.name = name;
            this.xid = xid;
            this.kind = kind;
            this.pos = pos;
            this.repeat = repeat;
        }
    }

    private static final class SegmentAcc {
        final String xid;
        final Set<String> names = new LinkedHashSet<>();
        final Map<Integer, FieldAcc> fields = new TreeMap<>();
        int occurrences;

        SegmentAcc(String xid) {
            this.xid = xid;
        }
    }

    private static final class CompositeAcc {
        final String dataEle;
        final Set<String> names = new LinkedHashSet<>();
        final Map<Integer, FieldAcc> fields = new TreeMap<>();
        int occurrences;

        CompositeAcc(String dataEle) {
            this.dataEle = dataEle;
        }
    }

    private static final class FieldAcc {
        final int seq;
        final String xid;
        final String dataEle;
        final Set<String> names = new LinkedHashSet<>();
        int seenIn;
        int requiredIn;
        int usedIn;
        Integer repeat;
        String composite;
        final Set<String> codeSources = new TreeSet<>();   // CodeRegistry.register over every use

        FieldAcc(int seq, String xid, String dataEle) {
            this.seq = seq;
            this.xid = xid;
            this.dataEle = dataEle;
        }
    }
}

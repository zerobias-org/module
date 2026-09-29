package com.zerobias.module.x12.materializer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imsweb.x12.Element;
import com.imsweb.x12.Loop;
import com.imsweb.x12.Segment;
import com.zerobias.module.x12.parser.Separators;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * imsweb {@link Loop} tree → typed JSON (DESIGN §5), walked by the {@link StructureIndex}
 * — never by {@code Loop.toJson()}. For each loop: {@code {"<segXid lower>": {...},
 * "loop<xid>": [...]}} in index order; for each segment: {@code {"<xid lower><nn>": value}}
 * with {@link X12Normalizer} applied per element type; composites nest using the index's
 * composite layout ({@code c00301}, ...); a segment or loop is an array exactly when the index
 * says {@code multi}, so the JSON always has the schema's shape — a second occurrence of a
 * single-use segment or loop is left out of the JSON (the raw keeps it, and imsweb has already
 * reported it as a non-fatal "appears too many times" parser error). {@code ^}-repeated
 * elements are split with the file's repetition separator (imsweb does not) and emitted as
 * arrays. Empty elements are omitted. Anything the index does not know (a segment imsweb placed
 * that the map lacks, trailing elements beyond the layout) is still emitted under its generic
 * key so nothing is dropped.
 */
public final class Materializer {

    private static final Gson GSON = new GsonBuilder().create();

    private final StructureIndex index;
    private final Separators separators;

    public Materializer(StructureIndex index, Separators separators) {
        this.index = index;
        this.separators = separators == null ? Separators.DEFAULT : separators;
    }

    public StructureIndex index() {
        return index;
    }

    /** The transaction-set atom ({@code ST_LOOP}) as an ordered map: {@code st, header, detail, ..., se}. */
    public Map<String, Object> materializeTransaction(Loop stLoop) {
        StructureIndex.LoopEntry entry = index.transactionEntry();
        return materializeLoop(stLoop, entry);
    }

    public String toJson(Map<String, Object> tree) {
        return GSON.toJson(tree);
    }

    /** One loop by its index entry ({@code null} entry → generic walk). */
    public Map<String, Object> materializeLoop(Loop loop, StructureIndex.LoopEntry entry) {
        Map<String, Object> out = new LinkedHashMap<>();
        Set<String> placedSegments = new HashSet<>();
        Set<String> placedLoops = new HashSet<>();
        if (entry != null) {
            for (StructureIndex.StructureRef ref : entry.structures) {
                if (ref.isLoop()) {
                    List<Object> instances = new ArrayList<>();
                    StructureIndex.LoopEntry child = index.loop(ref.xid);
                    for (Loop l : loop.getLoops()) {
                        if (ref.xid.equals(l.getId())) {
                            Map<String, Object> m = materializeLoop(l, child);
                            if (!m.isEmpty()) {
                                instances.add(m);
                            }
                        }
                    }
                    placedLoops.add(ref.xid);
                    putIndexed(out, ref, instances);
                } else {
                    List<Object> occ = new ArrayList<>();
                    StructureIndex.SegmentEntry se = index.segment(ref.xid);
                    for (Segment s : loop.getSegments()) {
                        if (ref.xid.equals(s.getId())) {
                            Map<String, Object> m = materializeSegment(s, se);
                            if (!m.isEmpty()) {
                                occ.add(m);
                            }
                        }
                    }
                    placedSegments.add(ref.xid);
                    putIndexed(out, ref, occ);
                }
            }
        }
        // Leftovers: segments/loops the map does not list under this loop.
        Map<String, List<Object>> extraSegs = new LinkedHashMap<>();
        for (Segment s : loop.getSegments()) {
            if (!placedSegments.contains(s.getId())) {
                Map<String, Object> m = materializeSegment(s, index.segment(s.getId()));
                if (!m.isEmpty()) {
                    extraSegs.computeIfAbsent(segmentProperty(s.getId()), k -> new ArrayList<>()).add(m);
                }
            }
        }
        for (Map.Entry<String, List<Object>> e : extraSegs.entrySet()) {
            putLeftover(out, e.getKey(), e.getValue());
        }
        Map<String, List<Object>> extraLoops = new LinkedHashMap<>();
        for (Loop l : loop.getLoops()) {
            if (!placedLoops.contains(l.getId())) {
                Map<String, Object> m = materializeLoop(l, index.loop(l.getId()));
                if (!m.isEmpty()) {
                    extraLoops.computeIfAbsent(index.loopProperty(l.getId()), k -> new ArrayList<>()).add(m);
                }
            }
        }
        for (Map.Entry<String, List<Object>> e : extraLoops.entrySet()) {
            putLeftover(out, e.getKey(), e.getValue());
        }
        return out;
    }

    /** The schema's shape: an array when the index says {@code multi}, else the first occurrence. */
    private static void putIndexed(Map<String, Object> out, StructureIndex.StructureRef ref, List<Object> values) {
        if (!values.isEmpty()) {
            out.put(ref.name, ref.multi ? values : values.get(0));
        }
    }

    /** No schema to follow: keep every occurrence, as an array when there are several. */
    private static void putLeftover(Map<String, Object> out, String name, List<Object> values) {
        if (!values.isEmpty() && !out.containsKey(name)) {
            out.put(name, values.size() > 1 ? values : values.get(0));
        }
    }

    /** One segment by its index entry ({@code null} entry → every element as a trimmed string). */
    public Map<String, Object> materializeSegment(Segment seg, StructureIndex.SegmentEntry entry) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Element> elements = seg.getElements();
        String prefix = segmentProperty(seg.getId());
        int covered = 0;
        if (entry != null) {
            String periodFormat = periodFormat(entry.fields, elements.size(), i -> elements.get(i - 1).getValue());
            for (StructureIndex.FieldEntry fe : entry.fields) {
                covered = Math.max(covered, fe.seq);
                if (fe.seq < 1 || fe.seq > elements.size()) {
                    continue;
                }
                String raw = elements.get(fe.seq - 1).getValue();
                Object v = materializeField(raw, fe, periodFormat);
                if (v != null) {
                    out.put(fe.name, v);
                }
            }
        }
        for (int i = covered; i < elements.size(); i++) {
            String raw = elements.get(i).getValue();
            if (raw != null && !raw.trim().isEmpty()) {
                out.put(prefix + String.format("%02d", i + 1), raw.trim());
            }
        }
        return out;
    }

    /**
     * The value of the Date Time Period Format Qualifier (1250) among {@code fields}, which says
     * how the 1251 beside it (DTP02/DTP03, DMG01/DMG02, HI0n-03/-04) is written; null when absent.
     */
    private static String periodFormat(List<StructureIndex.FieldEntry> fields, int present, IntFunction<String> valueAt) {
        for (StructureIndex.FieldEntry fe : fields) {
            if (X12Normalizer.DATE_TIME_FORMAT_QUALIFIER.equals(fe.dataEle) && fe.seq >= 1 && fe.seq <= present) {
                return valueAt.apply(fe.seq);
            }
        }
        return null;
    }

    /** A field value: composite object, normalized scalar, or an array of either when the element repeats. */
    private Object materializeField(String raw, StructureIndex.FieldEntry fe, String periodFormat) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        boolean repeats = fe.multi || (fe.repeat != null && fe.repeat > 1);
        if (repeats && separators.hasRepetition() && raw.indexOf(separators.repetition()) >= 0) {
            List<Object> reps = new ArrayList<>();
            for (String piece : separators.splitRepetitions(raw)) {
                Object v = materializeSingle(piece, fe, periodFormat);
                if (v != null) {
                    reps.add(v);
                }
            }
            return reps.isEmpty() ? null : reps;
        }
        Object v = materializeSingle(raw, fe, periodFormat);
        return (v != null && repeats) ? List.of(v) : v;
    }

    private Object materializeSingle(String raw, StructureIndex.FieldEntry fe, String periodFormat) {
        if (fe.composite != null) {
            return materializeComposite(raw, index.composite(fe.composite));
        }
        return scalar(raw, fe, periodFormat);
    }

    private static Object scalar(String raw, StructureIndex.FieldEntry fe, String periodFormat) {
        if (X12Normalizer.DATE_TIME_PERIOD.equals(fe.dataEle) && raw != null && !raw.trim().isEmpty()) {
            return X12Normalizer.dateTimePeriod(raw, periodFormat);
        }
        return X12Normalizer.normalize(raw, fe.x12Type, fe.coreType, fe.impliedDecimals);
    }

    private Object materializeComposite(String raw, StructureIndex.CompositeEntry ce) {
        String[] parts = separators.splitComponents(raw);
        Map<String, Object> out = new LinkedHashMap<>();
        int covered = 0;
        if (ce != null) {
            String periodFormat = periodFormat(ce.fields, parts.length, i -> parts[i - 1]);
            for (StructureIndex.FieldEntry fe : ce.fields) {
                covered = Math.max(covered, fe.seq);
                if (fe.seq < 1 || fe.seq > parts.length) {
                    continue;
                }
                Object v = scalar(parts[fe.seq - 1], fe, periodFormat);
                if (v != null) {
                    out.put(fe.name, v);
                }
            }
        }
        String prefix = ce == null ? "c" : ce.dataEle.toLowerCase(Locale.ROOT);
        for (int i = covered; i < parts.length; i++) {
            if (!parts[i].trim().isEmpty()) {
                out.put(prefix + String.format("%02d", i + 1), parts[i].trim());
            }
        }
        if (out.isEmpty()) {
            // A composite slot carrying a bare value with no components: keep it as a string.
            return raw.trim().isEmpty() ? null : raw.trim();
        }
        return out;
    }

    /** The generic key of a segment the index does not describe; the codegen names segments the same way. */
    static String segmentProperty(String xid) {
        return xid.toLowerCase(Locale.ROOT);
    }
}

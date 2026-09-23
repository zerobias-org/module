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

/**
 * imsweb {@link Loop} tree → typed JSON (DESIGN §5), walked by the {@link StructureIndex}
 * — never by {@code Loop.toJson()}. For each loop: {@code {"<segXid lower>": {...},
 * "loop<xid>": [...]}} in index order; for each segment: {@code {"<xid lower><nn>": value}}
 * with {@link X12Normalizer} applied per element type; composites nest using the index's
 * composite layout ({@code c00301}, ...); repeated segments/loops are arrays when the index
 * says {@code multi} (or when more than one occurrence is present); {@code ^}-repeated
 * elements are split with the file's repetition separator (imsweb does not) and emitted as
 * arrays. Empty elements are omitted. Anything the index does not know (a segment or loop
 * imsweb placed that the merged map lacks, trailing elements beyond the layout) is still
 * emitted under its generic key so nothing is dropped.
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
                    put(out, ref.name, instances, ref.multi);
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
                    put(out, ref.name, occ, ref.multi);
                }
            }
        }
        // Leftovers: segments/loops the (merged) map does not list under this loop.
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
            if (!out.containsKey(e.getKey())) {
                put(out, e.getKey(), e.getValue(), false);
            }
        }
        Map<String, List<Object>> extraLoops = new LinkedHashMap<>();
        for (Loop l : loop.getLoops()) {
            if (!placedLoops.contains(l.getId())) {
                Map<String, Object> m = materializeLoop(l, index.loop(l.getId()));
                if (!m.isEmpty()) {
                    extraLoops.computeIfAbsent(loopProperty(l.getId()), k -> new ArrayList<>()).add(m);
                }
            }
        }
        for (Map.Entry<String, List<Object>> e : extraLoops.entrySet()) {
            if (!out.containsKey(e.getKey())) {
                put(out, e.getKey(), e.getValue(), false);
            }
        }
        return out;
    }

    private static void put(Map<String, Object> out, String name, List<Object> values, boolean multi) {
        if (values.isEmpty()) {
            return;
        }
        out.put(name, (multi || values.size() > 1) ? values : values.get(0));
    }

    /** One segment by its index entry ({@code null} entry → every element as a trimmed string). */
    public Map<String, Object> materializeSegment(Segment seg, StructureIndex.SegmentEntry entry) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Element> elements = seg.getElements();
        String prefix = segmentProperty(seg.getId());
        int covered = 0;
        if (entry != null) {
            for (StructureIndex.FieldEntry fe : entry.fields) {
                covered = Math.max(covered, fe.seq);
                if (fe.seq < 1 || fe.seq > elements.size()) {
                    continue;
                }
                String raw = elements.get(fe.seq - 1).getValue();
                Object v = materializeField(raw, fe);
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

    /** A field value: composite object, normalized scalar, or an array of either when the element repeats. */
    private Object materializeField(String raw, StructureIndex.FieldEntry fe) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        boolean repeats = fe.multi || (fe.repeat != null && fe.repeat > 1);
        if (repeats && separators.hasRepetition() && raw.indexOf(separators.repetition()) >= 0) {
            List<Object> reps = new ArrayList<>();
            for (String piece : separators.splitRepetitions(raw)) {
                Object v = materializeSingle(piece, fe);
                if (v != null) {
                    reps.add(v);
                }
            }
            return reps.isEmpty() ? null : reps;
        }
        Object v = materializeSingle(raw, fe);
        return (v != null && repeats) ? List.of(v) : v;
    }

    private Object materializeSingle(String raw, StructureIndex.FieldEntry fe) {
        if (fe.composite != null) {
            return materializeComposite(raw, index.composite(fe.composite));
        }
        return X12Normalizer.normalize(raw, fe.x12Type, fe.impliedDecimals);
    }

    private Object materializeComposite(String raw, StructureIndex.CompositeEntry ce) {
        String[] parts = separators.splitComponents(raw);
        Map<String, Object> out = new LinkedHashMap<>();
        int covered = 0;
        if (ce != null) {
            for (StructureIndex.FieldEntry fe : ce.fields) {
                covered = Math.max(covered, fe.seq);
                if (fe.seq < 1 || fe.seq > parts.length) {
                    continue;
                }
                Object v = X12Normalizer.normalize(parts[fe.seq - 1], fe.x12Type, fe.impliedDecimals);
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

    // ---- naming (mirror of the codegen's Names) -------------------------------------

    static String segmentProperty(String xid) {
        return xid.toLowerCase(Locale.ROOT);
    }

    static String loopProperty(String xid) {
        if (xid == null || xid.isEmpty()) {
            return "loop";
        }
        if (Character.isDigit(xid.charAt(0))) {
            return "loop" + xid;
        }
        StringBuilder sb = new StringBuilder();
        boolean upNext = false;
        for (char ch : xid.toCharArray()) {
            if (!Character.isLetterOrDigit(ch)) {
                upNext = sb.length() > 0;
                continue;
            }
            if (upNext) {
                sb.append(Character.toUpperCase(ch));
                upNext = false;
            } else {
                sb.append(Character.toLowerCase(ch));
            }
        }
        return sb.toString();
    }
}

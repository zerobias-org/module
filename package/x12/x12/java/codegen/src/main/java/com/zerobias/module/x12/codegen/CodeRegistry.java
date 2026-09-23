package com.zerobias.module.x12.codegen;

import com.zerobias.module.x12.codegen.mapping.Mapping;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Accumulates the value set of every coded data element across all guides
 * (DESIGN §2.4): the union of each element's inline {@code valid_codes}, plus
 * the {@code codes.xml} codesets an element pulls in via
 * {@code valid_codes/@external}. Codesets are deliberately NOT bound by their
 * own declared {@code data_ele}: {@code remark_code} declares 127, the generic
 * "Reference Identification" element used by REF02/CLP07/TRN02, and only the
 * positions the IG marks (MOA/LQ remark codes) may reference it. One
 * {@code schema:enum:x12.codes.<dataEle>} is emitted per entry after all guides
 * are walked; whether a given field references it is the walker's per-position
 * decision.
 */
public final class CodeRegistry {

    /** dataEle → sorted code values. */
    private final Map<String, Set<String>> codes = new TreeMap<>();
    /** dataEle → codes.xml codeset ids that contributed (for the enum description). */
    private final Map<String, Set<String>> codesets = new TreeMap<>();
    /** dataEle → guides whose inline valid_codes contributed. */
    private final Map<String, Set<String>> guides = new TreeMap<>();
    private final Map<String, Mapping.CodeSet> external = new LinkedHashMap<>();

    public CodeRegistry(Map<String, Mapping.CodeSet> codeSets) {
        external.putAll(codeSets);
    }

    /**
     * Record an element's inline codes / external reference for the guide being
     * walked. Returns true when the element contributed a value set (i.e. the IG
     * marks this position as coded).
     */
    public boolean register(String gs08, Mapping.Element e) {
        boolean any = false;
        if (!e.validCodes().isEmpty()) {
            bucket(codes, e.dataEle()).addAll(e.validCodes());
            any = true;
        }
        if (e.externalCodes() != null) {
            final Mapping.CodeSet cs = external.get(e.externalCodes());
            if (cs == null) {
                System.err.printf("  warn %s: %s references unknown external codeset '%s'%n",
                    gs08, e.xid(), e.externalCodes());
            } else {
                addCodeSet(e.dataEle(), cs);
                any = true;
            }
        }
        if (any) {
            bucket(guides, e.dataEle()).add(gs08);
        }
        return any;
    }

    private void addCodeSet(String dataEle, Mapping.CodeSet cs) {
        bucket(codes, dataEle).addAll(cs.codes());
        bucket(codesets, dataEle).add(cs.id());
    }

    public boolean hasCodes(String dataEle) {
        final Set<String> s = codes.get(dataEle);
        return s != null && !s.isEmpty();
    }

    /** All data elements with a non-empty value set, sorted. */
    public Set<String> dataElements() {
        final Set<String> out = new TreeSet<>();
        for (Map.Entry<String, Set<String>> e : codes.entrySet()) {
            if (!e.getValue().isEmpty()) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    public Set<String> codesOf(String dataEle) {
        return codes.getOrDefault(dataEle, Set.of());
    }

    public Set<String> codesetsOf(String dataEle) {
        return codesets.getOrDefault(dataEle, Set.of());
    }

    public Set<String> guidesOf(String dataEle) {
        return guides.getOrDefault(dataEle, Set.of());
    }

    private static Set<String> bucket(Map<String, Set<String>> m, String key) {
        return m.computeIfAbsent(key, k -> new TreeSet<>());
    }

    /** Codeset ids known from codes.xml (for diagnostics). */
    public Set<String> externalIds() {
        return new LinkedHashSet<>(external.keySet());
    }
}

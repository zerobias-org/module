package com.zerobias.module.x12.codegen;

import com.zerobias.module.x12.codegen.mapping.Mapping;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Accumulates the value sets of the coded element positions across all guides (DESIGN §2.4)
 * and names the {@code schema:enum:x12.codes.<key>} each position references:
 * <ul>
 *   <li>a position whose uses all point at one {@code codes.xml} codeset
 *       ({@code valid_codes/@external}) references that codeset by its own id
 *       ({@code x12.codes.claim_status}), so two codesets on the same data element (277CA
 *       STC01-01 {@code claim_status_cat} and STC01-02 {@code claim_status}, both 1271) stay
 *       two enums;</li>
 *   <li>a position coded inline ({@code <valid_codes><code>}) references its data element
 *       ({@code x12.codes.1029}), the union of every inline list for that element;</li>
 *   <li>a position whose uses mix sources (277CA C043-03 is inline in some loops and
 *       {@code entity_id} in others) references its data element too, and that enum absorbs
 *       the codesets involved — the position really accepts all of them.</li>
 * </ul>
 * Codesets are deliberately NOT bound by their own declared {@code data_ele}:
 * {@code remark_code} declares 127, the generic "Reference Identification" element used by
 * REF02/CLP07/TRN02, and only the positions the IG marks (MOA/LQ remark codes) reference it.
 *
 * <p>{@link #register} runs per use while the guides are walked; {@link #bind} runs per merged
 * position at emit time, so every enum's final value set is known before any is written.
 */
public final class CodeRegistry {

    /** The source {@link #register} reports for inline {@code valid_codes}. */
    private static final String INLINE = "";

    /** Enum key (data element or codeset id) → sorted code values. */
    private final Map<String, Set<String>> codes = new TreeMap<>();
    /** Enum key → codes.xml codeset ids that contributed (for the enum description). */
    private final Map<String, Set<String>> codesets = new TreeMap<>();
    /** Data element → guides whose inline valid_codes contributed. */
    private final Map<String, Set<String>> guides = new TreeMap<>();
    private final Map<String, Mapping.CodeSet> external = new LinkedHashMap<>();

    public CodeRegistry(Map<String, Mapping.CodeSet> codeSets) {
        external.putAll(codeSets);
    }

    /**
     * Record one use of an element for the guide being walked: its inline codes join the data
     * element's set. Returns where the IG takes this use's values from — {@link #INLINE} and/or
     * a codeset id; empty when the IG does not code the position.
     */
    public Set<String> register(String gs08, Mapping.Element e) {
        final Set<String> sources = new TreeSet<>();
        if (!e.validCodes().isEmpty()) {
            bucket(codes, e.dataEle()).addAll(e.validCodes());
            bucket(guides, e.dataEle()).add(gs08);
            sources.add(INLINE);
        }
        if (e.externalCodes() != null) {
            if (external.containsKey(e.externalCodes())) {
                sources.add(e.externalCodes());
            } else {
                System.err.printf("  warn %s: %s references unknown external codeset '%s'%n",
                    gs08, e.xid(), e.externalCodes());
            }
        }
        return sources;
    }

    /**
     * The enum key for a merged position whose uses drew on {@code sources}, recording the value
     * set under it; null when that set is empty.
     */
    public String bind(String dataEle, Set<String> sources) {
        final String key = sources.size() == 1 && !sources.contains(INLINE) ? sources.iterator().next() : dataEle;
        for (String source : sources) {
            if (!INLINE.equals(source)) {
                final Mapping.CodeSet cs = external.get(source);
                bucket(codes, key).addAll(cs.codes());
                bucket(codesets, key).add(cs.id());
            }
        }
        return hasCodes(key) ? key : null;
    }

    public boolean hasCodes(String key) {
        final Set<String> s = codes.get(key);
        return s != null && !s.isEmpty();
    }

    /** All enum keys with a non-empty value set, sorted. */
    public Set<String> keys() {
        final Set<String> out = new TreeSet<>();
        for (Map.Entry<String, Set<String>> e : codes.entrySet()) {
            if (!e.getValue().isEmpty()) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    /** The codes.xml codeset an enum is named after, or null when the key is a data element. */
    public Mapping.CodeSet codeSet(String key) {
        return external.get(key);
    }

    public Set<String> codesOf(String key) {
        return codes.getOrDefault(key, Set.of());
    }

    public Set<String> codesetsOf(String key) {
        return codesets.getOrDefault(key, Set.of());
    }

    public Set<String> guidesOf(String key) {
        return guides.getOrDefault(key, Set.of());
    }

    private static Set<String> bucket(Map<String, Set<String>> m, String key) {
        return m.computeIfAbsent(key, k -> new TreeSet<>());
    }
}

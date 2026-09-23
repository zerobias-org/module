package com.zerobias.module.x12.codegen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The implementation guides generated in v1 (DESIGN §6): GS08 → imsweb map
 * file + display transaction type (DESIGN §2.1's {@code <TS>} table) + wire
 * aliases. An alias is generated as a full copy under its own GS08 (schema ids
 * embed the GS08, so a copy is the only way the id namespace stays honest); its
 * structure index carries {@code aliasOf}.
 */
public final class GuideCatalog {

    /** One guide: canonical GS08, map resource, display type, alias GS08s. */
    public record Guide(String gs08, String mapFile, String transactionType, List<String> aliases) {
    }

    private static final Map<String, Guide> GUIDES = new LinkedHashMap<>();

    static {
        add("005010X221A1", "835.5010.X221.A1.xml", "835");
        add("005010X222A1", "837.5010.X222.A1.xml", "837P");
        // imsweb maps X223.A1; the wire carries the A2 errata id. Emitted under A2, A1 aliased.
        add("005010X223A2", "837Q3.I.5010.X223.A1.xml", "837I", "005010X223A1");
        add("005010X214", "277.5010.X214.xml", "277CA");
        add("005010X212", "277.5010.X212.xml", "277");
        // imsweb's maps.xml keys the 999 map as 005010X231; the wire carries 005010X231A1.
        add("005010X231A1", "999.5010.xml", "999", "005010X231");
        add("005010X220A1", "834.5010.X220.A1.xml", "834");
        add("005010X218", "820.5010.X218.xml", "820");
    }

    private GuideCatalog() {
    }

    private static void add(String gs08, String mapFile, String ts, String... aliases) {
        GUIDES.put(gs08, new Guide(gs08, mapFile, ts, List.of(aliases)));
    }

    /** Canonical guides, in catalog order. */
    public static List<Guide> all() {
        return new ArrayList<>(GUIDES.values());
    }

    /** Canonical GS08 ids, in catalog order. */
    public static List<String> ids() {
        return new ArrayList<>(GUIDES.keySet());
    }

    /**
     * Resolve a requested GS08 (canonical or alias) to its guide, or null. An
     * alias request resolves to the canonical guide; the caller still emits every
     * alias of that guide.
     */
    public static Guide find(String gs08) {
        final Guide g = GUIDES.get(gs08);
        if (g != null) {
            return g;
        }
        for (Guide c : GUIDES.values()) {
            if (c.aliases().contains(gs08)) {
                return c;
            }
        }
        return null;
    }

    /** Every GS08 a guide is emitted under: canonical first, then aliases. */
    public static List<String> labels(Guide g) {
        final List<String> out = new ArrayList<>();
        out.add(g.gs08());
        out.addAll(g.aliases());
        return Collections.unmodifiableList(out);
    }
}

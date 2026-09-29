package com.zerobias.module.x12.codegen;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The implementation guides generated (DESIGN §6): GS08 → imsweb map file + display
 * transaction type (DESIGN §2.1's {@code <TS>} table) + wire aliases, read from the receiver's
 * own guide table ({@value #RESOURCE}, put on this module's classpath by its pom) so the
 * receiver never parses a guide it has no schemas for, or the reverse.
 *
 * <p>Schemas are generated once, under the canonical GS08: the receiver canonicalizes every
 * wire alias before it looks a guide up, so an alias never needs schemas of its own.
 */
public final class GuideCatalog {

    /** The receiver's guide table (parser.TransactionTypes reads the same file). */
    public static final String RESOURCE = "com/zerobias/module/x12/parser/guides.txt";

    /** One guide: canonical GS08, map resource, display type, alias GS08s. */
    public record Guide(String gs08, String mapFile, String transactionType, List<String> aliases) {
    }

    private static final Map<String, Guide> GUIDES = load();

    private GuideCatalog() {
    }

    /** Canonical guides, in table order. */
    public static List<Guide> all() {
        return new ArrayList<>(GUIDES.values());
    }

    /** Canonical GS08 ids, in table order. */
    public static List<String> ids() {
        return new ArrayList<>(GUIDES.keySet());
    }

    /** Resolve a requested GS08 (canonical or alias) to its guide, or null. */
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

    /** Columns: GS08, display type, imsweb FileType (the receiver's concern), map file, optional aliases. */
    private static Map<String, Guide> load() {
        final Map<String, Guide> out = new LinkedHashMap<>();
        try (InputStream in = GuideCatalog.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " is not on the classpath (see codegen/pom.xml <resources>)");
            }
            final BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = r.readLine()) != null; ) {
                final String t = line.strip();
                if (t.isEmpty() || t.startsWith("#")) {
                    continue;
                }
                final String[] c = t.split("\\s+");
                if (c.length < 4 || c.length > 5) {
                    throw new IllegalStateException(RESOURCE + ": expected 4 or 5 columns: " + line);
                }
                out.put(c[0], new Guide(c[0], c[3], c[1], c.length == 5 ? List.of(c[4].split(",")) : List.of()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + RESOURCE, e);
        }
        return out;
    }
}

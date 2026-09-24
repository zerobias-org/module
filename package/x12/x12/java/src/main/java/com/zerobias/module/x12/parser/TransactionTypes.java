package com.zerobias.module.x12.parser;

import com.imsweb.x12.reader.X12Reader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The GS08 → (canonical guide id, display type, imsweb {@link X12Reader.FileType}) table,
 * read from {@value #RESOURCE}. The schema codegen reads the same file, so a guide is either
 * parsed <em>and</em> has schemas, or is neither.
 *
 * <p>DESIGN §2.1 (display names), §4.2 step 3b (FileType selection), §6 (guides in v1). GS08 arrives in
 * several spellings: the canonical id with its addenda suffix ({@code 005010X221A1}), the bare
 * guide without the suffix ({@code 005010X222} — what the x12.org 837 examples carry), and, for
 * 837I, both {@code A1} and {@code A2}. All forms resolve; {@link #canonical(String)} is what the
 * buffer stores in {@code gs08} and what schema ids embed, so one wire spelling never yields
 * two collections. A GS08 that is not in the table is {@code unsupported-guide} (DESIGN §4.2 step 3b)
 * and the file goes to {@code .error}.
 */
public final class TransactionTypes {

    /** The table, a classpath resource shared with the codegen. */
    public static final String RESOURCE = "com/zerobias/module/x12/parser/guides.txt";

    /** One row of the table; {@code mapFile} is the pyx12 map under imsweb's {@code mapping/}. */
    public record Guide(String canonicalGs08, String transactionType, X12Reader.FileType fileType, String mapFile,
                        List<String> aliases) { }

    private TransactionTypes() { }

    /** The row for any accepted spelling, or empty when the guide is not supported. */
    public static Optional<Guide> guide(String gs08) {
        if (gs08 == null) return Optional.empty();
        return Optional.ofNullable(BY_GS08.get(gs08.trim().toUpperCase(Locale.ROOT)));
    }

    /** Canonical GS08 for any accepted spelling, or empty when the guide is unknown. */
    public static Optional<String> canonical(String gs08) {
        return guide(gs08).map(Guide::canonicalGs08);
    }

    /** Display transaction type ({@code 835}, {@code 837P}, …) or the bare ST01 when unknown. */
    public static String transactionType(String gs08, String st01) {
        return guide(gs08).map(Guide::transactionType).orElse(st01 == null ? "UNKNOWN" : st01.trim());
    }

    /** The imsweb definition to parse with, or empty when the guide is not supported. */
    public static Optional<X12Reader.FileType> fileTypeFor(String gs08) {
        return guide(gs08).map(Guide::fileType);
    }

    /** Every row, in file order. */
    public static List<Guide> guides() {
        return GUIDES;
    }

    // ---- table ------------------------------------------------------------------------------

    private static final List<Guide> GUIDES = load();
    private static final Map<String, Guide> BY_GS08 = index(GUIDES);

    private static List<Guide> load() {
        List<Guide> out = new ArrayList<>();
        try (InputStream in = TransactionTypes.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " is not on the classpath");
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = r.readLine()) != null; ) {
                String t = line.strip();
                if (t.isEmpty() || t.startsWith("#")) continue;
                String[] c = t.split("\\s+");
                if (c.length < 4 || c.length > 5) {
                    throw new IllegalStateException(RESOURCE + ": expected 4 or 5 columns: " + line);
                }
                List<String> aliases = c.length == 5 ? List.of(c[4].split(",")) : List.of();
                out.add(new Guide(c[0], c[1], X12Reader.FileType.valueOf(c[2]), c[3], aliases));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + RESOURCE, e);
        }
        return List.copyOf(out);
    }

    private static Map<String, Guide> index(List<Guide> guides) {
        Map<String, Guide> m = new HashMap<>();
        for (Guide g : guides) {
            m.put(g.canonicalGs08(), g);
            for (String a : g.aliases()) m.put(a, g);
        }
        return Map.copyOf(m);
    }
}

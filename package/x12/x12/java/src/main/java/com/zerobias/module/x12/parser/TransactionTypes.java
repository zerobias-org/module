package com.zerobias.module.x12.parser;

import com.imsweb.x12.reader.X12Reader;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The fixed GS08 → (imsweb {@link X12Reader.FileType}, display type, canonical guide id) table.
 *
 * <p>DESIGN §2.1 (display names), §4.2b (FileType selection), §6 (guides in v1). GS08 arrives in
 * several spellings: the canonical id with its addenda suffix ({@code 005010X221A1}), the bare
 * guide without the suffix ({@code 005010X222} — what the x12.org 837 examples carry), and, for
 * 837I, both {@code A1} and {@code A2}. All forms resolve; {@link #canonical(String)} is what the
 * buffer stores in {@code gs08} and what schema ids embed, so one wire spelling never yields
 * two collections.
 *
 * <p>Guides imsweb 1.16 does not map at 005010 (270/271 X279, 276 X212 request side, 837D X224)
 * return {@link Optional#empty()} from {@link #fileTypeFor(String)}; the poller treats that as
 * {@code unsupported-guide} (DESIGN §4.2b) and the file goes to {@code .error}.
 */
public final class TransactionTypes {

    /** One row of the table. */
    public record Guide(String canonicalGs08, String transactionType, X12Reader.FileType fileType) { }

    private TransactionTypes() { }

    /** Canonical GS08 for any accepted spelling, or empty when the guide is unknown. */
    public static Optional<String> canonical(String gs08) {
        if (gs08 == null) return Optional.empty();
        Guide g = TABLE.get(gs08.trim().toUpperCase(Locale.ROOT));
        return Optional.ofNullable(g).map(Guide::canonicalGs08);
    }

    /** Display transaction type ({@code 835}, {@code 837P}, …) or the bare ST01 when unknown. */
    public static String transactionType(String gs08, String st01) {
        Guide g = gs08 == null ? null : TABLE.get(gs08.trim().toUpperCase(Locale.ROOT));
        return g != null ? g.transactionType() : (st01 == null ? "UNKNOWN" : st01.trim());
    }

    /** The imsweb definition to parse with, or empty when imsweb has no 005010 map for the guide. */
    public static Optional<X12Reader.FileType> fileTypeFor(String gs08) {
        if (gs08 == null) return Optional.empty();
        Guide g = TABLE.get(gs08.trim().toUpperCase(Locale.ROOT));
        return g == null ? Optional.empty() : Optional.ofNullable(g.fileType());
    }

    /** True when the guide is known, even if imsweb cannot parse it (schemas still exist). */
    public static boolean isKnown(String gs08) {
        return canonical(gs08).isPresent();
    }

    // ---- table ------------------------------------------------------------------------------

    private static final Map<String, Guide> TABLE = buildTable();

    private static Map<String, Guide> buildTable() {
        var m = new java.util.HashMap<String, Guide>();
        add(m, new Guide("005010X221A1", "835",   X12Reader.FileType.ANSI835_5010_X221), "005010X221");
        add(m, new Guide("005010X222A1", "837P",  X12Reader.FileType.ANSI837_5010_X222), "005010X222");
        add(m, new Guide("005010X223A2", "837I",  X12Reader.FileType.ANSI837_5010_X223), "005010X223", "005010X223A1");
        add(m, new Guide("005010X214",   "277CA", X12Reader.FileType.ANSI277_5010_X214));
        add(m, new Guide("005010X212",   "277",   X12Reader.FileType.ANSI277_5010_X212));
        add(m, new Guide("005010X231A1", "999",   X12Reader.FileType.ANSI837_5010_X231), "005010X231");
        add(m, new Guide("005010X220A1", "834",   X12Reader.FileType.ANSI834_5010_X220), "005010X220");
        add(m, new Guide("005010X218",   "820",   null));
        return Map.copyOf(m);
    }

    private static void add(Map<String, Guide> m, Guide g, String... aliases) {
        m.put(g.canonicalGs08(), g);
        for (String a : aliases) m.put(a, g);
    }
}

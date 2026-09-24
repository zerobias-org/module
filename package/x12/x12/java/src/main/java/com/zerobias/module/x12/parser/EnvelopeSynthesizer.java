package com.zerobias.module.x12.parser;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Wraps a file that starts at {@code ST} (no ISA/GS envelope — the x12.org 837/277 examples,
 * some clearinghouse extracts) in a synthetic ISA/GS…GE/IEA interchange (DESIGN §4.3),
 * enabled by {@code config.allowBareTransactionSets}. The implementation guide comes from
 * {@code ST03} (mandatory for 005010 bare sets — without it the guide is unknowable); the
 * functional identifier from {@code ST01}; dates from the clock; sender/receiver are the
 * fixed {@link #SYNTHETIC_ID}. All transaction sets in the file land in one functional
 * group with {@code GS06 = 1} under {@code ISA13 = 000000001}, so element keys are
 * {@code <fileId>:000000001:1:<ST02>}.
 */
public final class EnvelopeSynthesizer {

    public static final String SYNTHETIC_ID = "SYNTHETIC";
    public static final String ISA_CONTROL = "000000001";
    public static final String GS_CONTROL = "1";

    /** ST01 → GS01 functional identifier code (X12 Appendix / HIPAA guides). */
    private static final Map<String, String> FUNCTIONAL_ID = Map.ofEntries(
        Map.entry("835", "HP"), Map.entry("837", "HC"), Map.entry("277", "HN"), Map.entry("276", "HR"),
        Map.entry("999", "FA"), Map.entry("997", "FA"), Map.entry("834", "BE"), Map.entry("820", "RA"),
        Map.entry("270", "HS"), Map.entry("271", "HB"), Map.entry("278", "HI"), Map.entry("275", "PI"));

    /**
     * Segment id → position of a composite whose first component is a qualifier: SV1-01, SV2-02,
     * SV3-01, SVC-01, SVD-03 (C003), CLM05 (C023), HI01 (C022), PLB03 (C042), STC01 (C043),
     * IK4-01 (C030).
     */
    private static final Map<String, Integer> COMPOSITES = Map.of(
        "SV1", 1, "SV2", 2, "SV3", 1, "SVC", 1, "SVD", 3, "CLM", 5, "HI", 1, "PLB", 3, "STC", 1, "IK4", 1);

    private static final String REPETITION_CANDIDATES = "^|!";

    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");
    private static final DateTimeFormatter CCYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HHmm");

    private EnvelopeSynthesizer() {
    }

    /** True when the text (BOM/whitespace already stripped) starts with an ST segment. */
    public static boolean isBare(String text) {
        return text != null && text.length() > 3 && text.startsWith("ST") && !Separators.isDataChar(text.charAt(2));
    }

    /** Result: the wrapped text plus the delimiters it was written with. */
    public record Wrapped(String text, Separators separators) {
    }

    /**
     * Wrap {@code text} (which must satisfy {@link #isBare}) in a synthetic envelope. The
     * element and segment delimiters are taken from the ST segment and the component
     * separator from the first composite that shows one ({@link #COMPOSITES}); only a file
     * without any falls back to {@code :} (or {@code >} when {@code :} is the element
     * separator). The repetition separator is {@code ^} unless that collides.
     */
    public static Wrapped wrap(String text, Clock clock) throws X12ParseException {
        if (!isBare(text)) {
            throw new X12ParseException("bare-transaction-set: text does not start with an ST segment");
        }
        char element = text.charAt(2);
        int termAt = -1;
        for (int i = 3; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != element && !Character.isLetterOrDigit(c) && c != ' ') {
                termAt = i;
                break;
            }
        }
        if (termAt < 0) {
            throw new X12ParseException("bare-transaction-set: cannot find the segment terminator after ST");
        }
        char segment = text.charAt(termAt);
        String lineBreak = "";
        if (segment != '\n' && segment != '\r') {
            if (text.startsWith("\r\n", termAt + 1)) {
                lineBreak = "\r\n";
            } else if (text.startsWith("\n", termAt + 1)) {
                lineBreak = "\n";
            }
        }
        List<String> segments = new ArrayList<>();
        for (String s : text.split(java.util.regex.Pattern.quote(String.valueOf(segment)))) {
            String t = s.strip();
            if (!t.isEmpty()) {
                segments.add(t);
            }
        }
        char component = componentSeparator(segments, element, segment);
        char repetition = '^';
        for (char c : REPETITION_CANDIDATES.toCharArray()) {
            if (c != element && c != component && c != segment) {
                repetition = c;
                break;
            }
        }
        Separators seps = new Separators(element, repetition, component, segment, lineBreak);
        String[] st = seps.splitElements(segments.get(0));
        if (st.length < 3) {
            throw new X12ParseException("bare-transaction-set: ST segment has no ST02");
        }
        String st01 = st[1].trim();
        String st03 = st.length > 3 ? st[3].trim() : "";
        if (st03.isEmpty()) {
            throw new X12ParseException("bare-transaction-set: ST03 is empty, cannot determine the implementation"
                + " guide of an envelope-less file (transaction set " + st01 + ")");
        }
        String gs01 = FUNCTIONAL_ID.getOrDefault(st01, "XX");
        int stCount = 0;
        for (String s : segments) {
            if (s.startsWith("ST" + element)) {
                stCount++;
            }
        }
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(ZoneOffset.UTC);

        StringBuilder out = new StringBuilder();
        String isa = isa(now, seps);
        out.append(isa).append(segment).append(lineBreak);
        out.append("GS").append(element).append(gs01).append(element).append(SYNTHETIC_ID).append(element)
            .append(SYNTHETIC_ID).append(element).append(CCYYMMDD.format(now)).append(element)
            .append(HHMM.format(now)).append(element).append(GS_CONTROL).append(element).append('X')
            .append(element).append(st03).append(segment).append(lineBreak);
        for (String s : segments) {
            out.append(s).append(segment).append(lineBreak);
        }
        out.append("GE").append(element).append(stCount).append(element).append(GS_CONTROL).append(segment).append(lineBreak);
        out.append("IEA").append(element).append('1').append(element).append(ISA_CONTROL).append(segment).append(lineBreak);
        return new Wrapped(out.toString(), seps);
    }

    /**
     * The component separator the bare file writes: the first character that is not a letter,
     * digit or blank inside a composite that opens with a qualifier ({@code SV1*HC>99213},
     * {@code HI*ABK>J069}). A guess would leave every composite unsplit when it is wrong.
     */
    static char componentSeparator(List<String> segments, char element, char segment) {
        for (String s : segments) {
            String[] tokens = s.split(java.util.regex.Pattern.quote(String.valueOf(element)), -1);
            Integer position = COMPOSITES.get(tokens[0].trim());
            if (position == null || position >= tokens.length) {
                continue;
            }
            for (char c : tokens[position].toCharArray()) {
                if (!Character.isLetterOrDigit(c) && c != ' ') {
                    if (c != element && c != segment && !Separators.isDataChar(c)) {
                        return c;
                    }
                    break;
                }
            }
        }
        return element == ':' ? '>' : ':';
    }

    /** The fixed-width ISA (105 chars before the terminator). */
    static String isa(ZonedDateTime now, Separators s) {
        char e = s.element();
        String isa = "ISA" + e + "00" + e + pad("", 10) + e + "00" + e + pad("", 10)
            + e + "ZZ" + e + pad(SYNTHETIC_ID, 15) + e + "ZZ" + e + pad(SYNTHETIC_ID, 15)
            + e + YYMMDD.format(now) + e + HHMM.format(now) + e + s.repetition() + e + "00501"
            + e + ISA_CONTROL + e + "0" + e + "T" + e + s.component();
        if (isa.length() != Separators.ISA_LENGTH - 1) {
            throw new IllegalStateException("synthetic ISA is " + isa.length() + " chars, expected 105");
        }
        return isa;
    }

    private static String pad(String v, int width) {
        StringBuilder sb = new StringBuilder(v);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.substring(0, width);
    }
}

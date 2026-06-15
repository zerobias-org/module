package com.zerobias.module.hl7.materializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wire-format normalization for HL7 v2 primitive values (DESIGN §2.4, §5).
 *
 * <p>The DataProducer surface exposes post-normalized JSON: ETL never sees HL7
 * date formats, pipe-encoded escapes, or the {@code ""} explicit-null sentinel.
 * This class is the pure, side-effect-free heart of that normalization, separated
 * from the HAPI parse so it can be exhaustively unit-tested.
 *
 * <p>Design choices worth noting:
 * <ul>
 *   <li>Dates/times are normalized to the <em>most precise ISO 8601 the feed
 *       actually sent</em> — partial values stay partial. We do NOT pad a
 *       date-only DTM to {@code T00:00:00Z}; fabricating a time/zone the sender
 *       never specified is worse than preserving its precision. (The §5 example's
 *       {@code "1980-01-01T00:00:00Z"} is illustrative.)</li>
 *   <li>Unparseable date/time values are returned trimmed-but-unchanged rather
 *       than dropped — no silent data loss on dirty feeds.</li>
 *   <li>Unknown escape sequences are dropped; the standard delimiter/escape/.br/
 *       hex set is decoded. (Flagged: revisit if real feeds carry custom escapes.)</li>
 * </ul>
 */
public final class Hl7Normalizer {

    /** Default HL7 encoding characters (MSH-1 {@code |}, MSH-2 {@code ^~\&}). */
    public static final char DEFAULT_FIELD = '|';
    public static final char DEFAULT_COMPONENT = '^';
    public static final char DEFAULT_REPETITION = '~';
    public static final char DEFAULT_ESCAPE = '\\';
    public static final char DEFAULT_SUBCOMPONENT = '&';

    // DTM: YYYY[MM[DD[HH[MM[SS[.S+]]]]]][+/-ZZZZ]
    private static final Pattern DTM = Pattern.compile(
        "^(\\d{4})(\\d{2})?(\\d{2})?(\\d{2})?(\\d{2})?(\\d{2})?(\\.\\d+)?([+-]\\d{4})?$");
    // DT: YYYY[MM[DD]]
    private static final Pattern DT = Pattern.compile("^(\\d{4})(\\d{2})?(\\d{2})?$");
    // TM: HH[MM[SS[.S+]]][+/-ZZZZ]
    private static final Pattern TM = Pattern.compile(
        "^(\\d{2})(\\d{2})?(\\d{2})?(\\.\\d+)?([+-]\\d{4})?$");

    private Hl7Normalizer() {
    }

    /** HL7 {@code ""} (two double-quotes) is the explicit-null / delete sentinel (DESIGN §5). */
    public static boolean isExplicitNull(String raw) {
        return "\"\"".equals(raw);
    }

    /** HL7 DT → ISO 8601 date, preserving precision: {@code 19800101→1980-01-01}, {@code 198001→1980-01}. */
    public static String normalizeDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final String s = value.trim();
        final Matcher m = DT.matcher(s);
        if (!m.matches()) {
            return s; // unparseable — preserve rather than lose
        }
        final StringBuilder sb = new StringBuilder(m.group(1));
        if (m.group(2) != null) {
            sb.append('-').append(m.group(2));
        }
        if (m.group(3) != null) {
            sb.append('-').append(m.group(3));
        }
        return sb.toString();
    }

    /** HL7 DTM/TS → ISO 8601 timestamp, preserving precision and timezone. */
    public static String normalizeDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final String s = value.trim();
        final Matcher m = DTM.matcher(s);
        if (!m.matches()) {
            return s;
        }
        final StringBuilder sb = new StringBuilder(m.group(1));
        if (m.group(2) != null) {
            sb.append('-').append(m.group(2));
        }
        if (m.group(3) != null) {
            sb.append('-').append(m.group(3));
        }
        if (m.group(4) != null) {
            sb.append('T').append(m.group(4));
            if (m.group(5) != null) {
                sb.append(':').append(m.group(5));
            }
            if (m.group(6) != null) {
                sb.append(':').append(m.group(6));
            }
            if (m.group(7) != null) {
                sb.append(m.group(7)); // fractional seconds, includes the dot
            }
        }
        if (m.group(8) != null) {
            sb.append(formatTimezone(m.group(8)));
        }
        return sb.toString();
    }

    /** HL7 TM → ISO 8601 time: {@code 103000→10:30:00}, {@code 103000-0500→10:30:00-05:00}. */
    public static String normalizeTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final String s = value.trim();
        final Matcher m = TM.matcher(s);
        if (!m.matches()) {
            return s;
        }
        final StringBuilder sb = new StringBuilder(m.group(1));
        if (m.group(2) != null) {
            sb.append(':').append(m.group(2));
        }
        if (m.group(3) != null) {
            sb.append(':').append(m.group(3));
        }
        if (m.group(4) != null) {
            sb.append(m.group(4));
        }
        if (m.group(5) != null) {
            sb.append(formatTimezone(m.group(5)));
        }
        return sb.toString();
    }

    /** {@code +0500→+05:00}, {@code +0000→Z}. */
    private static String formatTimezone(String tz) {
        final String sign = tz.substring(0, 1);
        final String hh = tz.substring(1, 3);
        final String mm = tz.substring(3, 5);
        if ("00".equals(hh) && "00".equals(mm)) {
            return "Z";
        }
        return sign + hh + ":" + mm;
    }

    /** Decode HL7 escape sequences using the default encoding characters. */
    public static String decodeEscapes(String value) {
        return decodeEscapes(value, DEFAULT_ESCAPE, DEFAULT_FIELD,
            DEFAULT_COMPONENT, DEFAULT_SUBCOMPONENT, DEFAULT_REPETITION);
    }

    /**
     * Decode HL7 escape sequences using the message's actual encoding characters.
     * Recognizes {@code \F\ \S\ \T\ \R\ \E\} (delimiters/escape), {@code \.br\}
     * (line break), {@code \Xdd..\} (hex), and the {@code \H\ \N\} formatting
     * toggles (stripped). Unknown sequences are dropped; an unterminated escape
     * is preserved verbatim.
     */
    public static String decodeEscapes(String value, char esc, char field,
                                       char component, char subcomponent, char repetition) {
        if (value == null || value.indexOf(esc) < 0) {
            return value;
        }
        final StringBuilder out = new StringBuilder(value.length());
        int i = 0;
        while (i < value.length()) {
            final char c = value.charAt(i);
            if (c != esc) {
                out.append(c);
                i++;
                continue;
            }
            final int end = value.indexOf(esc, i + 1);
            if (end < 0) {
                out.append(value, i, value.length()); // unterminated — preserve
                break;
            }
            out.append(decodeSequence(value.substring(i + 1, end),
                field, component, subcomponent, repetition, esc));
            i = end + 1;
        }
        return out.toString();
    }

    private static String decodeSequence(String seq, char field, char component,
                                         char subcomponent, char repetition, char esc) {
        if (seq.isEmpty()) {
            return "";
        }
        switch (seq) {
            case "F": return String.valueOf(field);
            case "S": return String.valueOf(component);
            case "T": return String.valueOf(subcomponent);
            case "R": return String.valueOf(repetition);
            case "E": return String.valueOf(esc);
            case ".br": return "\n";
            case "H": case "N": return ""; // highlight on/off — formatting only
            default:
                if (seq.charAt(0) == 'X' && seq.length() > 1 && (seq.length() % 2) == 1) {
                    return decodeHex(seq.substring(1));
                }
                return ""; // unknown escape — dropped
        }
    }

    private static String decodeHex(String hex) {
        final StringBuilder sb = new StringBuilder(hex.length() / 2);
        for (int i = 0; i + 1 < hex.length(); i += 2) {
            try {
                sb.append((char) Integer.parseInt(hex.substring(i, i + 2), 16));
            } catch (NumberFormatException e) {
                return ""; // malformed hex escape — drop
            }
        }
        return sb.toString();
    }
}

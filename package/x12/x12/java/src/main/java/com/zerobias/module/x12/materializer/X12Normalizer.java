package com.zerobias.module.x12.materializer;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Wire-format normalization for X12 element values (DESIGN §2.4): the DataProducer surface
 * never shows implied decimals, {@code CCYYMMDD} dates, {@code HHMM} times or the ISA's
 * fixed-width padding. Pure functions, exhaustively unit-tested.
 *
 * <ul>
 *   <li>{@code AN}/{@code ID} → trimmed string</li>
 *   <li>{@code N0}..{@code N9} → {@link BigDecimal} shifted right by the implied decimals
 *       (never a float — money)</li>
 *   <li>{@code R} → {@link BigDecimal}</li>
 *   <li>{@code DT} → ISO {@code YYYY-MM-DD}; six-digit {@code YYMMDD} → {@code 20YY}</li>
 *   <li>{@code TM} → {@code HH:MM[:SS[.dd]]}</li>
 *   <li>{@code B} → unchanged</li>
 * </ul>
 * A value that does not fit its declared type is returned trimmed-but-unchanged rather than
 * dropped or coerced — no silent data loss on dirty feeds.
 */
public final class X12Normalizer {

    private static final Pattern DT8 = Pattern.compile("^(\\d{4})(\\d{2})(\\d{2})$");
    private static final Pattern DT6 = Pattern.compile("^(\\d{2})(\\d{2})(\\d{2})$");
    private static final Pattern TM = Pattern.compile("^(\\d{2})(\\d{2})(\\d{2})?(\\d{1,2})?$");
    private static final Pattern NUMERIC = Pattern.compile("^[+-]?\\d+$");

    private X12Normalizer() {
    }

    /**
     * Normalize one element value by its pyx12 data type. Returns a {@link String},
     * a {@link BigDecimal}, or null when the value is empty.
     */
    public static Object normalize(String raw, String x12Type, Integer impliedDecimals) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        if (v.isEmpty()) {
            return null;
        }
        if (x12Type == null) {
            return v;
        }
        switch (x12Type) {
            case "R":
                return decimal(v);
            case "DT":
                return date(v);
            case "TM":
                return time(v);
            case "B":
                return raw;
            default:
                if (isImplied(x12Type)) {
                    int n = impliedDecimals != null ? impliedDecimals : x12Type.charAt(1) - '0';
                    return implied(v, n);
                }
                return v;
        }
    }

    static boolean isImplied(String t) {
        return t.length() == 2 && t.charAt(0) == 'N' && Character.isDigit(t.charAt(1));
    }

    /** {@code R}: an explicit-decimal number; unparseable → the trimmed string. */
    public static Object decimal(String v) {
        try {
            return new BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            return v.trim();
        }
    }

    /** {@code N<n>}: an integer on the wire with {@code n} implied decimal places. */
    public static Object implied(String v, int decimals) {
        String t = v.trim();
        if (!NUMERIC.matcher(t).matches()) {
            return t;
        }
        return new BigDecimal(t).movePointLeft(decimals);
    }

    /** {@code DT}: {@code CCYYMMDD} → {@code YYYY-MM-DD}; {@code YYMMDD} → {@code 20YY-MM-DD}. */
    public static String date(String v) {
        String t = v.trim();
        var m8 = DT8.matcher(t);
        if (m8.matches()) {
            return m8.group(1) + "-" + m8.group(2) + "-" + m8.group(3);
        }
        var m6 = DT6.matcher(t);
        if (m6.matches()) {
            return "20" + m6.group(1) + "-" + m6.group(2) + "-" + m6.group(3);
        }
        return t;
    }

    /** {@code TM}: {@code HHMM[SS[d[d]]]} → {@code HH:MM[:SS[.dd]]}. */
    public static String time(String v) {
        String t = v.trim();
        var m = TM.matcher(t);
        if (!m.matches()) {
            return t;
        }
        StringBuilder sb = new StringBuilder(m.group(1)).append(':').append(m.group(2));
        if (m.group(3) != null) {
            sb.append(':').append(m.group(3));
            if (m.group(4) != null) {
                sb.append('.').append(m.group(4));
            }
        }
        return sb.toString();
    }
}

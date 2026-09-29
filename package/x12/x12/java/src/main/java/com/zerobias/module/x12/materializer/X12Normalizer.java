package com.zerobias.module.x12.materializer;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.regex.Matcher;
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
 *   <li>{@code TM} → {@code HH:MM:SS[.d[d]]}</li>
 *   <li>{@code B} → unchanged</li>
 *   <li>a Date Time Period (data element 1251) → ISO 8601 by its format qualifier (1250),
 *       see {@link #dateTimePeriod}</li>
 * </ul>
 * A value that does not fit its declared type — including a date or time that does not exist,
 * such as {@code 20261345} — is returned trimmed-but-unchanged rather than dropped or coerced:
 * no silent data loss on dirty feeds.
 */
public final class X12Normalizer {

    /** Data element 1250: the format ({@code D8}, {@code RD8}, {@code DT}, ...) of the 1251 beside it. */
    public static final String DATE_TIME_FORMAT_QUALIFIER = "1250";
    /** Data element 1251: a date, range or date-time whose format the 1250 qualifier names. */
    public static final String DATE_TIME_PERIOD = "1251";

    private static final Pattern DT8 = Pattern.compile("^(\\d{4})(\\d{2})(\\d{2})$");
    private static final Pattern DT6 = Pattern.compile("^(\\d{2})(\\d{2})(\\d{2})$");
    private static final Pattern RD8 = Pattern.compile("^(\\d{8})-(\\d{8})$");
    private static final Pattern DT12 = Pattern.compile("^(\\d{8})(\\d{4})$");
    private static final Pattern TM = Pattern.compile("^(\\d{2})(\\d{2})(?:(\\d{2})(\\d{1,2})?)?$");
    private static final Pattern NUMERIC = Pattern.compile("^[+-]?\\d+$");

    private X12Normalizer() {
    }

    /**
     * Normalize one element value by its pyx12 data type. {@code coreType} is the schema's type
     * for the position: a numeric X12 type the schema declares {@code string} (a control number
     * such as GS06, where leading zeros are part of the identity) stays the trimmed string.
     * Returns a {@link String}, a {@link BigDecimal}, or null when the value is empty.
     */
    public static Object normalize(String raw, String x12Type, String coreType, Integer impliedDecimals) {
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
                return isNumber(coreType) ? decimal(v) : v;
            case "DT":
                return date(v);
            case "TM":
                return time(v);
            case "B":
                return raw;
            default:
                if (isImplied(x12Type) && isNumber(coreType)) {
                    int n = impliedDecimals != null ? impliedDecimals : x12Type.charAt(1) - '0';
                    return implied(v, n);
                }
                return v;
        }
    }

    private static boolean isNumber(String coreType) {
        return coreType == null || "decimal".equals(coreType);
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
        String iso = isoDate(t);
        if (iso != null) {
            return iso;
        }
        if (DT6.matcher(t).matches()) {
            String d = isoDate("20" + t);
            return d == null ? t : d;
        }
        return t;
    }

    /** {@code TM}: {@code HHMM[SS[d[d]]]} → {@code HH:MM:SS[.d[d]]}, seconds always present. */
    public static String time(String v) {
        String t = v.trim();
        String iso = isoTime(t);
        return iso == null ? t : iso;
    }

    /**
     * A Date Time Period (1251) by its format qualifier (1250): {@code D8} → {@code YYYY-MM-DD},
     * {@code RD8} ({@code CCYYMMDD-CCYYMMDD}) → the ISO 8601 interval
     * {@code YYYY-MM-DD/YYYY-MM-DD}, {@code DT} ({@code CCYYMMDDHHMM}) → {@code YYYY-MM-DDTHH:MM:SS}.
     * Any other qualifier, or a value that does not fit it, stays the trimmed string.
     */
    public static String dateTimePeriod(String v, String qualifier) {
        String t = v.trim();
        if (qualifier == null) {
            return t;
        }
        String out = null;
        switch (qualifier.trim()) {
            case "D8":
                out = isoDate(t);
                break;
            case "RD8": {
                Matcher m = RD8.matcher(t);
                if (m.matches()) {
                    String from = isoDate(m.group(1));
                    String to = isoDate(m.group(2));
                    out = from == null || to == null ? null : from + "/" + to;
                }
                break;
            }
            case "DT": {
                Matcher m = DT12.matcher(t);
                if (m.matches()) {
                    String d = isoDate(m.group(1));
                    String time = isoTime(m.group(2));
                    out = d == null || time == null ? null : d + "T" + time;
                }
                break;
            }
            default:
                break;
        }
        return out == null ? t : out;
    }

    /** {@code CCYYMMDD} → {@code YYYY-MM-DD}, or null when it is not eight digits of a real date. */
    private static String isoDate(String t) {
        Matcher m = DT8.matcher(t);
        if (!m.matches()) {
            return null;
        }
        try {
            return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)))
                .toString();
        } catch (DateTimeException e) {
            return null;
        }
    }

    /** {@code HHMM[SS[d[d]]]} → {@code HH:MM:SS[.d[d]]}, or null when it is not a real time of day. */
    private static String isoTime(String t) {
        Matcher m = TM.matcher(t);
        if (!m.matches()) {
            return null;
        }
        String seconds = m.group(3) == null ? "00" : m.group(3);
        try {
            LocalTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(seconds));
        } catch (DateTimeException e) {
            return null;
        }
        StringBuilder sb = new StringBuilder(m.group(1)).append(':').append(m.group(2)).append(':').append(seconds);
        if (m.group(4) != null) {
            sb.append('.').append(m.group(4));
        }
        return sb.toString();
    }
}

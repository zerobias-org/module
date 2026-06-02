package com.zerobias.module.hl7.filter;

import com.zerobias.litefilter.Adapter;
import com.zerobias.litefilter.ComparisonOperator;
import com.zerobias.litefilter.Expression;
import com.zerobias.litefilter.LogicalOperator;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * lite-filter {@link Adapter} that converts an RFC4515 {@link Expression} into a
 * SQLite {@code WHERE}-clause fragment over the buffer's {@code messages} table
 * (DESIGN §2.6).
 *
 * <p>Property resolution follows the schema property names consumers see, not
 * HL7 positional codes:
 * <ul>
 *   <li>The four envelope properties ({@code controlId}, {@code receivedAt},
 *       {@code status}, {@code leaseId}) resolve to their denormalized columns.</li>
 *   <li>Everything else is a dotted path into the typed message body and resolves
 *       to {@code json_extract(mapped_json, '$.<path>')} via SQLite's JSON1.</li>
 * </ul>
 *
 * <p>Two deviations from the SQL generic module's adapter, both deliberate:
 * <ol>
 *   <li><b>Public-getter reflection, enum dispatch.</b> lite-filter's {@code Clause}
 *       and {@code Grouping} are package-private, so we cannot reference them
 *       directly — but their getters are public and return the public
 *       {@link ComparisonOperator}/{@link LogicalOperator} enums. We reflect the
 *       getters (not the private fields) and switch on the enums. This is robust
 *       to lite-filter storing {@code expressions} as a {@code List} rather than
 *       an array (the SQL module's verbatim {@code (Expression[])} cast would
 *       {@code ClassCastException} against this version).</li>
 *   <li><b>Epoch-millis date columns.</b> {@code received_at}/{@code acked_at}/
 *       {@code in_flight_until} are stored as epoch-millis INTEGERs (so ISO
 *       strings would mis-sort lexicographically). Comparisons against an ISO
 *       literal, and the {@code :withinDays:}/{@code :year:} date extensions, are
 *       emitted with {@code unixepoch(...)*1000} / {@code strftime(.../1000,
 *       'unixepoch')} so they evaluate correctly against integer storage.</li>
 * </ol>
 *
 * <p>String equality is emitted {@code COLLATE NOCASE} to match lite-filter's
 * in-memory evaluator, whose default {@code MatchOptions} is case-insensitive;
 * {@code LIKE} is already case-insensitive for ASCII in SQLite.
 */
public class Hl7SqlAdapter implements Adapter {

    /** lite-filter adapter-registry key. */
    public static final String KEY = "SQL";

    /** Top-level envelope properties that map to real columns (not JSON paths). */
    private static final Map<String, String> ENVELOPE_COLUMNS = Map.of(
        "controlId", "control_id",
        "receivedAt", "received_at",
        "status", "status",
        "leaseId", "lease_id"
    );

    /** Columns stored as epoch-millis INTEGERs. */
    private static final Set<String> EPOCH_MILLIS_COLUMNS =
        Set.of("received_at", "acked_at", "in_flight_until");

    @Override
    public String fromExpression(Expression expression) {
        if (expression == null) {
            return "";
        }
        String kind = expression.getClass().getSimpleName();
        switch (kind) {
            case "Clause":
                return clause(expression);
            case "Grouping":
                return grouping(expression);
            default:
                throw new IllegalArgumentException("Unknown expression type: " + kind);
        }
    }

    // --- grouping (& | !) -------------------------------------------------

    private String grouping(Expression expression) {
        LogicalOperator op = (LogicalOperator) invoke(expression, "getOperator");
        @SuppressWarnings("unchecked")
        List<Expression> children = (List<Expression>) invoke(expression, "getExpressions");

        if (children == null || children.isEmpty()) {
            return "";
        }

        switch (op) {
            case AND:
            case OR:
                String join = op == LogicalOperator.AND ? " AND " : " OR ";
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < children.size(); i++) {
                    if (i > 0) {
                        sb.append(join);
                    }
                    sb.append('(').append(fromExpression(children.get(i))).append(')');
                }
                return sb.toString();
            case NOT:
                if (children.size() != 1) {
                    throw new IllegalArgumentException("NOT requires exactly one expression");
                }
                return "NOT (" + fromExpression(children.get(0)) + ")";
            default:
                throw new IllegalArgumentException("Unsupported logical operator: " + op);
        }
    }

    // --- clause (property op value) --------------------------------------

    private String clause(Expression expression) {
        String property = (String) invoke(expression, "getProperty");
        ComparisonOperator op = (ComparisonOperator) invoke(expression, "getOperator");
        Object value = invoke(expression, "getValue");

        String col = column(property);
        boolean epoch = isEpochColumn(property);

        switch (op) {
            case IS_NULL:
            case IS_EMPTY:       // arrays have no SQL analogue; treat as absence
                return col + " IS NULL";
            case PRESENCE_CHECK:
                return col + " IS NOT NULL";

            case EQUALS:
                if (value instanceof String && ((String) value).contains("*")) {
                    // Escape LIKE metacharacters in the literal, then map the glob '*' to '%'.
                    String glob = ((String) value)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
                        .replace("*", "%");
                    return col + " LIKE " + lit(glob) + " ESCAPE '\\'";
                }
                return equality(col, "=", value, epoch);
            case NOT_EQUALS:
                return equality(col, "!=", value, epoch);

            case GREATER_THAN:
                return compare(col, ">", value, epoch);
            case GREATER_THAN_OR_EQUAL:
                return compare(col, ">=", value, epoch);
            case LESS_THAN:
                return compare(col, "<", value, epoch);
            case LESS_THAN_OR_EQUAL:
                return compare(col, "<=", value, epoch);

            case APPROX_MATCH:   // no fuzzy in SQL; degrade to substring
            case REGEX:          // SQLite has no REGEXP by default; degrade to substring
            case CONTAINS:
                return col + " LIKE " + likeLit("%", value, "%");
            case BEGINS_WITH:
                return col + " LIKE " + likeLit("", value, "%");
            case ENDS_WITH:
                return col + " LIKE " + likeLit("%", value, "");

            case BETWEEN: {
                String[] parts = String.valueOf(value).split(",");
                if (parts.length != 2) {
                    throw new IllegalArgumentException(":between: requires two values: min,max");
                }
                return col + " BETWEEN " + numeric(parts[0].trim()) + " AND " + numeric(parts[1].trim());
            }

            case WITHIN_DAYS:
                return withinDays(col, value, epoch);
            case YEAR:
                return year(col, value, epoch);

            case INCLUDES:
            case INCLUDES_ANY:
                throw new UnsupportedOperationException(
                    "Array operators (:includes:/:includesAny:) are not supported by the SQL adapter");

            default:
                throw new IllegalArgumentException("Unsupported operator: " + op);
        }
    }

    // --- helpers ----------------------------------------------------------

    private String column(String property) {
        String col = ENVELOPE_COLUMNS.get(property);
        if (col != null) {
            return col;
        }
        return "json_extract(mapped_json, " + jsonPath(property) + ")";
    }

    private boolean isEpochColumn(String property) {
        String col = ENVELOPE_COLUMNS.get(property);
        return col != null && EPOCH_MILLIS_COLUMNS.contains(col);
    }

    /** Equality with case-insensitive collation for strings (matches the evaluator). */
    private String equality(String col, String sqlOp, Object value, boolean epoch) {
        if (epoch && value instanceof String && looksTemporal((String) value)) {
            return col + " " + sqlOp + " " + epochLiteral((String) value);
        }
        if (value instanceof String) {
            return col + " " + sqlOp + " " + lit((String) value) + " COLLATE NOCASE";
        }
        return col + " " + sqlOp + " " + format(value);
    }

    private String compare(String col, String sqlOp, Object value, boolean epoch) {
        if (epoch && value instanceof String && looksTemporal((String) value)) {
            return col + " " + sqlOp + " " + epochLiteral((String) value);
        }
        return col + " " + sqlOp + " " + format(value);
    }

    private String withinDays(String col, Object value, boolean epoch) {
        int days = ((Number) requireNumber(value, ":withinDays:")).intValue();
        if (epoch) {
            return col + " >= (unixepoch('now', '-" + days + " days') * 1000)";
        }
        return "unixepoch(" + col + ") >= unixepoch('now', '-" + days + " days')";
    }

    private String year(String col, Object value, boolean epoch) {
        int y = ((Number) requireNumber(value, ":year:")).intValue();
        if (epoch) {
            return "strftime('%Y', " + col + " / 1000, 'unixepoch') = '" + y + "'";
        }
        return "strftime('%Y', " + col + ") = '" + y + "'";
    }

    /** SQLite JSON path: {@code '$.a.b.c'}, with the path-string single-quote-escaped. */
    private String jsonPath(String property) {
        for (String part : property.split("\\.")) {
            if (part.isEmpty() || !part.matches("[A-Za-z0-9_]+")) {
                throw new IllegalArgumentException("Illegal property path segment: '" + part + "'");
            }
        }
        return lit("$." + property);
    }

    private String likeLit(String pre, Object value, String post) {
        // Escape LIKE metacharacters in the literal value, then wrap with our own.
        String v = String.valueOf(value).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return lit(pre + v + post) + " ESCAPE '\\'";
    }

    private String format(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return lit(value.toString());
    }

    private String numeric(String s) {
        // Validate it parses as a number; emit unquoted.
        Double.parseDouble(s);
        return s;
    }

    private String epochLiteral(String iso) {
        return "(unixepoch(" + lit(iso) + ") * 1000)";
    }

    private boolean looksTemporal(String s) {
        // ISO date or date-time, optionally with zone — enough to tell a date literal
        // from a free-text equality value.
        return s.matches("\\d{4}-\\d{2}-\\d{2}([T ].*)?");
    }

    private Number requireNumber(Object value, String op) {
        if (value instanceof Number) {
            return (Number) value;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(op + " requires a numeric value, got: " + value);
        }
    }

    /** SQL string literal with single quotes doubled. */
    private String lit(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static Object invoke(Object target, String getter) {
        try {
            Method m = target.getClass().getMethod(getter);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to read " + getter + " from " + target.getClass(), e);
        }
    }
}

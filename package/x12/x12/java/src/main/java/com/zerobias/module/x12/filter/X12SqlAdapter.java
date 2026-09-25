package com.zerobias.module.x12.filter;

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
 * SQLite {@code WHERE}-clause fragment over the buffer's {@code transactions} table
 * (DESIGN §2.6). Cloned from hl7/v2's {@code Hl7SqlAdapter}.
 *
 * <p>Property resolution follows the schema property names consumers see:
 * <ul>
 *   <li>Envelope properties resolve to their denormalized columns (the fast search
 *       axes): {@code elementKey, fileId, sourceName, transactionType, gs08, senderId,
 *       receiverId, receivedAt, status, leaseId} (the DESIGN §2.6 list) plus the other
 *       envelope fields that ARE real columns ({@code isaControlNumber, gsControlNumber,
 *       stControlNumber, interchangeDate, envelope, parserErrorCount, schemaId}).</li>
 *   <li>Everything else is a dotted path into the transaction body and resolves into the
 *       OBJECT GRAPH (DESIGN §8.4) — {@code (loop2100.clp.clp02=1)} becomes a scalar
 *       subquery for the {@code clp02} of a {@code clp} instance in that transaction set.
 *       There is no stored document to {@code json_extract} from.</li>
 * </ul>
 *
 * <p>Two deliberate deviations from the SQL generic module's adapter:
 * <ol>
 *   <li><b>Public-getter reflection, enum dispatch.</b> lite-filter's {@code Clause} and
 *       {@code Grouping} are package-private, so their public getters are reflected
 *       and the public {@link ComparisonOperator}/{@link LogicalOperator} enums are
 *       switched on.</li>
 *   <li><b>Epoch-millis date columns.</b> {@code received_at}/{@code acked_at}/
 *       {@code in_flight_until}/{@code interchange_at} are epoch-millis INTEGERs, so ISO
 *       literals and the {@code :withinDays:}/{@code :year:} extensions are emitted via
 *       {@code unixepoch(...)*1000} / {@code strftime(.../1000, 'unixepoch')}.</li>
 * </ol>
 *
 * <p>String equality is emitted {@code COLLATE NOCASE} to match lite-filter's in-memory
 * evaluator (case-insensitive by default); {@code LIKE} is already case-insensitive
 * for ASCII in SQLite.
 */
public class X12SqlAdapter implements Adapter {

    /** lite-filter adapter-registry key. */
    public static final String KEY = "SQL";

    /** Envelope properties → real (denormalized) columns instead of json_extract. */
    private static final Map<String, String> ENVELOPE_COLUMNS = Map.ofEntries(
        Map.entry("elementKey", "element_key"),
        Map.entry("fileId", "file_id"),
        Map.entry("sourceName", "source_name"),
        Map.entry("transactionType", "transaction_type"),
        Map.entry("gs08", "gs08"),
        Map.entry("senderId", "sender_id"),
        Map.entry("receiverId", "receiver_id"),
        Map.entry("receivedAt", "received_at"),
        Map.entry("status", "status"),
        Map.entry("leaseId", "lease_id"),
        Map.entry("isaControlNumber", "isa_control"),
        Map.entry("gsControlNumber", "gs_control"),
        Map.entry("stControlNumber", "st_control"),
        Map.entry("interchangeDate", "interchange_at"),
        Map.entry("envelope", "envelope"),
        Map.entry("parserErrorCount", "parser_error_count"),
        Map.entry("schemaId", "schema_id")
    );

    /** Columns stored as epoch-millis INTEGERs. */
    private static final Set<String> EPOCH_MILLIS_COLUMNS =
        Set.of("received_at", "acked_at", "in_flight_until", "interchange_at");

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
        boolean json = !ENVELOPE_COLUMNS.containsKey(property);

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
                return equality(col, "=", value, epoch, json);
            case NOT_EQUALS:
                return equality(col, "!=", value, epoch, json);

            case GREATER_THAN:
                return compare(col, ">", value, epoch, json);
            case GREATER_THAN_OR_EQUAL:
                return compare(col, ">=", value, epoch, json);
            case LESS_THAN:
                return compare(col, "<", value, epoch, json);
            case LESS_THAN_OR_EQUAL:
                return compare(col, "<=", value, epoch, json);

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
        return graphValue(property);
    }

    /**
     * A body property resolves into the object graph (DESIGN §8.4). There is no stored
     * document to {@code json_extract} from any more: the last path segment is the element and
     * the one before it names the structure that carries it, so {@code loop2100.clp.clp04}
     * becomes "the {@code clp04} of a {@code clp} instance in this transaction set".
     *
     * <p>A scalar subquery, ordered by instance id and limited to one, so the semantics match
     * what {@code json_extract} gave: the FIRST matching instance, not "any". A filter that
     * needs per-instance semantics — every claim over 1000 rather than a transaction whose
     * first claim is — belongs on a business collection, where the grain is the row
     * (DESIGN §8.5).
     *
     * <p>Numerics come back through {@code value_num / 1000000.0}: the column holds exact
     * integer micro-units, so this is the comparison value, while the stored value itself
     * stays exact in {@code value_text}.
     */
    private String graphValue(String property) {
        for (String part : property.split("\\.")) {
            if (part.isEmpty() || !part.matches("[A-Za-z0-9_]+")) {
                throw new IllegalArgumentException("Illegal property path segment: '" + part + "'");
            }
        }
        final int dot = property.lastIndexOf('.');
        final String element = dot < 0 ? property : property.substring(dot + 1);
        final String parentPath = dot < 0 ? null : property.substring(0, dot);
        final String structure = parentPath == null ? null
            : parentPath.substring(parentPath.lastIndexOf('.') + 1);
        final StringBuilder sb = new StringBuilder("(SELECT CASE v2.data_type"
            + " WHEN 'decimal' THEN v2.value_num / 1000000.0"
            + " WHEN 'integer' THEN v2.value_num / 1000000"
            + " ELSE v2.value_text END"
            + " FROM entity_values v2 JOIN entities e2 ON e2.id = v2.entity_id"
            + " WHERE e2.element_key = transactions.element_key AND v2.property = ")
            .append(lit(element));
        if (structure != null) {
            sb.append(" AND e2.property = ").append(lit(structure));
        }
        return sb.append(" ORDER BY e2.id LIMIT 1)").toString();
    }

    private boolean isEpochColumn(String property) {
        String col = ENVELOPE_COLUMNS.get(property);
        return col != null && EPOCH_MILLIS_COLUMNS.contains(col);
    }

    /**
     * Equality with case-insensitive collation for strings (matches the evaluator).
     *
     * <p>lite-filter delivers every literal as a String, but v1 materializes {@code N*}/
     * {@code R} elements as JSON <em>numbers</em> (DESIGN §5) and SQLite never equates
     * {@code 1} with {@code '1'}. So a numeric-looking literal against a JSON path is
     * emitted as {@code (col = '1' COLLATE NOCASE OR col = 1)} — DESIGN §2.6's own
     * example {@code (loop2100.clp.clp02=1)} depends on this. Real columns are TEXT
     * (affinity converts) and need no such alternative.
     */
    private String equality(String col, String sqlOp, Object value, boolean epoch, boolean json) {
        String core;
        if (epoch && value instanceof String && looksTemporal((String) value)) {
            core = col + " " + sqlOp + " " + epochLiteral((String) value);
        } else if (value instanceof String) {
            core = col + " " + sqlOp + " " + lit((String) value) + " COLLATE NOCASE";
            if (json && looksNumeric((String) value)) {
                String num = col + " " + sqlOp + " " + (String) value;
                core = "=".equals(sqlOp) ? "(" + core + " OR " + num + ")" : "(" + core + " AND " + num + ")";
            }
        } else {
            core = col + " " + sqlOp + " " + format(value);
        }
        // "!=" must also select rows where the property is ABSENT — that is what
        // lite-filter's in-memory evaluator does, whereas SQL's NULL != x is NULL.
        return "!=".equals(sqlOp) ? "(" + col + " IS NULL OR " + core + ")" : core;
    }

    /**
     * Ordered comparison. A numeric-looking literal against a JSON path is emitted
     * unquoted: SQLite orders every INTEGER/REAL below every TEXT, so
     * {@code json_extract(...) > '1000'} would be false for the number 1500.
     */
    private String compare(String col, String sqlOp, Object value, boolean epoch, boolean json) {
        if (epoch && value instanceof String && looksTemporal((String) value)) {
            return col + " " + sqlOp + " " + epochLiteral((String) value);
        }
        if (json && value instanceof String && looksNumeric((String) value)) {
            return col + " " + sqlOp + " " + (String) value;
        }
        return col + " " + sqlOp + " " + format(value);
    }

    private String withinDays(String col, Object value, boolean epoch) {
        int days = requireNumber(value, ":withinDays:").intValue();
        if (epoch) {
            return col + " >= (unixepoch('now', '-" + days + " days') * 1000)";
        }
        return "unixepoch(" + col + ") >= unixepoch('now', '-" + days + " days')";
    }

    private String year(String col, Object value, boolean epoch) {
        int y = requireNumber(value, ":year:").intValue();
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
        Double.parseDouble(s); // validate; emit unquoted
        return s;
    }

    private String epochLiteral(String iso) {
        return "(unixepoch(" + lit(iso) + ") * 1000)";
    }

    /** A plain decimal literal (what a JSON number renders as); no exponent, no leading '+'. */
    private boolean looksNumeric(String s) {
        return s.matches("-?\\d+(\\.\\d+)?");
    }

    private boolean looksTemporal(String s) {
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

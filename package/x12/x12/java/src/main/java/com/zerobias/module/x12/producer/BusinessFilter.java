package com.zerobias.module.x12.producer;

import com.zerobias.module.x12.producer.mapping.EntityMapping;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * RFC4515 filters over projected business rows (DESIGN §8.5.1).
 *
 * <p>Supports the shapes a caller actually writes against a claims collection:
 * {@code (paidAmount>=1000)}, {@code (claimStatus=4)}, {@code (patientLastName=DOE*)},
 * {@code (&(payerName=EXAMPLE HEALTH PLAN)(paidAmount<=500))}, {@code (!(claimStatus=22))},
 * and {@code (allowedAmount=*)} for presence.
 *
 * <p>Comparison follows the column's declared type, which is the point of typing the graph:
 * {@code paidAmount} compares as an exact {@link BigDecimal}, never as text, so
 * {@code (paidAmount>=1000)} cannot match "999.99" on a lexical ordering. Dates compare as
 * ISO text, which orders correctly.
 *
 * <p>An unknown attribute is an error rather than an empty result — a filter naming a column
 * that does not exist is a mistake worth reporting, not a query that legitimately matches
 * nothing.
 */
final class BusinessFilter {

    private BusinessFilter() {
    }

    static Predicate<Map<String, Object>> compile(EntityMapping mapping, String filter) {
        final String trimmed = filter.trim();
        final Parser p = new Parser(mapping, trimmed);
        final Predicate<Map<String, Object>> predicate = p.parseFilter();
        p.requireEnd();
        return predicate;
    }

    /** A minimal recursive-descent RFC4515 reader: &, |, !, and the comparison operators. */
    private static final class Parser {

        private final EntityMapping mapping;
        private final String src;
        private int at;

        Parser(EntityMapping mapping, String src) {
            this.mapping = mapping;
            this.src = src;
        }

        Predicate<Map<String, Object>> parseFilter() {
            expect('(');
            final Predicate<Map<String, Object>> out;
            switch (peek()) {
                case '&':
                    at++;
                    out = all(parseList());
                    break;
                case '|':
                    at++;
                    out = any(parseList());
                    break;
                case '!':
                    at++;
                    final Predicate<Map<String, Object>> inner = parseFilter();
                    out = row -> !inner.test(row);
                    break;
                default:
                    out = parseComparison();
                    break;
            }
            expect(')');
            return out;
        }

        private List<Predicate<Map<String, Object>>> parseList() {
            final List<Predicate<Map<String, Object>>> out = new ArrayList<>();
            while (peek() == '(') {
                out.add(parseFilter());
            }
            if (out.isEmpty()) {
                throw new IllegalArgumentException("empty filter list");
            }
            return out;
        }

        private Predicate<Map<String, Object>> parseComparison() {
            final int start = at;
            while (at < src.length() && "<>~=)".indexOf(src.charAt(at)) < 0) {
                at++;
            }
            final String attribute = src.substring(start, at).trim();
            if (attribute.isEmpty()) {
                throw new IllegalArgumentException("missing attribute name");
            }
            final EntityMapping.Column column = mapping.column(attribute);
            if (column == null) {
                throw new IllegalArgumentException("unknown attribute '" + attribute + "' on "
                    + mapping.name() + "; columns: " + mapping.columns().stream()
                        .map(EntityMapping.Column::name).toList());
            }
            final String op = readOperator();
            final int valueStart = at;
            while (at < src.length() && src.charAt(at) != ')') {
                at++;
            }
            final String value = src.substring(valueStart, at).trim();
            return predicate(column, op, value);
        }

        private String readOperator() {
            if (at + 1 < src.length() && src.charAt(at + 1) == '=') {
                final char c = src.charAt(at);
                if (c == '>' || c == '<' || c == '~') {
                    at += 2;
                    return c + "=";
                }
            }
            if (at < src.length() && src.charAt(at) == '=') {
                at++;
                return "=";
            }
            throw new IllegalArgumentException("expected =, >=, <= or ~= at offset " + at);
        }

        private char peek() {
            return at < src.length() ? src.charAt(at) : '\0';
        }

        private void expect(char c) {
            if (at >= src.length() || src.charAt(at) != c) {
                throw new IllegalArgumentException("expected '" + c + "' at offset " + at);
            }
            at++;
        }

        void requireEnd() {
            if (at != src.length()) {
                throw new IllegalArgumentException("trailing input at offset " + at);
            }
        }
    }

    private static Predicate<Map<String, Object>> all(List<Predicate<Map<String, Object>>> parts) {
        return row -> {
            for (Predicate<Map<String, Object>> p : parts) {
                if (!p.test(row)) {
                    return false;
                }
            }
            return true;
        };
    }

    private static Predicate<Map<String, Object>> any(List<Predicate<Map<String, Object>>> parts) {
        return row -> {
            for (Predicate<Map<String, Object>> p : parts) {
                if (p.test(row)) {
                    return true;
                }
            }
            return false;
        };
    }

    private static Predicate<Map<String, Object>> predicate(EntityMapping.Column column, String op,
            String value) {
        final String name = column.name();
        if ("=".equals(op) && "*".equals(value)) {
            return row -> row.get(name) != null;   // presence
        }
        final boolean numeric = "decimal".equals(column.dataType()) || "integer".equals(column.dataType());
        if (numeric) {
            final BigDecimal bound = decimal(value);
            return row -> {
                final BigDecimal actual = decimal(row.get(name));
                if (actual == null) {
                    return false;
                }
                final int cmp = actual.compareTo(bound);
                switch (op) {
                    case ">=": return cmp >= 0;
                    case "<=": return cmp <= 0;
                    case "~=": return cmp == 0;
                    default:   return cmp == 0;
                }
            };
        }
        if ("=".equals(op) && (value.startsWith("*") || value.endsWith("*"))) {
            final String needle = value.replace("*", "").toLowerCase(Locale.ROOT);
            final boolean prefix = value.endsWith("*") && !value.startsWith("*");
            final boolean suffix = value.startsWith("*") && !value.endsWith("*");
            return row -> {
                final Object actual = row.get(name);
                if (actual == null) {
                    return false;
                }
                final String text = String.valueOf(actual).toLowerCase(Locale.ROOT);
                if (prefix) {
                    return text.startsWith(needle);
                }
                if (suffix) {
                    return text.endsWith(needle);
                }
                return text.contains(needle);
            };
        }
        return row -> {
            final Object actual = row.get(name);
            if (actual == null) {
                return false;
            }
            final String text = String.valueOf(actual);
            switch (op) {
                case ">=": return text.compareToIgnoreCase(value) >= 0;
                case "<=": return text.compareToIgnoreCase(value) <= 0;
                case "~=": return text.equalsIgnoreCase(value);
                default:   return text.equalsIgnoreCase(value);
            }
        };
    }

    private static BigDecimal decimal(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal) {
            return (BigDecimal) raw;
        }
        if (raw instanceof Number) {
            return new BigDecimal(raw.toString());
        }
        try {
            return new BigDecimal(String.valueOf(raw).trim());
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException("'" + raw + "' is not a number");
        }
    }
}

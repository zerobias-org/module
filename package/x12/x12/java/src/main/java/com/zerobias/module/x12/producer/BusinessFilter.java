package com.zerobias.module.x12.producer;

import com.zerobias.litefilter.Expression;
import com.zerobias.module.x12.filter.X12Filter;
import com.zerobias.module.x12.producer.mapping.EntityMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * RFC4515 filters over projected business rows (DESIGN §8.5.1).
 *
 * <p><b>One parser.</b> The filter is parsed by lite-filter — the same parser the structural
 * path uses through {@link X12Filter} — and evaluated with lite-filter's own
 * {@link Expression#matches(Object)} against the projected row. Nothing here reimplements
 * RFC4515, so the two paths cannot drift in what they accept: {@code &}, {@code |},
 * {@code !}, the comparison operators, presence, and the {@code :contains:} /
 * {@code :startsWith:} / {@code :endsWith:} extensions all behave identically on a claims
 * collection and on {@code /transactions}.
 *
 * <p>The structural path compiles the same expression to SQL instead (grain, file and
 * dimension scoping are already pushed down; see {@code BusinessEntities} for why a business
 * column cannot be). What this class adds on top of lite-filter is the one thing the library
 * cannot know: which attribute names a given business entity actually has.
 *
 * <p>Comparison typing comes from the projected row, not from this class — an amount is a
 * {@code BigDecimal} and a date an ISO string by the time the evaluator sees it, so
 * {@code (paidAmount>=1000)} is a numeric comparison and cannot match {@code 999.99}
 * lexically.
 */
final class BusinessFilter {

    private BusinessFilter() {
    }

    /**
     * Parse and bind a filter to {@code mapping}. Throws {@link IllegalArgumentException} for a
     * malformed filter or an attribute the entity does not have — a typo in a filter should say
     * so rather than return an empty page that looks like "no matches".
     */
    static Predicate<Map<String, Object>> compile(EntityMapping mapping, String filter) {
        final Expression expression = X12Filter.parse(filter);
        requireKnownAttributes(mapping, expression);
        return row -> expression.matches(row == null ? Map.of() : row);
    }

    /**
     * Every attribute the expression names must be a column of the entity, a dimension it
     * carries, or one of the provenance fields every row has.
     *
     * <p>lite-filter's {@code Clause}/{@code Grouping} are package-private, so their public
     * getters are reflected — the same approach {@code X12SqlAdapter} takes for the SQL side.
     */
    private static void requireKnownAttributes(EntityMapping mapping, Expression expression) {
        final Map<String, Boolean> known = new LinkedHashMap<>();
        for (EntityMapping.Column c : mapping.columns()) {
            known.put(c.name(), true);
        }
        for (EntityMapping.Dimension d : mapping.dimensions()) {
            known.put(d.name(), true);
        }
        known.put("elementKey", true);
        known.put("fileId", true);

        final List<String> unknown = new ArrayList<>();
        collectAttributes(expression, unknown, known);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("unknown attribute" + (unknown.size() > 1 ? "s " : " ")
                + unknown + " on " + mapping.name() + "; available: " + known.keySet());
        }
    }

    private static void collectAttributes(Expression expression, List<String> unknown,
            Map<String, Boolean> known) {
        final Object property = invokeOrNull(expression, "getProperty");
        if (property != null) {
            final String name = property.toString();
            if (!known.containsKey(name) && !unknown.contains(name)) {
                unknown.add(name);
            }
            return;
        }
        final Object children = invokeOrNull(expression, "getExpressions");
        if (children instanceof List) {
            for (Object child : (List<?>) children) {
                if (child instanceof Expression) {
                    collectAttributes((Expression) child, unknown, known);
                }
            }
        }
    }

    /** A getter the node may not have (a clause has no children, a grouping no property). */
    private static Object invokeOrNull(Object target, String getter) {
        try {
            final Method m = target.getClass().getMethod(getter);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException notThisNodeType) {
            return null;
        }
    }
}

package com.zerobias.module.x12.filter;

import com.zerobias.litefilter.Expression;

/**
 * Thin facade over lite-filter for the buffer's search path: parse an RFC4515
 * filter string into an {@link Expression} and render it to a SQLite
 * {@code WHERE}-clause fragment via {@link X12SqlAdapter}.
 *
 * <p>The adapter is registered once under {@link X12SqlAdapter#KEY} so the normal
 * lite-filter call site ({@code expression.as("SQL")}) works too.
 */
public final class X12Filter {

    static {
        Expression.addAdapter(
            X12SqlAdapter.KEY,
            "X12 buffer SQLite adapter (envelope columns + json_extract over mapped_json)",
            new X12SqlAdapter());
    }

    private X12Filter() {
    }

    /** Force class-init (and thus adapter registration). */
    public static void register() {
        // no-op; the static initializer does the work
    }

    /** Parse an RFC4515 filter string. */
    public static Expression parse(String filter) {
        return Expression.parse(filter);
    }

    /** Render a parsed expression to a SQLite WHERE-clause fragment. */
    public static String toWhereClause(Expression expression) {
        register();
        return expression.as(X12SqlAdapter.KEY);
    }

    /**
     * A validated {@code ORDER BY} fragment for the buffer's search path, or null when no sort
     * was asked for. {@code sortBy} resolves through the same property mapping the filter uses
     * (envelope column, or a graph lookup for a body path); {@code sortDir} accepts
     * {@code asc}/{@code desc} in any case.
     *
     * <p>NULLs sort last in both directions, so a page is never led by rows missing the very
     * field they were sorted on.
     *
     * @throws IllegalArgumentException for an unusable property path or an unknown direction
     */
    public static String orderBy(String sortBy, String sortDir) {
        if (sortBy == null || sortBy.trim().isEmpty()) {
            return null;
        }
        final String dir = direction(sortDir);
        final String expr = X12SqlAdapter.orderExpression(sortBy.trim());
        return "(" + expr + ") IS NULL, " + expr + " " + dir;
    }

    /** {@code asc}/{@code desc}, case-insensitive; blank means ascending. */
    public static String direction(String sortDir) {
        if (sortDir == null || sortDir.trim().isEmpty()) {
            return "ASC";
        }
        final String d = sortDir.trim().toUpperCase(java.util.Locale.ROOT);
        if (!"ASC".equals(d) && !"DESC".equals(d)) {
            throw new IllegalArgumentException("sortDir must be asc or desc, got '" + sortDir + "'");
        }
        return d;
    }

    /** Parse and render in one step. Returns {@code null} for a null/blank filter. */
    public static String toWhereClause(String filter) {
        if (filter == null || filter.trim().isEmpty()) {
            return null;
        }
        return toWhereClause(parse(filter));
    }
}

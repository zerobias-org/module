package com.zerobias.module.hl7.filter;

import com.zerobias.litefilter.Expression;

/**
 * Thin facade over lite-filter for the buffer's search path: parse an RFC4515
 * filter string into an {@link Expression} and render it to a SQLite
 * {@code WHERE}-clause fragment via {@link Hl7SqlAdapter}.
 *
 * <p>The adapter is registered once under {@link Hl7SqlAdapter#KEY} so the
 * normal lite-filter call site ({@code expression.as("SQL")}) works too.
 */
public final class Hl7Filter {

    static {
        Expression.addAdapter(
            Hl7SqlAdapter.KEY,
            "HL7 buffer SQLite adapter (envelope columns + json_extract over mapped_json)",
            new Hl7SqlAdapter());
    }

    private Hl7Filter() {
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
        return expression.as(Hl7SqlAdapter.KEY);
    }

    /** Parse and render in one step. Returns {@code null} for a null/blank filter. */
    public static String toWhereClause(String filter) {
        if (filter == null || filter.trim().isEmpty()) {
            return null;
        }
        return toWhereClause(parse(filter));
    }
}

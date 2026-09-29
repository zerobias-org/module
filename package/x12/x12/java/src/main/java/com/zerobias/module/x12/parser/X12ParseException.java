package com.zerobias.module.x12.parser;

import java.util.List;

/**
 * A file that cannot be ingested (DESIGN §4.2e): structural failure, unreadable
 * separators, an unsupported implementation guide, or imsweb fatal errors. The message
 * starts with a stable kebab-case kind ({@code unsupported-guide}, {@code no-isa},
 * {@code bare-transaction-set}, {@code fatal}, ...) so operators and tests can match on
 * it; {@link #fatalErrors()} carries imsweb's {@code getFatalErrors()} verbatim when the
 * parser produced them.
 */
public final class X12ParseException extends Exception {

    private final List<String> fatalErrors;

    public X12ParseException(String message) {
        this(message, List.of());
    }

    public X12ParseException(String message, List<String> fatalErrors) {
        super(fatalErrors.isEmpty() ? message : message + " " + fatalErrors);
        this.fatalErrors = List.copyOf(fatalErrors);
    }

    public List<String> fatalErrors() {
        return fatalErrors;
    }
}

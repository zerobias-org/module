package com.zerobias.module.x12.codegen;

import java.util.regex.Pattern;

/**
 * Canonical DataProducer schema-id construction + validation for X12 content
 * (DESIGN §2.2, interface SchemaIds.md).
 *
 * <p>Form: {@code schema:{type}:{catalog}.{schema}.{name}[:{direction}]}. For X12
 * the catalog token is always {@code x12}; the schema slot is the implementation
 * guide id (GS08, e.g. {@code 005010X221A1}) for guide-bound content,
 * {@code codes} for data-element code sets and {@code ops} for the receiver's
 * own enums/functions.
 */
public final class SchemaIds {

    /** Catalog token — constant for all X12 content. */
    public static final String CATALOG = "x12";
    /** Schema slot for the data-element code-set enums. */
    public static final String CODES = "codes";
    /** Schema slot for receiver-owned enums and functions. */
    public static final String OPS = "ops";

    /** The interface's canonical validation pattern (interface SchemaIds.md / api.yml). */
    public static final Pattern CANONICAL = Pattern.compile(
        "^(schema:(table|view|type|enum):[^:.]+\\.[^:.]+\\.[^:.]+"
        + "|schema:function:[^:.]+\\.[^:.]+\\.[^:.]+:(input|output)"
        + "|schema:shared:[^:]+)$");

    private SchemaIds() {
    }

    /** {@code schema:table:x12.<GS08>.<TS>} — transaction sets (collection schemas). */
    public static String table(String gs08, String transactionType) {
        return "schema:table:" + CATALOG + "." + gs08 + "." + transactionType;
    }

    /** {@code schema:type:x12.<GS08>.<xid>} — loops, segments, composites. */
    public static String type(String gs08, String xid) {
        return "schema:type:" + CATALOG + "." + gs08 + "." + xid;
    }

    /** {@code schema:enum:x12.codes.<dataEle>} — a data element's code set. */
    public static String codes(String dataEle) {
        return "schema:enum:" + CATALOG + "." + CODES + "." + dataEle;
    }

    /** {@code schema:enum:x12.ops.<Name>} — receiver-owned enums (status values, ...). */
    public static String opsEnum(String name) {
        return "schema:enum:" + CATALOG + "." + OPS + "." + name;
    }

    /** {@code schema:shared:x12.<name>} — reusable, non-guide-scoped (envelope, file, stats). */
    public static String shared(String name) {
        return "schema:shared:" + CATALOG + "." + name;
    }

    public static boolean isValid(String id) {
        return id != null && CANONICAL.matcher(id).matches();
    }

    /** Throws if {@code id} is not a canonical schema id — used to fail the build on drift. */
    public static String requireValid(String id) {
        if (!isValid(id)) {
            throw new IllegalArgumentException("non-canonical schema id: " + id);
        }
        return id;
    }
}

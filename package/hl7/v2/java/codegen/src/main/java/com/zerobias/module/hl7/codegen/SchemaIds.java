package com.zerobias.module.hl7.codegen;

import java.util.regex.Pattern;

/**
 * Canonical DataProducer schema-id construction + validation for HL7 content
 * (DESIGN §2.2, interface SchemaIds.md).
 *
 * <p>Form: {@code schema:{type}:{catalog}.{schema}.{name}[:{direction}]}. For HL7
 * the catalog token is always {@code hl7v2} and the schema slot is the version
 * (e.g. {@code v251}) for spec content, or an extension namespace (e.g. {@code epic}).
 */
public final class SchemaIds {

    /** Catalog token — constant for all HL7 v2 content. */
    public static final String CATALOG = "hl7v2";

    /** The interface's canonical validation pattern (interface SchemaIds.md / api.yml). */
    public static final Pattern CANONICAL = Pattern.compile(
        "^(schema:(table|view|type|enum):[^:.]+\\.[^:.]+\\.[^:.]+"
        + "|schema:function:[^:.]+\\.[^:.]+\\.[^:.]+:(input|output)"
        + "|schema:shared:[^:]+)$");

    private SchemaIds() {
    }

    /** {@code schema:table:hl7v2.<schema>.<name>} — message structures (collection schemas). */
    public static String table(String schema, String name) {
        return "schema:table:" + CATALOG + "." + schema + "." + name;
    }

    /** {@code schema:type:hl7v2.<schema>.<name>} — segments, groups, composite datatypes. */
    public static String type(String schema, String name) {
        return "schema:type:" + CATALOG + "." + schema + "." + name;
    }

    /** {@code schema:enum:hl7v2.<schema>.HL7nnnn} — HL7 table / value set, 4-digit zero-padded. */
    public static String enumTable(String schema, int hl7TableNumber) {
        return "schema:enum:" + CATALOG + "." + schema + "." + String.format("HL7%04d", hl7TableNumber);
    }

    /** {@code schema:shared:<name>} — reusable, non-version-scoped (e.g. the message envelope). */
    public static String shared(String name) {
        return "schema:shared:" + name;
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

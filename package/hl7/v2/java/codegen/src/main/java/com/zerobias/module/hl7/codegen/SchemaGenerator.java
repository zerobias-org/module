package com.zerobias.module.hl7.codegen;

/**
 * Build-time schema generator (DESIGN §6).
 *
 * <p>Walks the HAPI typed structure classes for an HL7 version and emits, under
 * {@code <outputDir>}:
 * <ul>
 *   <li>{@code schemas/<version>/messages/*.json}   — {@code schema:table:hl7v2.<v>.<MSG>}</li>
 *   <li>{@code schemas/<version>/segments/*.json}    — {@code schema:type:hl7v2.<v>.<SEG>}</li>
 *   <li>{@code schemas/<version>/datatypes/*.json}   — {@code schema:type:hl7v2.<v>.<DT>}</li>
 *   <li>{@code schemas/<version>/tables/*.json}      — {@code schema:enum:hl7v2.<v>.<HL7nnnn>}</li>
 *   <li>{@code structure-index/<version>.json}       — runtime materializer driver</li>
 * </ul>
 *
 * <p>Generated output is committed to git (reviewable diffs across HL7 versions)
 * and served at request time from the classpath with no reflection — this tool
 * runs only on demand. This is Phase 1 in PLAN.md; this class is a stub.
 *
 * <p>Usage: {@code SchemaGenerator <version> <outputDir>}
 * (e.g. {@code SchemaGenerator v251 ../src/main/resources}).
 */
public final class SchemaGenerator {

    private SchemaGenerator() {
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: SchemaGenerator <version> <outputDir>  (e.g. v251 ../src/main/resources)");
            System.exit(2);
            return;
        }
        final String version = args[0];
        final String outputDir = args[1];

        // TODO(Phase 1):
        //   1. Resolve the HAPI structure package for `version`
        //      (e.g. ca.uhn.hl7v2.model.v251.*).
        //   2. Walk messages -> segments -> datatypes; map HL7 primitives to core
        //      dataTypes per DESIGN §2.4; emit composition `references` per §2.3.
        //   3. Emit table/value-set enums (schema:enum:hl7v2.<v>.HL7nnnn).
        //   4. Emit structure-index/<version>.json (PID-3 -> patientIdentifierList,
        //      multi, type CX, ...) for the runtime materializer.
        //   5. Apply the SchemaId namespace rules from DESIGN §2.2.
        throw new UnsupportedOperationException(
            "SchemaGenerator not yet implemented (Phase 1). version=" + version + " outputDir=" + outputDir);
    }
}

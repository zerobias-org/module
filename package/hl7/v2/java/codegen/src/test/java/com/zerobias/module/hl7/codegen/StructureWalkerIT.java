package com.zerobias.module.hl7.codegen;

import com.zerobias.module.hl7.codegen.model.Property;
import com.zerobias.module.hl7.codegen.model.Schema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance test for Phase 1 (PLAN.md): walks {@code ADT_A01} against HAPI's
 * v2.5.1 structures and asserts the worked traversal from DESIGN §2.3 —
 * {@code ADT_A01 → PID → CX → HD}, with the table-bound enum references
 * (administrative sex → HL70001, identifier type code → HL70203).
 *
 * <p>Requires {@code hapi-structures-v251} on the test classpath (it is a
 * dependency of the codegen module), so it runs only under the build toolchain,
 * not the toolchain-independent {@link PureHelpersTest}.
 */
class StructureWalkerIT {

    @Test
    void walksAdtA01ToEnumLeaves() throws Exception {
        final StructureWalker w = new StructureWalker("v251");
        w.walkMessage("ADT_A01");

        // Structures discovered transitively.
        assertTrue(w.messages.containsKey("ADT_A01"), "message ADT_A01");
        assertTrue(w.segments.containsKey("MSH"), "segment MSH");
        assertTrue(w.segments.containsKey("PID"), "segment PID");
        assertTrue(w.datatypes.containsKey("CX"), "datatype CX");
        assertTrue(w.datatypes.containsKey("HD"), "datatype HD");

        // Every emitted schema id is canonical.
        w.messages.values().forEach(s -> assertTrue(SchemaIds.isValid(s.id), s.id));
        w.segments.values().forEach(s -> assertTrue(SchemaIds.isValid(s.id), s.id));
        w.datatypes.values().forEach(s -> assertTrue(SchemaIds.isValid(s.id), s.id));

        // ADT_A01 references the PID segment by composition.
        final Property pidProp = prop(w.messages.get("ADT_A01"), "pid");
        assertNotNull(pidProp, "ADT_A01.pid property");
        assertEquals(SchemaIds.type("v251", "PID"), pidProp.references.schemaId);

        // PID.patientIdentifierList is a repeating CX composition (DESIGN §2.3).
        final Property pidList = prop(w.segments.get("PID"), "patientIdentifierList");
        assertNotNull(pidList, "PID.patientIdentifierList property");
        assertEquals(Boolean.TRUE, pidList.multi);
        assertEquals(SchemaIds.type("v251", "CX"), pidList.references.schemaId);

        // CX composes HD (assigning authority) and carries an HL70203 enum reference.
        final Schema cx = w.datatypes.get("CX");
        assertTrue(cx.properties.stream().anyMatch(p ->
                p.references != null && SchemaIds.type("v251", "HD").equals(p.references.schemaId)),
            "CX references HD");
        assertTrue(cx.properties.stream().anyMatch(p ->
                p.references != null && SchemaIds.enumTable("v251", 203).equals(p.references.schemaId)),
            "CX references enum HL70203");

        // Table-bound fields registered the enums (HL70001 admin sex, HL70203 id type).
        assertTrue(w.tables.contains(1), "table HL70001 registered");
        assertTrue(w.tables.contains(203), "table HL70203 registered");
    }

    private static Property prop(Schema schema, String name) {
        if (schema == null) {
            return null;
        }
        return schema.properties.stream()
            .filter(p -> name.equals(p.name))
            .findFirst()
            .orElse(null);
    }
}

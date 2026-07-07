package com.zerobias.module.hl7.codegen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Toolchain-independent unit tests for the codegen helpers. These cover the
 * contract-critical logic that does not require HAPI: schema-id formatting,
 * HAPI positional-accessor parsing (property naming), and the §2.4 primitive
 * type mapping.
 */
class PureHelpersTest {

    @Test
    void schemaIds() {
        assertEquals("schema:table:hl7v2.v27.ADT_A01", SchemaIds.table("v27", "ADT_A01"));
        assertEquals("schema:type:hl7v2.v27.PID", SchemaIds.type("v27", "PID"));
        assertEquals("schema:enum:hl7v2.v27.HL70001", SchemaIds.enumTable("v27", 1));
        assertEquals("schema:enum:hl7v2.v27.HL70203", SchemaIds.enumTable("v27", 203));
        assertEquals("schema:shared:hl7v2.message-envelope", SchemaIds.shared("hl7v2.message-envelope"));

        assertTrue(SchemaIds.isValid("schema:table:hl7v2.v27.ADT_A01"));
        assertTrue(SchemaIds.isValid("schema:enum:hl7v2.v27.HL70001"));
        assertTrue(SchemaIds.isValid("schema:shared:hl7v2.message-envelope"));
        assertFalse(SchemaIds.isValid("table:foo"));
    }

    @Test
    void positionalAccessorParsing() {
        var pid3 = HapiNames.parseAccessor("getPid3_PatientIdentifierList").orElseThrow();
        assertEquals(3, pid3.index());
        assertEquals("patientIdentifierList", pid3.beanName());

        // Leading-acronym fields keep their casing (JavaBeans decapitalize rule).
        assertEquals("IDNumber", HapiNames.parseAccessor("getCx1_IDNumber").orElseThrow().beanName());
        assertEquals("messageType", HapiNames.parseAccessor("getMsh9_MessageType").orElseThrow().beanName());

        // Non-positional accessors (the "clean" getters) are not matched.
        assertTrue(HapiNames.parseAccessor("getMessage").isEmpty());
    }

    @Test
    void prefixAnchoredParsingHandlesDigitEndingSegments() {
        // GT1 (accessor prefix "Gt1"): the generic parser splits getGt11_ as
        // "Gt" + index 11 (folding the segment's trailing 1 into the index), which
        // collides fields 1-9 with 11-19. Prefix-anchored parsing keeps them apart.
        assertEquals("Gt1", HapiNames.accessorPrefix("GT1"));
        assertEquals("Pid", HapiNames.accessorPrefix("PID"));
        assertEquals("Cx", HapiNames.accessorPrefix("CX"));

        var gt1_1 = HapiNames.parseAccessor("getGt11_SetIDGT1", "Gt1").orElseThrow();
        assertEquals(1, gt1_1.index());
        assertEquals("setIDGT1", gt1_1.beanName());

        var gt1_11 = HapiNames.parseAccessor("getGt111_GuarantorRelationship", "Gt1").orElseThrow();
        assertEquals(11, gt1_11.index());
        assertEquals("guarantorRelationship", gt1_11.beanName());

        // Non-digit-ending segments still resolve against their own prefix.
        assertEquals(3, HapiNames.parseAccessor("getPid3_PatientIdentifierList", "Pid").orElseThrow().index());
        // A method for a different structure does not match this prefix.
        assertTrue(HapiNames.parseAccessor("getPid3_PatientIdentifierList", "Gt1").isEmpty());
    }

    @Test
    void descriptionFallback() {
        assertEquals("patientIdentifierList", HapiNames.fromDescription("Patient Identifier List"));
        assertEquals("setIDPID", HapiNames.fromDescription("Set ID - PID"));
    }

    @Test
    void primitiveMapping() {
        assertEquals("decimal", CoreTypes.forPrimitive("NM"));
        assertEquals("integer", CoreTypes.forPrimitive("SI"));
        assertEquals("date", CoreTypes.forPrimitive("DT"));
        assertEquals("date-time", CoreTypes.forPrimitive("DTM"));
        assertEquals("string", CoreTypes.forPrimitive("TM"));
        assertEquals("time", CoreTypes.formatHint("TM"));
        assertTrue(CoreTypes.isEnumBound("ID"));
        assertFalse(CoreTypes.isEnumBound("ST"));
        assertEquals("string", CoreTypes.forPrimitive("ZZZ"));
    }
}

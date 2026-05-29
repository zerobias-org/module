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
        assertEquals("schema:table:hl7v2.v251.ADT_A01", SchemaIds.table("v251", "ADT_A01"));
        assertEquals("schema:type:hl7v2.v251.PID", SchemaIds.type("v251", "PID"));
        assertEquals("schema:enum:hl7v2.v251.HL70001", SchemaIds.enumTable("v251", 1));
        assertEquals("schema:enum:hl7v2.v251.HL70203", SchemaIds.enumTable("v251", 203));
        assertEquals("schema:shared:hl7v2.message-envelope", SchemaIds.shared("hl7v2.message-envelope"));

        assertTrue(SchemaIds.isValid("schema:table:hl7v2.v251.ADT_A01"));
        assertTrue(SchemaIds.isValid("schema:enum:hl7v2.v251.HL70001"));
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

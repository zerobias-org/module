package com.zerobias.module.x12.codegen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Toolchain-independent helpers: schema ids (DESIGN §2.2), naming (§2.3), type mapping (§2.4). */
class PureHelpersTest {

    @Test
    void schemaIdsMatchTheCanonicalRegex() {
        assertEquals("schema:table:x12.005010X221A1.835", SchemaIds.table("005010X221A1", "835"));
        assertEquals("schema:type:x12.005010X221A1.2100", SchemaIds.type("005010X221A1", "2100"));
        assertEquals("schema:type:x12.005010X221A1.CLP", SchemaIds.type("005010X221A1", "CLP"));
        assertEquals("schema:type:x12.005010X221A1.C022", SchemaIds.type("005010X221A1", "C022"));
        assertEquals("schema:enum:x12.codes.1029", SchemaIds.codes("1029"));
        assertEquals("schema:enum:x12.codes.I01", SchemaIds.codes("I01"));
        assertEquals("schema:enum:x12.ops.TransactionStatus", SchemaIds.opsEnum("TransactionStatus"));
        assertEquals("schema:shared:x12.transaction-envelope", SchemaIds.shared("transaction-envelope"));

        for (String id : new String[] {
            SchemaIds.table("005010X221A1", "835"), SchemaIds.type("005010X223A2", "2010AA"),
            SchemaIds.codes("1029"), SchemaIds.opsEnum("FileStatus"), SchemaIds.shared("file"),
            "schema:function:x12.ops.take:input"}) {
            assertTrue(SchemaIds.isValid(id), id);
            assertEquals(id, SchemaIds.requireValid(id));
        }
        assertFalse(SchemaIds.isValid("table:foo"));
        assertFalse(SchemaIds.isValid("schema:table:x12.835"));                 // missing a slot
        assertFalse(SchemaIds.isValid("schema:type:x12.005010X221A1.C022.01")); // dot in name
        assertFalse(SchemaIds.isValid("schema:function:x12.ops.take"));         // no direction
        assertFalse(SchemaIds.isValid(null));
        assertThrows(IllegalArgumentException.class, () -> SchemaIds.requireValid("schema:table:x12.835"));
    }

    @Test
    void propertyNaming() {
        assertEquals("loop2100", Names.loopProperty("2100"));
        assertEquals("loop1000A", Names.loopProperty("1000A"));
        assertEquals("header", Names.loopProperty("HEADER"));
        assertEquals("isaLoop", Names.loopProperty("ISA_LOOP"));
        assertEquals("stLoop", Names.loopProperty("ST_LOOP"));
        assertEquals("clp", Names.segmentProperty("CLP"));
        assertEquals("nm1", Names.segmentProperty("NM1"));
        assertEquals("clp01", Names.elementProperty("CLP01"));
        assertEquals("c00301", Names.compositeElementProperty("C003", 1));
        assertEquals("c02212", Names.compositeElementProperty("C022", 12));
    }

    @Test
    void x12TypeMapping() {
        assertEquals("string", CoreTypes.forX12("AN"));
        assertEquals("string", CoreTypes.forX12("ID"));
        assertEquals("decimal", CoreTypes.forX12("R"));
        assertEquals("decimal", CoreTypes.forX12("N0"));
        assertEquals("decimal", CoreTypes.forX12("N2"));
        assertEquals("date", CoreTypes.forX12("DT"));
        assertEquals("string", CoreTypes.forX12("TM"));
        assertEquals("time", CoreTypes.formatHint("TM"));
        assertNull(CoreTypes.formatHint("DT"));
        assertEquals("byte", CoreTypes.forX12("B"));
        assertEquals("string", CoreTypes.forX12("ZZ"));
        assertEquals("string", CoreTypes.forX12(null));
        assertEquals(Integer.valueOf(2), CoreTypes.impliedDecimals("N2"));
        assertEquals(Integer.valueOf(0), CoreTypes.impliedDecimals("N0"));
        assertNull(CoreTypes.impliedDecimals("R"));
        assertNull(CoreTypes.impliedDecimals("AN"));
        // Every core type the generator emits has a definition.
        for (String t : new String[] {"string", "integer", "decimal", "boolean", "date", "date-time", "byte", "mimeType"}) {
            assertEquals(t, CoreTypes.definition(t).name);
        }
    }

    @Test
    void guideCatalog() {
        assertEquals(8, GuideCatalog.all().size());
        assertEquals("835", GuideCatalog.find("005010X221A1").transactionType());
        assertEquals("005010X223A2", GuideCatalog.find("005010X223A1").gs08(), "alias resolves to canonical");
        assertEquals("005010X231A1", GuideCatalog.find("005010X231").gs08());
        assertNull(GuideCatalog.find("004010X098A1"));
        assertEquals(java.util.List.of("005010X223A2", "005010X223A1"), GuideCatalog.labels(GuideCatalog.find("005010X223A2")));
        assertEquals(java.util.List.of(GuideCatalog.find("005010X221A1")), SchemaGenerator.resolveGuides("005010X221A1"));
        assertThrows(IllegalArgumentException.class, () -> SchemaGenerator.resolveGuides("005010X999"));
    }
}

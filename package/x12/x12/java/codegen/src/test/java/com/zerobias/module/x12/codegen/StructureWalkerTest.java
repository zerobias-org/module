package com.zerobias.module.x12.codegen;

import com.zerobias.module.x12.codegen.mapping.Mapping;
import com.zerobias.module.x12.codegen.mapping.MappingLoader;
import com.zerobias.module.x12.codegen.model.Property;
import com.zerobias.module.x12.codegen.model.Schema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Walks the 835 (005010X221A1) map from the x12-parser jar and asserts the
 * worked traversal from DESIGN §2.3/§2.4: {@code 835 → DETAIL → 2000 → 2100 → CLP},
 * with CLP02 a coded string (enum reference) and CLP03/CLP04 money as decimal.
 */
class StructureWalkerTest {

    private static Map<String, Mapping.DataElement> dataElements;
    private static CodeRegistry codes;
    private static StructureWalker.Generated g835;

    @BeforeAll
    static void walk835() {
        final MappingLoader loader = new MappingLoader();
        dataElements = loader.loadDataElements();
        codes = new CodeRegistry(loader.loadCodeSets());
        final GuideCatalog.Guide g = GuideCatalog.find("005010X221A1");
        final StructureWalker w = new StructureWalker(g.gs08(), g.transactionType(), null, g.mapFile(), dataElements, codes);
        w.walk(loader.loadTransaction(g.mapFile()));
        g835 = w.emit();
    }

    @Test
    void clpSegmentTypes() {
        final Schema clp = g835.segments.get("CLP");
        assertNotNull(clp, "segment CLP");
        assertEquals(SchemaIds.type("005010X221A1", "CLP"), clp.id);

        final Property clp01 = clp.property("clp01");
        assertNotNull(clp01);
        assertEquals("string", clp01.dataType);
        assertTrue(clp01.required);
        assertNull(clp01.references, "CLP01 (AN 1028) has no code set");
        assertEquals("Patient Control Number", clp01.description);

        final Property clp02 = clp.property("clp02");
        assertNotNull(clp02);
        assertEquals("string", clp02.dataType);
        assertNotNull(clp02.references, "CLP02 is an ID element with valid_codes");
        assertEquals(SchemaIds.codes("1029"), clp02.references.schemaId);
        assertNull(clp02.references.propertyName, "composition/enum reference, not a foreign key");
        assertTrue(codes.codesOf("1029").containsAll(java.util.List.of("1", "2", "3", "4", "19", "20", "21", "22", "23", "25")));

        assertEquals("decimal", clp.property("clp03").dataType, "CLP03 Total Claim Charge Amount (R 782)");
        assertEquals("decimal", clp.property("clp04").dataType, "CLP04 Claim Payment Amount (R 782)");
        assertTrue(clp.property("clp03").required);
        assertFalse(clp.property("clp05").required, "CLP05 is situational");
        assertEquals(SchemaIds.codes("1032"), clp.property("clp06").references.schemaId, "Claim Filing Indicator Code");
        assertNull(clp.property("clp07").references,
            "CLP07 is generic AN 127: codes.xml's remark_code codeset declares 127 but only IG-coded positions reference it");
        assertNull(clp.property("clp09").references, "CLP09 (ID 1325) has no valid_codes in the 835 map");
        assertEquals(14, clp.properties.size(), "CLP01..CLP14, not-used positions included");
        assertNull(clp.property("clp02").multi);
    }

    @Test
    void loopCompositionAndCardinality() {
        // Transaction table references the ST_LOOP children (ST, HEADER, DETAIL, ..., SE).
        final Schema table = g835.table;
        assertEquals(SchemaIds.table("005010X221A1", "835"), table.id);
        assertNotNull(table.property("st"));
        assertEquals(SchemaIds.type("005010X221A1", "ST"), table.property("st").references.schemaId);
        assertNotNull(table.property("header"));
        assertNotNull(table.property("detail"));
        assertNotNull(table.property("se"));
        assertNull(table.property("isaLoop"), "envelope loops are not part of the atom");

        // DETAIL → 2000[] → 2100[] → CLP + 2110[] (DESIGN §2.3).
        final Schema detail = g835.loops.get("DETAIL");
        assertNotNull(detail);
        final Property l2000 = detail.property("loop2000");
        assertNotNull(l2000);
        assertEquals(Boolean.TRUE, l2000.multi, "2000 repeats >1");
        assertEquals(SchemaIds.type("005010X221A1", "2000"), l2000.references.schemaId);

        final Schema l2100 = g835.loops.get("2100");
        assertNotNull(l2100);
        final Property clp = l2100.property("clp");
        assertNotNull(clp);
        assertTrue(clp.required);
        assertNull(clp.multi, "CLP max_use 1");
        assertEquals(SchemaIds.type("005010X221A1", "CLP"), clp.references.schemaId);
        assertEquals(Boolean.TRUE, l2100.property("nm1").multi, "several NM1 segments share the property");
        assertEquals(Boolean.TRUE, l2100.property("loop2110").multi);
        assertEquals(Boolean.TRUE, g835.loops.get("2000").property("loop2100").multi);

        // Envelope loops are indexed but never emitted as schemas; ST_LOOP is the table.
        assertNull(g835.loops.get("ISA_LOOP"));
        assertNull(g835.loops.get("GS_LOOP"));
        assertNull(g835.loops.get("ST_LOOP"));
        assertTrue(g835.segments.containsKey("ISA"), "ISA segment schema is emitted (index references it)");
    }

    @Test
    void compositesAreSharedTypesKeyedByDataElement() {
        // SVC01 is composite C003; its sub-elements are the composite's own properties.
        final Schema svc = g835.segments.get("SVC");
        assertNotNull(svc);
        final Property svc01 = svc.property("svc01");
        assertEquals(SchemaIds.type("005010X221A1", "C003"), svc01.references.schemaId);
        final Schema c003 = g835.composites.get("C003");
        assertNotNull(c003);
        final Property c00301 = c003.property("c00301");
        assertNotNull(c00301);
        assertEquals("string", c00301.dataType);
        assertEquals(SchemaIds.codes("235"), c00301.references.schemaId, "Product/Service ID Qualifier");
        assertEquals("string", c003.property("c00302").dataType);
    }

    @Test
    void everyIdIsCanonicalAndDistinct() {
        final java.util.Set<String> ids = new java.util.HashSet<>();
        for (Schema s : g835.all()) {
            assertTrue(SchemaIds.isValid(s.id), s.id);
            assertTrue(ids.add(s.id), "duplicate id " + s.id);
            for (Property p : s.properties) {
                assertTrue(p.name.matches("[a-z][A-Za-z0-9]*"), s.id + " property not an identifier: " + p.name);
                if (p.references != null) {
                    assertTrue(SchemaIds.isValid(p.references.schemaId), p.references.schemaId);
                }
            }
        }
    }

    @Test
    void duplicateXidsMergeAcrossUses() {
        // 837P nests loop 2300 under both 2000B and 2000C; the walker must merge, not fail or duplicate.
        final MappingLoader loader = new MappingLoader();
        final GuideCatalog.Guide g = GuideCatalog.find("005010X222A1");
        final StructureWalker w = new StructureWalker(g.gs08(), g.transactionType(), null, g.mapFile(), dataElements, codes);
        w.walk(loader.loadTransaction(g.mapFile()));
        final StructureWalker.Generated gen = w.emit();
        assertEquals(SchemaIds.table("005010X222A1", "837P"), gen.table.id);
        assertNotNull(gen.loops.get("2300"));
        assertNotNull(gen.loops.get("2000B").property("loop2300"));
        assertNotNull(gen.loops.get("2000C").property("loop2300"));
        // A merged segment carries every position any use defines, in order.
        final Schema ref = gen.segments.get("REF");
        assertNotNull(ref);
        assertEquals("ref01", ref.properties.get(0).name);
        assertEquals(SchemaIds.codes("128"), ref.property("ref01").references.schemaId);
        assertEquals("ref04", ref.properties.get(3).name);
        assertEquals(SchemaIds.type("005010X222A1", "C040"), ref.property("ref04").references.schemaId);
        assertNotNull(gen.composites.get("C040"), "referenced composite is emitted even when no use lists sub-elements");
    }
}

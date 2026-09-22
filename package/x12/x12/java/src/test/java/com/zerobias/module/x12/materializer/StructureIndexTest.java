package com.zerobias.module.x12.materializer;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Loads the codegen output from the classpath (structure-index/<GS08>.json). */
class StructureIndexTest {

    @Test
    void loads835IndexFromClasspath() {
        StructureIndex idx = StructureIndex.fromClasspath("005010X221A1").orElseThrow();
        assertEquals("005010X221A1", idx.gs08);
        assertEquals("835", idx.transactionType);
        assertEquals("schema:table:x12.005010X221A1.835", idx.tableSchemaId);
        assertEquals("ST_LOOP", idx.transactionLoop);
        StructureIndex.LoopEntry st = idx.transactionEntry();
        assertNotNull(st);
        assertEquals("st", st.structures.get(0).name);
        assertEquals("header", st.structures.get(1).name);
        assertTrue(st.structures.get(2).isLoop());
        assertEquals("detail", st.structures.get(2).name);
        StructureIndex.SegmentEntry clp = idx.segment("CLP");
        assertEquals("clp04", clp.fields.get(3).name);
        assertEquals("R", clp.fields.get(3).x12Type);
        assertEquals("C003", idx.segment("SVC").fields.get(0).composite);
        assertEquals("c00302", idx.composite("C003").fields.get(1).name);
    }

    @Test
    void aliasesResolveThroughTransactionTypes() {
        assertEquals("005010X222A1", StructureIndex.fromClasspath("005010X222").orElseThrow().gs08);
        assertEquals("005010X223A2", StructureIndex.fromClasspath("005010x223a1").orElseThrow().gs08);
        assertEquals(Optional.empty(), StructureIndex.fromClasspath("005010X999"));
        assertEquals(Optional.empty(), StructureIndex.fromClasspath(null));
    }

    @Test
    void resolverCachesAndDegradesToEnvelopeSchema() {
        StructureResolver r = new StructureResolver();
        assertEquals("schema:table:x12.005010X221A1.835", r.schemaIdFor("005010X221A1"));
        assertEquals("schema:table:x12.005010X222A1.837P", r.schemaIdFor("005010X222"));
        assertEquals(StructureResolver.ENVELOPE_SCHEMA, r.schemaIdFor("005010X999"));
        assertTrue(r.resolve("005010X221A1").get() == r.resolve("005010X221").get(), "one cached instance per canonical id");
    }
}

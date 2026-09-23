package com.zerobias.module.x12.codegen;

import com.google.gson.Gson;
import com.zerobias.module.x12.codegen.mapping.MappingLoader;
import com.zerobias.module.x12.codegen.model.StructureIndex;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The materializer index (DESIGN §5): positions, names, types, implied decimals, round-trips through Gson. */
class StructureIndexTest {

    private static StructureIndex index835() {
        final MappingLoader loader = new MappingLoader();
        final GuideCatalog.Guide g = GuideCatalog.find("005010X221A1");
        final StructureWalker w = new StructureWalker(g.gs08(), g.transactionType(), null, g.mapFile(),
            loader.loadDataElements(), new CodeRegistry(loader.loadCodeSets()));
        w.walk(loader.loadTransaction(g.mapFile()));
        return w.emit().index;
    }

    @Test
    void indexDescribesTheWalk() {
        final StructureIndex idx = index835();
        assertEquals("005010X221A1", idx.gs08);
        assertEquals("835", idx.transactionType);
        assertEquals("835W1", idx.transactionXid);
        assertEquals("mapping/835.5010.X221.A1.xml", idx.mapFile);
        assertNull(idx.aliasOf);
        assertEquals("ST_LOOP", idx.transactionLoop);
        assertEquals(SchemaIds.table("005010X221A1", "835"), idx.tableSchemaId);

        // Envelope loops are present (schema-less) so the runtime can walk ISA/GS too.
        final StructureIndex.LoopEntry isa = idx.loops.get("ISA_LOOP");
        assertNotNull(isa);
        assertNull(isa.schemaId);
        assertEquals("isa", isa.structures.get(0).name);
        assertEquals("segment", isa.structures.get(0).kind);
        assertEquals("gsLoop", isa.structures.get(1).name);
        assertEquals("loop", isa.structures.get(1).kind);
        assertEquals(idx.tableSchemaId, idx.loops.get("ST_LOOP").schemaId, "the atom root resolves to the table schema");
        assertEquals(SchemaIds.type("005010X221A1", "2100"), idx.loops.get("2100").schemaId);

        final StructureIndex.LoopEntry l2100 = idx.loops.get("2100");
        final StructureIndex.StructureRef clpRef = l2100.structures.get(0);
        assertEquals("clp", clpRef.name);
        assertEquals("CLP", clpRef.xid);
        assertTrue(clpRef.required);
        assertFalse(clpRef.multi);
        assertEquals("0100", clpRef.pos);
        assertEquals("1", clpRef.repeat);
        final StructureIndex.StructureRef l2110 = l2100.structures.stream()
            .filter(s -> "2110".equals(s.xid)).findFirst().orElseThrow();
        assertEquals("loop2110", l2110.name);
        assertTrue(l2110.multi);
        assertEquals("999", l2110.repeat);

        final StructureIndex.SegmentEntry clp = idx.segments.get("CLP");
        assertNotNull(clp);
        assertEquals(SchemaIds.type("005010X221A1", "CLP"), clp.schemaId);
        assertEquals(14, clp.fields.size());
        final StructureIndex.FieldEntry clp02 = clp.fields.get(1);
        assertEquals(2, clp02.seq);
        assertEquals("clp02", clp02.name);
        assertEquals("CLP02", clp02.xid);
        assertEquals("1029", clp02.dataEle);
        assertEquals("ID", clp02.x12Type);
        assertEquals("string", clp02.coreType);
        assertEquals("1029", clp02.codes);
        assertEquals("R", clp02.usage);
        assertEquals(Integer.valueOf(1), clp02.minLen);
        assertEquals(Integer.valueOf(2), clp02.maxLen);
        final StructureIndex.FieldEntry clp03 = clp.fields.get(2);
        assertEquals("R", clp03.x12Type);
        assertEquals("decimal", clp03.coreType);
        assertNull(clp03.impliedDecimals);
        assertNull(clp03.codes);
        assertEquals("N", clp.fields.get(9).usage, "CLP10 is not used in the 835");

        // Implied decimals: GS06 Group Control Number is N0; ISA13 is N0 too.
        final StructureIndex.FieldEntry gs06 = idx.segments.get("GS").fields.get(5);
        assertEquals("28", gs06.dataEle);
        assertEquals("N0", gs06.x12Type);
        assertEquals("decimal", gs06.coreType);
        assertEquals(Integer.valueOf(0), gs06.impliedDecimals);
        // Dates and times.
        final StructureIndex.FieldEntry dtm02 = idx.segments.get("DTM").fields.get(1);
        assertEquals("DT", dtm02.x12Type);
        assertEquals("date", dtm02.coreType);
        assertEquals("TM", idx.segments.get("GS").fields.get(4).x12Type);

        // Composite slot + composite layout.
        final StructureIndex.FieldEntry svc01 = idx.segments.get("SVC").fields.get(0);
        assertEquals("C003", svc01.composite);
        assertNull(svc01.coreType);
        assertEquals("svc01", svc01.name);
        final StructureIndex.CompositeEntry c003 = idx.composites.get("C003");
        assertNotNull(c003);
        assertEquals(SchemaIds.type("005010X221A1", "C003"), c003.schemaId);
        assertEquals("c00301", c003.fields.get(0).name);
        assertEquals("SVC01-01", c003.fields.get(0).xid);
        assertEquals("235", c003.fields.get(0).dataEle);
    }

    @Test
    void roundTripsThroughGson() {
        final Gson gson = new Gson();
        final StructureIndex idx = index835();
        final String json = gson.toJson(idx);
        final StructureIndex back = gson.fromJson(json, StructureIndex.class);
        assertEquals(idx.loops.keySet(), back.loops.keySet());
        assertEquals(idx.segments.keySet(), back.segments.keySet());
        assertEquals(idx.composites.keySet(), back.composites.keySet());
        assertEquals(List.of("clp", "cas", "nm1"), List.of(
            back.loops.get("2100").structures.get(0).name,
            back.loops.get("2100").structures.get(1).name,
            back.loops.get("2100").structures.get(2).name));
        assertEquals("1029", back.segments.get("CLP").fields.get(1).codes);
        assertEquals(Integer.valueOf(0), back.segments.get("GS").fields.get(5).impliedDecimals);
        assertEquals(json, gson.toJson(back), "stable serialization");
    }
}

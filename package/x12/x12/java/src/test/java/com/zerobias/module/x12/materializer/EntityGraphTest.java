package com.zerobias.module.x12.materializer;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zerobias.module.x12.parser.Fixtures;
import com.zerobias.module.x12.parser.X12Parse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DESIGN §8.4: flattening a transaction set into an addressable object graph.
 *
 * <p>The load-bearing test is {@link #roundTripsEveryFixture()}: the graph must reassemble
 * into exactly what the materializer produced. Until that holds, storing rows instead of the
 * document would lose data, and every query built on the rows would be answering about
 * something other than what arrived.
 */
class EntityGraphTest {

    private static final Gson GSON = new Gson();

    @Test
    void roundTripsEveryFixture() throws Exception {
        for (String fixture : List.of(Fixtures.F835, Fixtures.F837P, Fixtures.F837I,
                Fixtures.F277CA, Fixtures.F999)) {
            final Materialized m = materialize(fixture);
            final List<EntityGraph.Entity> graph = EntityGraph.flatten(m.index, m.tree);
            assertFalse(graph.isEmpty(), fixture);

            final Map<String, Object> back = EntityGraph.assemble(graph);
            assertEquals(GSON.toJson(m.tree), GSON.toJson(back),
                fixture + ": the graph must reassemble into the materialized form exactly");
        }
    }

    @Test
    void everyInstanceCarriesItsRealSchemaId() throws Exception {
        final Materialized m = materialize(Fixtures.F835);
        final List<EntityGraph.Entity> graph = EntityGraph.flatten(m.index, m.tree);

        final EntityGraph.Entity root = graph.get(0);
        assertEquals("schema:table:x12.005010X221A1.835", root.schemaId, "the root is the table schema");
        assertEquals(EntityGraph.KIND_LOOP, root.kind);
        assertEquals("", root.path);
        assertEquals(null, root.parentLocalId);

        int loops = 0;
        int segments = 0;
        for (EntityGraph.Entity e : graph) {
            if (e.parentLocalId == null) {
                continue;
            }
            assertNotNull(e.schemaId, e.path + " (" + e.xid + ") has no schema id");
            assertTrue(e.schemaId.startsWith("schema:type:x12.005010X221A1.")
                || e.schemaId.startsWith("schema:table:x12.005010X221A1."), e.schemaId);
            assertTrue(e.schemaId.endsWith("." + e.xid), e.schemaId + " vs xid " + e.xid);
            if (EntityGraph.KIND_LOOP.equals(e.kind)) {
                loops++;
            } else if (EntityGraph.KIND_SEGMENT.equals(e.kind)) {
                segments++;
            }
        }
        assertTrue(loops >= 3, "835 has header/detail/footer loops: " + loops);
        assertTrue(segments >= 8, "835 has BPR/TRN/DTM/N1/CLP/... segments: " + segments);
    }

    @Test
    void moneyIsQueryableAsAnExactDecimalAndDatesAsEpochMillis() throws Exception {
        final Materialized m = materialize(Fixtures.F835);
        final List<EntityGraph.Entity> graph = EntityGraph.flatten(m.index, m.tree);

        EntityGraph.Value paid = find(graph, "BPR", "bpr02");
        assertNotNull(paid, "BPR02 total payment amount");
        assertEquals("decimal", paid.dataType());
        assertNotNull(paid.num(), "a filter must compare money as a number, never as text");
        assertEquals(0, paid.num().compareTo(new java.math.BigDecimal("450")), paid.text());

        EntityGraph.Value effective = find(graph, "BPR", "bpr16");
        assertNotNull(effective, "BPR16 payment effective date");
        assertEquals("date", effective.dataType());
        assertNotNull(effective.date(), "dates are stored as epoch-millis for range queries");
        assertEquals("2026-09-22", effective.text());

        // the qualifier that tells payer from payee is itself queryable
        EntityGraph.Value qualifier = find(graph, "N1", "n101");
        assertNotNull(qualifier);
        assertEquals("string", qualifier.dataType());
        assertNotNull(qualifier.text());
    }

    @Test
    void repeatsKeepTheirPositionAndPath() throws Exception {
        final Materialized m = materialize(Fixtures.F837P);
        final List<EntityGraph.Entity> graph = EntityGraph.flatten(m.index, m.tree);

        boolean sawIndexedPath = false;
        for (EntityGraph.Entity e : graph) {
            if (e.path.contains("[")) {
                sawIndexedPath = true;
                assertTrue(e.ordinal >= 0, e.path);
            }
        }
        assertTrue(sawIndexedPath, "a repeating structure records its position in the path");

        // and the round trip already proved ordering survives; assert paths are unique
        List<String> paths = new ArrayList<>();
        for (EntityGraph.Entity e : graph) {
            paths.add(e.path + "#" + e.xid);
        }
        assertEquals(paths.size(), paths.stream().distinct().count(), "instance paths are unique");
    }

    @Test
    void anIndexlessTransactionStillFlattensAndReassembles() throws Exception {
        final Materialized m = materialize(Fixtures.F835);
        // No index: nothing can be typed or schema-bound, but the shape must survive.
        final List<EntityGraph.Entity> graph = EntityGraph.flatten(null, m.tree);
        assertFalse(graph.isEmpty());
        assertEquals(1, graph.size(), "without structure there is only the root");
        assertEquals(GSON.toJson(new java.util.LinkedHashMap<>()),
            GSON.toJson(EntityGraph.assemble(graph)), "an empty root reassembles empty");
    }

    /**
     * The graph is the stored representation, so it is what must honour the generated schema:
     * what {@link EntityGraph#assemble} gives back has the schema's keys, array-ness and value
     * types, and every stored value is typed with its schema property's dataType.
     */
    @Test
    void everyFixtureGraphHonoursItsGeneratedSchema() throws Exception {
        for (String fixture : Fixtures.WELL_FORMED) {
            final X12Parse.ParsedFile parsed = X12Parse.parse(Fixtures.bytes(fixture), false);
            for (X12Parse.Transaction tx : parsed.transactions()) {
                final Materializer m = new StructureResolver().materializerFor(tx.gs08(), parsed.separators()).orElseThrow();
                final List<EntityGraph.Entity> graph = EntityGraph.flatten(m.index(), m.materializeTransaction(tx.loop()));
                final JsonObject back = JsonParser.parseString(GSON.toJson(EntityGraph.assemble(graph))).getAsJsonObject();
                MaterializerTest.assertConforms(back, m.index().tableSchemaId, fixture);
                for (EntityGraph.Entity e : graph) {
                    final Map<String, String> types = propertyTypes(e.schemaId);
                    for (EntityGraph.Value v : e.values) {
                        final String property = v.property().replaceAll("\\[\\d+]$", "");
                        assertEquals(types.get(property), v.dataType(),
                            fixture + " " + e.path + "." + v.property() + " typed unlike " + e.schemaId);
                    }
                }
            }
        }
    }

    @Test
    void controlNumbersAreStoredAsTextWithTheirLeadingZeros() throws Exception {
        final String t = Fixtures.text(Fixtures.F999).replace("AK1*HC*102*", "AK1*HC*000102*");
        final Materialized m = materialize(t.getBytes(StandardCharsets.UTF_8));
        final EntityGraph.Value ak102 = find(EntityGraph.flatten(m.index, m.tree), "AK1", "ak102");
        assertEquals("string", ak102.dataType(), "a 999 joins AK102 to the acknowledged GS06 by text");
        assertEquals("000102", ak102.text());
        assertNull(ak102.num());
    }

    @Test
    void aSecondSingleUseSegmentKeepsTheSchemaShapeThroughTheGraph() throws Exception {
        final String trn = "TRN*1*EFT000000101*1000000000~\n";
        final String t = Fixtures.text(Fixtures.F835).replace(trn, trn + trn.replace("101*", "102*"))
            .replace("SE*42*0001~", "SE*43*0001~");
        final Materialized m = materialize(t.getBytes(StandardCharsets.UTF_8));
        final List<EntityGraph.Entity> graph = EntityGraph.flatten(m.index, m.tree);
        final JsonObject back = JsonParser.parseString(GSON.toJson(EntityGraph.assemble(graph))).getAsJsonObject();
        final JsonElement stored = back.getAsJsonObject("header").get("trn");
        assertTrue(stored.isJsonObject(), "TRN is single-use in the schema: " + stored);
        assertEquals("EFT000000101", stored.getAsJsonObject().get("trn02").getAsString(), "the first occurrence");
        MaterializerTest.assertConforms(back, m.index.tableSchemaId, "835 with two TRN");
    }

    @Test
    void anUnknownSegmentIsLeftToTheRawAndNeverStoredWithoutASchemaId() throws Exception {
        final String t = Fixtures.text(Fixtures.F835).replace("PLB*", "ZZZ*1*2~\nPLB*").replace("SE*42*0001~", "SE*43*0001~");
        final Materialized m = materialize(t.getBytes(StandardCharsets.UTF_8));
        final List<EntityGraph.Entity> graph = EntityGraph.flatten(m.index, m.tree);
        for (EntityGraph.Entity e : graph) {
            assertNotNull(e.schemaId, e.path + ": entities.schema_id is NOT NULL, one null fails the whole file");
            assertFalse("ZZZ".equals(e.xid), e.path);
        }
        final Map<String, Object> back = EntityGraph.assemble(graph);
        MaterializerTest.assertConforms(JsonParser.parseString(GSON.toJson(back)).getAsJsonObject(),
            m.index.tableSchemaId, "835 with ZZZ");
    }

    // --- helpers ------------------------------------------------------------

    /** Property name → dataType of a generated schema. */
    private static Map<String, String> propertyTypes(String schemaId) {
        final Map<String, String> out = new HashMap<>();
        for (JsonElement p : MaterializerTest.schema(schemaId).getAsJsonArray("properties")) {
            out.put(p.getAsJsonObject().get("name").getAsString(), p.getAsJsonObject().get("dataType").getAsString());
        }
        return out;
    }

    private record Materialized(StructureIndex index, Map<String, Object> tree) {
    }

    private static Materialized materialize(String fixture) throws Exception {
        return materialize(Fixtures.bytes(fixture));
    }

    private static Materialized materialize(byte[] bytes) throws Exception {
        final X12Parse.ParsedFile parsed = X12Parse.parse(bytes, true);
        assertFalse(parsed.transactions().isEmpty(), "parsed no transaction sets");
        final Materializer m = new StructureResolver()
            .materializerFor(parsed.gs08(), parsed.separators()).orElseThrow();
        return new Materialized(m.index(), m.materializeTransaction(parsed.transactions().get(0).loop()));
    }

    private static EntityGraph.Value find(List<EntityGraph.Entity> graph, String xid, String property) {
        for (EntityGraph.Entity e : graph) {
            if (xid.equals(e.xid)) {
                for (EntityGraph.Value v : e.values) {
                    if (property.equals(v.property())) {
                        return v;
                    }
                }
            }
        }
        return null;
    }
}

package com.zerobias.module.x12.materializer;

import com.google.gson.Gson;
import com.zerobias.module.x12.parser.Fixtures;
import com.zerobias.module.x12.parser.X12Parse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    // --- helpers ------------------------------------------------------------

    private record Materialized(StructureIndex index, Map<String, Object> tree) {
    }

    private static Materialized materialize(String fixture) throws Exception {
        final X12Parse.ParsedFile parsed = X12Parse.parse(Fixtures.bytes(fixture), true);
        assertFalse(parsed.transactions().isEmpty(), fixture + " parsed no transaction sets");
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

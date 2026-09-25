package com.zerobias.module.x12.producer;

import com.zerobias.module.x12.materializer.EntityGraph;
import com.zerobias.module.x12.materializer.Materializer;
import com.zerobias.module.x12.materializer.StructureResolver;
import com.zerobias.module.x12.parser.Fixtures;
import com.zerobias.module.x12.parser.X12Parse;
import com.zerobias.module.x12.producer.mapping.EntityMapping;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DESIGN §8.5: the bundled 835 mapping projected over a real graph. This is the layer that
 * turns {@code loop2100.clp.clp04} into {@code Claim.paidAmount} — and the grain assertions
 * are the point: a remittance is one row, a claim is one row per CLP loop, a service line one
 * per SVC loop.
 */
class EntityMappingTest {

    private static final String GUIDE = "005010X221A1";

    @Test
    void theBundledMappingDeclaresTheGrainForEachEntity() {
        List<EntityMapping> mappings = EntityMapping.forGuide(GUIDE);
        assertEquals(List.of("Remittance", "Claim", "ServiceLine"),
            mappings.stream().map(EntityMapping::name).toList());

        EntityMapping claim = byName(mappings, "Claim");
        assertEquals("claims", claim.collection());
        assertEquals("schema:type:x12.005010X221A1.2100", claim.anchorSchemaId(),
            "grain = one row per CLP loop instance");
        assertEquals("schema:business:x12.835.Claim", claim.schemaId());
        assertEquals("claimId", claim.primaryKey().name());

        assertEquals("schema:table:x12.005010X221A1.835", byName(mappings, "Remittance").anchorSchemaId(),
            "grain = one row per transaction set");
        assertEquals("schema:type:x12.005010X221A1.2110", byName(mappings, "ServiceLine").anchorSchemaId(),
            "grain = one row per SVC loop instance");

        // dimensions are transaction-level and shared by every entity of the guide
        assertEquals(List.of("payerName", "payerId", "payeeName", "payeeNpi", "checkOrEftNumber",
            "paymentEffectiveDate"), claim.dimensions().stream().map(EntityMapping.Dimension::name).toList());
    }

    @Test
    void claimsProjectWithNamedTypedColumns() throws Exception {
        List<EntityGraph.Entity> graph = graph(Fixtures.F835);
        EntityMapping claim = byName(EntityMapping.forGuide(GUIDE), "Claim");

        List<Map<String, Object>> rows = project(graph, claim);
        assertEquals(2, rows.size(), "the fixture carries two claims — one row each");

        Map<String, Object> first = rows.get(0);
        assertEquals("CLM0001", first.get("claimId"));
        assertEquals("1", first.get("claimStatus"));
        assertEquals(0, ((BigDecimal) first.get("chargedAmount")).compareTo(new BigDecimal("300.00")));
        assertEquals(0, ((BigDecimal) first.get("paidAmount")).compareTo(new BigDecimal("220.00")));
        assertEquals(0, ((BigDecimal) first.get("patientResponsibility")).compareTo(new BigDecimal("40.00")));
        assertEquals("EHP2026000001", first.get("payerClaimControlNumber"));
        assertEquals(0, ((BigDecimal) first.get("allowedAmount")).compareTo(new BigDecimal("260.00")),
            "AMT*AU resolved by qualifier");
        assertEquals("2026-09-01", first.get("statementFromDate"));

        // the qualifier predicates are what separate patient from provider
        assertEquals("DOE", first.get("patientLastName"));
        assertEquals("JANE", first.get("patientFirstName"));
        assertEquals("EHP000000001", first.get("patientMemberId"));
        assertEquals("SMITH", first.get("renderingProviderLastName"));
        assertEquals("1234567893", first.get("renderingProviderNpi"));

        assertEquals("CLM0002", rows.get(1).get("claimId"));
        assertEquals(0, ((BigDecimal) rows.get(1).get("paidAmount")).compareTo(new BigDecimal("240.00")));
    }

    @Test
    void serviceLinesReachIntoTheCompositeForTheProcedure() throws Exception {
        List<EntityGraph.Entity> graph = graph(Fixtures.F835);
        EntityMapping line = byName(EntityMapping.forGuide(GUIDE), "ServiceLine");

        List<Map<String, Object>> rows = project(graph, line);
        assertEquals(3, rows.size(), "two lines on the first claim, one on the second");

        assertEquals("99213", rows.get(0).get("procedureCode"), "SVC01-2 inside composite C003");
        assertEquals("HC", rows.get(0).get("procedureQualifier"));
        assertEquals(0, ((BigDecimal) rows.get(0).get("chargedAmount")).compareTo(new BigDecimal("200.00")));
        assertEquals(0, ((BigDecimal) rows.get(0).get("paidAmount")).compareTo(new BigDecimal("160.00")));
        assertEquals("2026-09-01", rows.get(0).get("serviceDate"));
        assertEquals(0, ((BigDecimal) rows.get(0).get("allowedAmount")).compareTo(new BigDecimal("180.00")));
        assertEquals("36415", rows.get(1).get("procedureCode"));
        assertEquals("99214", rows.get(2).get("procedureCode"));
    }

    @Test
    void theRemittanceIsOneRowCarryingThePaymentAndTheParties() throws Exception {
        List<EntityGraph.Entity> graph = graph(Fixtures.F835);
        EntityMapping remit = byName(EntityMapping.forGuide(GUIDE), "Remittance");

        List<Map<String, Object>> rows = project(graph, remit);
        assertEquals(1, rows.size());
        Map<String, Object> r = rows.get(0);
        assertEquals("0001", r.get("transactionControlNumber"));
        assertEquals(0, ((BigDecimal) r.get("paymentAmount")).compareTo(new BigDecimal("450.00")));
        assertEquals("ACH", r.get("paymentMethod"));
        assertEquals("2026-09-22", r.get("effectiveDate"));
        assertEquals("EFT000000101", r.get("checkOrEftNumber"));
        assertEquals("2026-09-22", r.get("productionDate"), "DTM*405 by qualifier");
        assertEquals("EXAMPLE HEALTH PLAN", r.get("payerName"), "N1*PR");
        assertEquals("EXAMPLE MEDICAL GROUP", r.get("payeeName"), "N1*PE");
        assertEquals("1234567893", r.get("payeeNpi"));
    }

    @Test
    void dimensionsResolveOncePerTransactionForSegmentation() throws Exception {
        List<EntityGraph.Entity> graph = graph(Fixtures.F835);
        EntityMapping claim = byName(EntityMapping.forGuide(GUIDE), "Claim");
        EntityGraph.Entity root = graph.get(0);

        Map<String, String> dims = new java.util.LinkedHashMap<>();
        for (EntityMapping.Dimension d : claim.dimensions()) {
            EntityGraph.Value v = EntityMapping.read(graph, root, d.path());
            dims.put(d.name(), v == null ? null : v.text());
        }
        assertEquals("EXAMPLE HEALTH PLAN", dims.get("payerName"), "'claims for this payer' segments on this");
        assertEquals("EXAMPLE MEDICAL GROUP", dims.get("payeeName"));
        assertEquals("1234567893", dims.get("payeeNpi"));
        assertEquals("EFT000000101", dims.get("checkOrEftNumber"));
        assertEquals("2026-09-22", dims.get("paymentEffectiveDate"));
    }

    @Test
    void aDecimalIsProjectedFromItsLexicalValueNeverTheComparisonKey() {
        // A value whose comparison key has lost its scale (what value_num / 1e6, or any float
        // path, yields) must still project with the scale the wire carried.
        EntityGraph.Entity anchor = EntityGraph.Entity.of(0, null, "schema:type:x", "2100", "loop", null, "", 0);
        EntityGraph.Entity clp = EntityGraph.Entity.of(1, 0, "schema:type:x.CLP", "CLP", "segment", "clp", "clp", 0);
        clp.values.add(new EntityGraph.Value("clp03", "decimal", "300.00", new BigDecimal("300"), null));
        clp.values.add(new EntityGraph.Value("clp04", "decimal", "220.5", new BigDecimal("220.50000"), null));
        clp.values.add(new EntityGraph.Value("clp05", "decimal", "not-a-number", new BigDecimal("40.00"), null));
        clp.values.add(new EntityGraph.Value("clp09", "integer", "007", new BigDecimal("7.000"), null));
        List<EntityGraph.Entity> graph = List.of(anchor, clp);

        EntityMapping m = EntityMapping.parse("X", """
            {"entities":[{"name":"C","anchorSchemaId":"schema:type:x","columns":[
              {"name":"charged","path":"clp.clp03","dataType":"decimal"},
              {"name":"paid","path":"clp.clp04","dataType":"decimal"},
              {"name":"resp","path":"clp.clp05","dataType":"decimal"},
              {"name":"freq","path":"clp.clp09","dataType":"integer"}]}]}""").get(0);
        Map<String, Object> row = m.project(graph, anchor);

        assertEquals("300.00", ((BigDecimal) row.get("charged")).toPlainString(), "scale from value_text");
        assertEquals("220.5", ((BigDecimal) row.get("paid")).toPlainString());
        assertEquals("40.00", ((BigDecimal) row.get("resp")).toPlainString(),
            "unparseable text falls back to the comparison key rather than failing the row");
        assertEquals(7L, row.get("freq"));
        assertEquals("{\"charged\":300.00}", new com.google.gson.Gson().toJson(Map.of("charged", row.get("charged"))),
            "Gson writes a BigDecimal's own scale");
    }

    @Test
    void theFixtureAmountsKeepTheirWireScale() throws Exception {
        Map<String, Object> first = project(graph(Fixtures.F835), byName(EntityMapping.forGuide(GUIDE), "Claim")).get(0);
        assertEquals("300.00", ((BigDecimal) first.get("chargedAmount")).toPlainString());
        assertEquals("220.00", ((BigDecimal) first.get("paidAmount")).toPlainString());
        assertEquals("40.00", ((BigDecimal) first.get("patientResponsibility")).toPlainString());
    }

    @Test
    void anAbsentOptionalColumnIsPresentAndNull() throws Exception {
        List<EntityGraph.Entity> graph = graph(Fixtures.F835);
        EntityMapping line = byName(EntityMapping.forGuide(GUIDE), "ServiceLine");
        Map<String, Object> row = project(graph, line).get(0);

        assertTrue(row.containsKey("revenueCode"), "every row has the same shape");
        assertNull(row.get("revenueCode"), "SVC04 is absent on a professional line");
        assertEquals(line.columns().size(), row.size());
    }

    @Test
    void aGuideWithNoMappingYieldsNoBusinessEntities() {
        assertEquals(List.of(), EntityMapping.forGuide("005010X231A1"), "999 has no mapping bundled yet");
        assertEquals(List.of(), EntityMapping.forGuide(null));
        assertEquals(List.of(), EntityMapping.parse(GUIDE, "not json"));
        assertEquals(List.of(), EntityMapping.parse(GUIDE, "{}"));
    }

    // --- helpers ------------------------------------------------------------

    private static List<EntityGraph.Entity> graph(String fixture) throws Exception {
        X12Parse.ParsedFile p = X12Parse.parse(Fixtures.bytes(fixture), true);
        Materializer m = new StructureResolver().materializerFor(p.gs08(), p.separators()).orElseThrow();
        List<EntityGraph.Entity> graph = EntityGraph.flatten(m.index(),
            m.materializeTransaction(p.transactions().get(0).loop()));
        assertFalse(graph.isEmpty());
        return graph;
    }

    /** Every anchor instance of the mapping, projected — what a collection page will hold. */
    private static List<Map<String, Object>> project(List<EntityGraph.Entity> graph, EntityMapping mapping) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EntityGraph.Entity e : graph) {
            if (mapping.anchorSchemaId().equals(e.schemaId)) {
                rows.add(mapping.project(graph, e));
            }
        }
        return rows;
    }

    private static EntityMapping byName(List<EntityMapping> mappings, String name) {
        for (EntityMapping m : mappings) {
            if (m.name().equals(name)) {
                return m;
            }
        }
        throw new AssertionError("no mapping named " + name);
    }
}

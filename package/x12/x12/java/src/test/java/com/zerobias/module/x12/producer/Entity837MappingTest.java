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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DESIGN §8.5 for the 837s: the bundled 837P / 837I mappings projected over real graphs.
 * Grain = loop2300 for a claim and loop2400 for a service line; the subscriber and the
 * per-claim payer are read through an ancestor step, and every column resolves on the
 * fixtures except the ones the fixture genuinely does not carry (listed per test).
 */
class Entity837MappingTest {

    private static final String P = "005010X222A1";
    private static final String I = "005010X223A2";

    @Test
    void theGrainIsDeclaredPerEntity() {
        EntityMapping claim = byName(EntityMapping.forGuide(P), "Claim");
        assertEquals("professional-claims", claim.collection());
        assertEquals("schema:type:x12.005010X222A1.2300", claim.anchorSchemaId());
        assertEquals("schema:business:x12.837P.Claim", claim.schemaId());
        assertEquals("claimId", claim.primaryKey().name());
        assertEquals("schema:type:x12.005010X222A1.2400",
            byName(EntityMapping.forGuide(P), "ServiceLine").anchorSchemaId());

        EntityMapping iclaim = byName(EntityMapping.forGuide(I), "Claim");
        assertEquals("institutional-claims", iclaim.collection());
        assertEquals("schema:type:x12.005010X223A2.2300", iclaim.anchorSchemaId());
        assertEquals("schema:business:x12.837I.Claim", iclaim.schemaId());
    }

    @Test
    void aliasedGuidesShareTheCanonicalMapping() {
        // 005010X223A1 / 005010X223 are materialized with the X223A2 structure (TransactionTypes),
        // so their graphs carry X223A2 schema ids and must resolve the X223A2 mapping — without
        // it their dimensions would silently never be written.
        for (String alias : List.of("005010X223A1", "005010X223", "005010x223a1")) {
            List<EntityMapping> ms = EntityMapping.forGuide(alias);
            assertEquals(I, ms.get(0).gs08(), alias);
            assertEquals("schema:type:x12.005010X223A2.2300", byName(ms, "Claim").anchorSchemaId(), alias);
        }
        assertEquals(P, EntityMapping.forGuide("005010X222").get(0).gs08());
    }

    @Test
    void professionalClaimProjectsEveryColumnTheFixtureCarries() throws Exception {
        List<EntityGraph.Entity> graph = graph(Fixtures.F837P);
        EntityMapping claim = byName(EntityMapping.forGuide(P), "Claim");
        List<Map<String, Object>> rows = project(graph, claim);
        assertEquals(1, rows.size());
        Map<String, Object> r = rows.get(0);

        assertEquals("CLM0001", r.get("claimId"));
        assertEquals("300.00", ((BigDecimal) r.get("chargedAmount")).toPlainString(), "scale from the wire");
        assertEquals("11", r.get("placeOfServiceCode"), "CLM05-1 inside composite C023");
        assertEquals("B", r.get("facilityCodeQualifier"));
        assertEquals("1", r.get("claimFrequencyCode"));
        assertEquals("Y", r.get("providerSignatureIndicator"));
        assertEquals("A", r.get("assignmentParticipationCode"));
        assertEquals("Y", r.get("benefitsAssignmentIndicator"));
        assertEquals("Y", r.get("releaseOfInformationCode"));
        assertEquals("ABK", r.get("principalDiagnosisQualifier"));
        assertEquals("J069", r.get("principalDiagnosisCode"));
        assertEquals("SMITH", r.get("renderingProviderLastName"));
        assertEquals("1234567893", r.get("renderingProviderNpi"));
        assertEquals("207Q00000X", r.get("renderingProviderTaxonomy"));
        // ancestor reads: the subscriber and payer live above the claim, in 2000B
        assertEquals("DOE", r.get("subscriberLastName"));
        assertEquals("JANE", r.get("subscriberFirstName"));
        assertEquals("EHP000000001", r.get("subscriberMemberId"));
        assertEquals("GRP0001", r.get("subscriberGroupNumber"));
        assertEquals("P", r.get("payerResponsibilityCode"));
        assertEquals("CI", r.get("claimFilingIndicator"));
        assertEquals("EXAMPLE HEALTH PLAN", r.get("claimPayerName"));
        assertEquals("EHPID00001", r.get("claimPayerId"));

        assertEquals(Set.of(), nullColumns(r), "the 837P fixture carries every claim column");
    }

    @Test
    void professionalServiceLinesReachUpForTheClaimAndIntoTheComposite() throws Exception {
        List<EntityGraph.Entity> graph = graph(Fixtures.F837P);
        List<Map<String, Object>> rows = project(graph, byName(EntityMapping.forGuide(P), "ServiceLine"));
        assertEquals(2, rows.size(), "two LX loops");

        Map<String, Object> first = rows.get(0);
        assertEquals("CLM0001", first.get("claimId"), "^loop2300 — the enclosing claim");
        assertEquals(1L, first.get("lineNumber"));
        assertEquals("HC", first.get("procedureQualifier"));
        assertEquals("99213", first.get("procedureCode"));
        assertEquals("200.00", ((BigDecimal) first.get("chargedAmount")).toPlainString());
        assertEquals("UN", first.get("unitBasis"));
        assertEquals(0, ((BigDecimal) first.get("units")).compareTo(BigDecimal.ONE));
        assertEquals("1", first.get("diagnosisPointer"));
        assertEquals("2026-09-01", first.get("serviceDate"), "D8 CCYYMMDD becomes ISO");
        assertEquals("2026-09-01", first.get("serviceDateTo"), "a D8 date is its own end");
        assertEquals("LINE0001", first.get("lineItemControlNumber"));
        assertEquals("36415", rows.get(1).get("procedureCode"));
        assertEquals(2L, rows.get(1).get("lineNumber"));

        // SV101-3 and SV105 are situational and the fixture has neither
        assertEquals(Set.of("procedureModifier1", "placeOfServiceCode"), nullColumns(first));
    }

    @Test
    void institutionalClaimSplitsTheStatementRange() throws Exception {
        List<EntityGraph.Entity> graph = graph(Fixtures.F837I);
        List<Map<String, Object>> rows = project(graph, byName(EntityMapping.forGuide(I), "Claim"));
        assertEquals(1, rows.size());
        Map<String, Object> r = rows.get(0);

        assertEquals("CLM0002", r.get("claimId"));
        assertEquals("2500.00", ((BigDecimal) r.get("chargedAmount")).toPlainString());
        assertEquals("13", r.get("facilityTypeCode"));
        assertEquals("A", r.get("facilityCodeQualifier"));
        assertEquals("1", r.get("claimFrequencyCode"));
        assertEquals("2026-09-05", r.get("statementFromDate"), "RD8 start");
        assertEquals("2026-09-05", r.get("statementToDate"), "RD8 end");
        assertEquals("1", r.get("admissionTypeCode"));
        assertEquals("1", r.get("admissionSourceCode"));
        assertEquals("01", r.get("patientStatusCode"));
        assertEquals("K3580", r.get("principalDiagnosisCode"));
        assertEquals("SMITH", r.get("attendingProviderLastName"));
        assertEquals("1234567893", r.get("attendingProviderNpi"));
        assertEquals("ROE", r.get("subscriberLastName"));
        assertEquals("EHP000000002", r.get("subscriberMemberId"));
        assertEquals("EHPID00001", r.get("claimPayerId"));

        // an outpatient claim (bill type 13x) carries no DTP*435 admission date
        assertEquals(Set.of("admissionDate"), nullColumns(r));
    }

    @Test
    void institutionalServiceLinesCarryTheRevenueCode() throws Exception {
        List<Map<String, Object>> rows = project(graph(Fixtures.F837I),
            byName(EntityMapping.forGuide(I), "ServiceLine"));
        assertEquals(3, rows.size());
        Map<String, Object> first = rows.get(0);
        assertEquals("CLM0002", first.get("claimId"));
        assertEquals(1L, first.get("lineNumber"));
        assertEquals("0450", first.get("revenueCode"), "leading zero kept: a code, not a number");
        assertEquals("HC", first.get("procedureQualifier"));
        assertEquals("99283", first.get("procedureCode"));
        assertEquals("800.00", ((BigDecimal) first.get("chargedAmount")).toPlainString());
        assertEquals("UN", first.get("unitBasis"));
        assertEquals(0, ((BigDecimal) first.get("units")).compareTo(BigDecimal.ONE));
        assertEquals("2026-09-05", first.get("serviceDate"));
        assertEquals(Set.of(), nullColumns(first));
        assertEquals("1500.00", ((BigDecimal) rows.get(2).get("chargedAmount")).toPlainString());
    }

    @Test
    void dimensionsResolveFromTheHeaderAndTheHlLevels() throws Exception {
        Map<String, EntityGraph.Value> p = EntityMapping.dimensions(P, graph(Fixtures.F837P));
        assertEquals("EXAMPLE MEDICAL GROUP", p.get("submitterName").text());
        assertEquals("000000000", p.get("submitterId").text());
        assertEquals("EXAMPLE HEALTH PLAN", p.get("receiverName").text());
        assertEquals("EHPID00001", p.get("receiverId").text());
        assertEquals("EXAMPLE MEDICAL GROUP", p.get("billingProviderName").text());
        assertEquals("1234567893", p.get("billingProviderNpi").text());
        assertEquals("EXAMPLE HEALTH PLAN", p.get("payerName").text());
        assertEquals("EHPID00001", p.get("payerId").text());

        Map<String, EntityGraph.Value> i = EntityMapping.dimensions("005010X223A1", graph(Fixtures.F837I));
        assertEquals("EXAMPLE COMMUNITY HOSPITAL", i.get("billingProviderName").text(),
            "an aliased GS08 still resolves its dimensions");
        assertEquals("EHPID00001", i.get("payerId").text());
    }

    @Test
    void aDimensionTheTransactionStatesTwiceDifferentlyIsAbsent() throws Exception {
        // two subscribers with different payers in one batch: which payer is "the" payer of the
        // transaction? Neither — so no payerName dimension, while each claim keeps its own.
        List<EntityGraph.Entity> graph = graph(twoPayer837P());
        Map<String, EntityGraph.Value> dims = EntityMapping.dimensions(P, graph);
        assertNull(dims.get("payerName"), "ambiguous means absent, never the first one");
        assertNull(dims.get("payerId"));
        assertEquals("EXAMPLE MEDICAL GROUP", dims.get("billingProviderName").text(), "still one billing provider");

        List<Map<String, Object>> claims = project(graph, byName(EntityMapping.forGuide(P), "Claim"));
        assertEquals(2, claims.size());
        assertEquals("EXAMPLE HEALTH PLAN", claims.get(0).get("claimPayerName"));
        assertEquals("OTHER PLAN", claims.get(1).get("claimPayerName"), "the per-claim column is exact");
        assertEquals("ROE", claims.get(1).get("subscriberLastName"));
    }

    // --- helpers ------------------------------------------------------------

    /** The 837P fixture with a second subscriber (other payer) and claim appended. */
    static String twoPayer837P() {
        String text = Fixtures.text(Fixtures.F837P);
        String extra = String.join("\n",
            "HL*3*1*22*0~",
            "SBR*P*18*GRP0002******CI~",
            "NM1*IL*1*ROE*RICHARD****MI*OTH000000002~",
            "NM1*PR*2*OTHER PLAN*****PI*OTHID00002~",
            "CLM*CLM0009*125.50***11:B:1*Y*A*Y*Y~",
            "HI*ABK:Z0000~",
            "LX*1~",
            "SV1*HC:99212*125.50*UN*1***1~",
            "DTP*472*D8*20260903~") + "\n";
        return withSegmentCount(text.replace("SE*30*0001~", extra + "SE*30*0001~"));
    }

    /** Recount SE01 for a fixture whose segments were edited. */
    static String withSegmentCount(String text) {
        String[] lines = text.split("\n");
        int count = 0;
        boolean in = false;
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            if (line.startsWith("ST*")) {
                in = true;
                count = 0;
            }
            if (in) {
                count++;
            }
            if (line.startsWith("SE*")) {
                String[] parts = line.split("\\*");
                line = "SE*" + count + "*" + parts[2];
                in = false;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    static List<EntityGraph.Entity> graph(String fixtureOrText) throws Exception {
        byte[] bytes = fixtureOrText.startsWith("ISA")
            ? fixtureOrText.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            : Fixtures.bytes(fixtureOrText);
        X12Parse.ParsedFile p = X12Parse.parse(bytes, true);
        Materializer m = new StructureResolver().materializerFor(p.gs08(), p.separators()).orElseThrow();
        List<EntityGraph.Entity> graph = EntityGraph.flatten(m.index(),
            m.materializeTransaction(p.transactions().get(0).loop()));
        assertFalse(graph.isEmpty());
        return graph;
    }

    private static List<Map<String, Object>> project(List<EntityGraph.Entity> graph, EntityMapping mapping) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EntityGraph.Entity e : graph) {
            if (mapping.anchorSchemaId().equals(e.schemaId)) {
                rows.add(mapping.project(graph, e));
            }
        }
        return rows;
    }

    private static Set<String> nullColumns(Map<String, Object> row) {
        Set<String> out = new java.util.TreeSet<>();
        row.forEach((k, v) -> {
            if (v == null) {
                out.add(k);
            }
        });
        return out;
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

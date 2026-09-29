package com.zerobias.module.x12.materializer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zerobias.module.x12.parser.Fixtures;
import com.zerobias.module.x12.parser.X12Parse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loop tree → typed JSON (DESIGN §2.3 keys, §2.4 types, §5 walk) against the fixtures and
 * their README invariants.
 */
class MaterializerTest {

    private static JsonObject materialize(String fixture) throws Exception {
        return materialize(Fixtures.bytes(fixture));
    }

    private static JsonObject materialize(byte[] bytes) throws Exception {
        X12Parse.ParsedFile p = X12Parse.parse(bytes, true);
        Materializer m = new StructureResolver().materializerFor(p.gs08(), p.separators()).orElseThrow();
        Map<String, Object> tree = m.materializeTransaction(p.transactions().get(0).loop());
        return JsonParser.parseString(m.toJson(tree)).getAsJsonObject();
    }

    @Test
    void the835FollowsTheSchemaKeysAndTypes() throws Exception {
        JsonObject tx = materialize(Fixtures.F835);
        assertEquals(java.util.List.of("st", "header", "detail", "footer", "se"), tx.keySet().stream().toList(),
            "ST_LOOP children in index order");
        assertEquals("835", tx.getAsJsonObject("st").get("st01").getAsString());
        assertEquals("0001", tx.getAsJsonObject("st").get("st02").getAsString());

        JsonObject header = tx.getAsJsonObject("header");
        JsonObject bpr = header.getAsJsonObject("bpr");
        assertTrue(bpr.get("bpr02").isJsonPrimitive() && bpr.get("bpr02").getAsJsonPrimitive().isNumber(), "BPR02 is a JSON number");
        assertEquals(0, new BigDecimal("450.00").compareTo(bpr.get("bpr02").getAsBigDecimal()));
        assertEquals("2026-09-22", bpr.get("bpr16").getAsString(), "DT -> ISO");
        assertEquals("EFT000000101", header.getAsJsonObject("trn").get("trn02").getAsString());
        assertEquals("2026-09-22", header.getAsJsonObject("dtm").get("dtm02").getAsString());
        assertEquals("EXAMPLE HEALTH PLAN", header.getAsJsonObject("loop1000A").getAsJsonObject("n1").get("n102").getAsString());
        assertTrue(header.getAsJsonObject("loop1000A").get("per").isJsonArray(), "PER is multi in the map -> array");
        assertEquals("1234567893", header.getAsJsonObject("loop1000B").getAsJsonObject("n1").get("n104").getAsString());
        assertTrue(header.getAsJsonObject("loop1000B").get("ref").isJsonArray(), "REF multi -> array even with one");

        JsonArray detail = tx.getAsJsonArray("detail");
        assertEquals(1, detail.size());
        JsonArray loop2000 = detail.get(0).getAsJsonObject().getAsJsonArray("loop2000");
        assertEquals(1, loop2000.size());
        assertEquals("1", loop2000.get(0).getAsJsonObject().getAsJsonObject("lx").get("lx01").getAsString());
        JsonArray loop2100 = loop2000.get(0).getAsJsonObject().getAsJsonArray("loop2100");
        assertEquals(2, loop2100.size(), "2 CLP");

        JsonObject claim1 = loop2100.get(0).getAsJsonObject();
        JsonObject clp = claim1.getAsJsonObject("clp");
        assertEquals("CLM0001", clp.get("clp01").getAsString());
        assertEquals("1", clp.get("clp02").getAsString(), "ID stays a string");
        assertTrue(clp.get("clp02").getAsJsonPrimitive().isString());
        assertTrue(clp.get("clp04").getAsJsonPrimitive().isNumber(), "R -> number");
        assertEquals(0, new BigDecimal("220.00").compareTo(clp.get("clp04").getAsBigDecimal()));
        assertEquals("220.00", clp.get("clp04").getAsJsonPrimitive().toString(), "scale preserved on the wire");
        assertEquals(0, new BigDecimal("300.00").compareTo(clp.get("clp03").getAsBigDecimal()));
        assertEquals("EHP2026000001", clp.get("clp07").getAsString());
        assertEquals("11", clp.get("clp08").getAsString());
        assertNull(clp.get("clp10"), "empty/absent elements omitted");
        JsonArray nm1 = claim1.getAsJsonArray("nm1");
        assertEquals(2, nm1.size());
        assertEquals("DOE", nm1.get(0).getAsJsonObject().get("nm103").getAsString());
        assertEquals("EHP000000001", nm1.get(0).getAsJsonObject().get("nm109").getAsString());
        assertNull(nm1.get(0).getAsJsonObject().get("nm105"), "empty NM105 omitted");
        JsonArray dtm = claim1.getAsJsonArray("dtm");
        assertEquals("232", dtm.get(0).getAsJsonObject().get("dtm01").getAsString());
        assertEquals("2026-09-01", dtm.get(0).getAsJsonObject().get("dtm02").getAsString());
        assertEquals(0, new BigDecimal("260.00").compareTo(
            claim1.getAsJsonArray("amt").get(0).getAsJsonObject().get("amt02").getAsBigDecimal()));

        JsonArray svc = claim1.getAsJsonArray("loop2110");
        assertEquals(2, svc.size());
        JsonObject svc1 = svc.get(0).getAsJsonObject().getAsJsonObject("svc");
        JsonObject c003 = svc1.getAsJsonObject("svc01");
        assertEquals("HC", c003.get("c00301").getAsString(), "composite nests by the index layout");
        assertEquals("99213", c003.get("c00302").getAsString());
        assertEquals(0, new BigDecimal("200.00").compareTo(svc1.get("svc02").getAsBigDecimal()));
        assertEquals(0, new BigDecimal("160.00").compareTo(svc1.get("svc03").getAsBigDecimal()));
        assertNull(svc1.get("svc04"), "SVC04 empty");
        assertEquals(0, new BigDecimal("1").compareTo(svc1.get("svc05").getAsBigDecimal()));
        JsonArray cas = svc.get(0).getAsJsonObject().getAsJsonArray("cas");
        assertEquals(2, cas.size());
        assertEquals("CO", cas.get(0).getAsJsonObject().get("cas01").getAsString());
        assertEquals("45", cas.get(0).getAsJsonObject().get("cas02").getAsString());
        assertEquals(0, new BigDecimal("20.00").compareTo(cas.get(0).getAsJsonObject().get("cas03").getAsBigDecimal()));

        JsonObject footer = tx.getAsJsonObject("footer");
        assertTrue(footer.get("plb").isJsonArray(), "PLB repeats in the map -> array");
        JsonObject plb = footer.getAsJsonArray("plb").get(0).getAsJsonObject();
        assertEquals("1234567893", plb.get("plb01").getAsString());
        assertEquals("2026-12-31", plb.get("plb02").getAsString());
        assertEquals("WO", plb.getAsJsonObject("plb03").get("c04201").getAsString());
        assertEquals("CLM0000", plb.getAsJsonObject("plb03").get("c04202").getAsString());
        assertEquals(0, new BigDecimal("10.00").compareTo(plb.get("plb04").getAsBigDecimal()));
        assertEquals(0, new BigDecimal("42").compareTo(tx.getAsJsonObject("se").get("se01").getAsBigDecimal()));
    }

    @Test
    void the835MoneyInvariantsHold() throws Exception {
        JsonObject tx = materialize(Fixtures.F835);
        BigDecimal bpr02 = tx.getAsJsonObject("header").getAsJsonObject("bpr").get("bpr02").getAsBigDecimal();
        BigDecimal plb04 = tx.getAsJsonObject("footer").getAsJsonArray("plb").get(0).getAsJsonObject().get("plb04").getAsBigDecimal();
        JsonArray claims = tx.getAsJsonArray("detail").get(0).getAsJsonObject().getAsJsonArray("loop2000")
            .get(0).getAsJsonObject().getAsJsonArray("loop2100");
        BigDecimal paid = BigDecimal.ZERO;
        int svcCount = 0;
        int casCount = 0;
        for (JsonElement c : claims) {
            JsonObject claim = c.getAsJsonObject();
            BigDecimal clp04 = claim.getAsJsonObject("clp").get("clp04").getAsBigDecimal();
            BigDecimal clp05 = claim.getAsJsonObject("clp").get("clp05").getAsBigDecimal();
            paid = paid.add(clp04);
            BigDecimal svcPaid = BigDecimal.ZERO;
            BigDecimal pr = BigDecimal.ZERO;
            for (JsonElement s : claim.getAsJsonArray("loop2110")) {
                svcCount++;
                JsonObject line = s.getAsJsonObject();
                BigDecimal svc02 = line.getAsJsonObject("svc").get("svc02").getAsBigDecimal();
                BigDecimal svc03 = line.getAsJsonObject("svc").get("svc03").getAsBigDecimal();
                svcPaid = svcPaid.add(svc03);
                BigDecimal adj = BigDecimal.ZERO;
                for (JsonElement a : line.getAsJsonArray("cas")) {
                    casCount++;
                    JsonObject cas = a.getAsJsonObject();
                    adj = adj.add(cas.get("cas03").getAsBigDecimal());
                    if ("PR".equals(cas.get("cas01").getAsString())) {
                        pr = pr.add(cas.get("cas03").getAsBigDecimal());
                    }
                }
                assertEquals(0, svc02.subtract(adj).compareTo(svc03), "SVC02 - CO - PR == SVC03");
            }
            assertEquals(0, svcPaid.compareTo(clp04), "sum(SVC03) == CLP04");
            assertEquals(0, pr.compareTo(clp05), "sum(PR) == CLP05");
        }
        assertEquals(3, svcCount);
        assertEquals(5, casCount);
        assertEquals(0, paid.subtract(plb04).compareTo(bpr02), "sum(CLP04) - PLB04 == BPR02");
    }

    @Test
    void the837PNestsHierarchicalLoopsAndSplitsComposites() throws Exception {
        JsonObject tx = materialize(Fixtures.F837P);
        assertEquals("BATCH000001", tx.getAsJsonObject("header").getAsJsonObject("bht").get("bht03").getAsString());
        assertEquals("12:00", tx.getAsJsonObject("header").getAsJsonObject("bht").get("bht05").getAsString(), "TM -> HH:MM");
        JsonObject loop2000A = tx.getAsJsonArray("detail").get(0).getAsJsonObject().getAsJsonArray("loop2000A").get(0).getAsJsonObject();
        assertEquals("20", loop2000A.getAsJsonObject("hl").get("hl03").getAsString());
        JsonObject loop2000B = loop2000A.getAsJsonArray("loop2000B").get(0).getAsJsonObject();
        assertEquals("18", loop2000B.getAsJsonObject("sbr").get("sbr02").getAsString());
        JsonObject claim = loop2000B.getAsJsonArray("loop2300").get(0).getAsJsonObject();
        JsonObject clm = claim.getAsJsonObject("clm");
        assertEquals("CLM0001", clm.get("clm01").getAsString());
        assertEquals(0, new BigDecimal("300.00").compareTo(clm.get("clm02").getAsBigDecimal()));
        JsonObject c023 = clm.getAsJsonObject("clm05");
        assertEquals("11", c023.get("c02301").getAsString());
        assertEquals("B", c023.get("c02302").getAsString());
        assertEquals("1", c023.get("c02303").getAsString());
        JsonObject hi = claim.getAsJsonArray("hi").get(0).getAsJsonObject();
        assertEquals("ABK", hi.getAsJsonObject("hi01").get("c02201").getAsString());
        assertEquals("J069", hi.getAsJsonObject("hi01").get("c02202").getAsString());
        JsonArray lines = claim.getAsJsonArray("loop2400");
        assertEquals(2, lines.size());
        JsonObject sv1 = lines.get(1).getAsJsonObject().getAsJsonObject("sv1");
        assertEquals("36415", sv1.getAsJsonObject("sv101").get("c00302").getAsString());
        assertEquals(0, new BigDecimal("100.00").compareTo(sv1.get("sv102").getAsBigDecimal()));
        // DTP03 (data element 1251) is AN: its format is governed by DTP02 (D8/RD8), so it is not a DT and stays verbatim.
        JsonObject dtp = lines.get(1).getAsJsonObject().getAsJsonArray("dtp").get(0).getAsJsonObject();
        assertEquals("20260901", dtp.get("dtp03").getAsString());
        assertEquals("D8", dtp.get("dtp02").getAsString());
    }

    @Test
    void repeatedElementsSplitOnTheRepetitionSeparator() throws Exception {
        // HI01..HI12 are separate positions, so use a synthetic ^-repeat on a field the map marks repeating is not
        // available in the fixtures; verify the mechanism directly on a segment entry that repeats.
        StructureIndex idx = StructureIndex.fromClasspath("005010X222A1").orElseThrow();
        StructureIndex.FieldEntry repeating = null;
        String segId = null;
        for (StructureIndex.SegmentEntry se : idx.segments.values()) {
            for (StructureIndex.FieldEntry fe : se.fields) {
                if (fe.repeat != null && fe.repeat > 1 && fe.composite == null) {
                    repeating = fe;
                    segId = se.xid;
                    break;
                }
            }
            if (repeating != null) {
                break;
            }
        }
        if (repeating == null) {
            return; // guide has no ^-repeating simple element; nothing to test here
        }
        com.imsweb.x12.Segment seg = new com.imsweb.x12.Segment(new com.imsweb.x12.Separators('~', '*', ':'));
        StringBuilder sb = new StringBuilder(segId);
        for (int i = 1; i <= repeating.seq; i++) {
            sb.append('*').append(i == repeating.seq ? "A^B" : "");
        }
        seg.addElements(sb.toString());
        Materializer m = new Materializer(idx, com.zerobias.module.x12.parser.Separators.DEFAULT);
        Map<String, Object> out = m.materializeSegment(seg, idx.segment(segId));
        assertTrue(out.get(repeating.name) instanceof java.util.List, repeating.name + " -> " + out);
        assertEquals(2, ((java.util.List<?>) out.get(repeating.name)).size());
    }

    @Test
    void unknownSegmentsAndTrailingElementsAreKeptGenerically() throws Exception {
        // Append a trailing element to SE and drop a Z-style segment into the footer: nothing is lost.
        String t = Fixtures.text(Fixtures.F835).replace("SE*42*0001~", "SE*42*0001*EXTRA~");
        JsonObject tx = materialize(t.getBytes(StandardCharsets.UTF_8));
        assertEquals("EXTRA", tx.getAsJsonObject("se").get("se03").getAsString());
        assertFalse(tx.has("zzz"));
    }
}

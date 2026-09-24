package com.zerobias.module.x12.materializer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imsweb.x12.Segment;
import com.zerobias.module.x12.parser.Fixtures;
import com.zerobias.module.x12.parser.Separators;
import com.zerobias.module.x12.parser.X12Parse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        return materialize(X12Parse.parse(bytes, true, Clock.systemUTC()), 0);
    }

    private static JsonObject materialize(X12Parse.ParsedFile p, int transaction) {
        X12Parse.Transaction tx = p.transactions().get(transaction);
        Materializer m = new StructureResolver().materializerFor(tx.gs08(), p.separators()).orElseThrow();
        Map<String, Object> tree = m.materializeTransaction(tx.loop());
        return JsonParser.parseString(m.toJson(tree)).getAsJsonObject();
    }

    @Test
    void the835FollowsTheSchemaKeysAndTypes() throws Exception {
        JsonObject tx = materialize(Fixtures.F835);
        assertEquals(List.of("st", "header", "detail", "footer", "se"), tx.keySet().stream().toList(),
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
        assertEquals("12:00:00", tx.getAsJsonObject("header").getAsJsonObject("bht").get("bht05").getAsString(),
            "TM -> HH:MM:SS");
        JsonObject loop2000A = tx.getAsJsonArray("detail").get(0).getAsJsonObject().getAsJsonArray("loop2000A").get(0).getAsJsonObject();
        assertEquals("20", loop2000A.getAsJsonObject("hl").get("hl03").getAsString());
        JsonObject loop2000B = loop2000A.getAsJsonArray("loop2000B").get(0).getAsJsonObject();
        assertEquals("18", loop2000B.getAsJsonObject("sbr").get("sbr02").getAsString());
        JsonObject dmg = loop2000B.getAsJsonObject("loop2010BA").getAsJsonObject("dmg");
        assertEquals("1980-01-15", dmg.get("dmg02").getAsString(), "DMG01 D8 names DMG02's format");
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
        // DTP03 (data element 1251) is AN on the wire; DTP02 (1250) says how to read it.
        JsonObject dtp = lines.get(1).getAsJsonObject().getAsJsonArray("dtp").get(0).getAsJsonObject();
        assertEquals("2026-09-01", dtp.get("dtp03").getAsString(), "D8 -> ISO date");
        assertEquals("D8", dtp.get("dtp02").getAsString());
    }

    @Test
    void rangeDatesBecomeIsoIntervals() throws Exception {
        JsonObject claim = materialize(Fixtures.F837I).getAsJsonArray("detail").get(0).getAsJsonObject()
            .getAsJsonArray("loop2000A").get(0).getAsJsonObject().getAsJsonArray("loop2000B").get(0).getAsJsonObject()
            .getAsJsonArray("loop2300").get(0).getAsJsonObject();
        JsonObject statement = null;
        for (JsonElement d : claim.getAsJsonArray("dtp")) {
            if ("434".equals(d.getAsJsonObject().get("dtp01").getAsString())) {
                statement = d.getAsJsonObject();
            }
        }
        assertEquals("RD8", statement.get("dtp02").getAsString());
        assertEquals("2026-09-05/2026-09-05", statement.get("dtp03").getAsString(), "RD8 -> ISO 8601 interval");
    }

    @Test
    void controlNumbersKeepTheirLeadingZeros() throws Exception {
        // AK102 is data element 28 (N0), the acknowledged GS06: an identifier, not a quantity.
        String t = Fixtures.text(Fixtures.F999).replace("AK1*HC*102*", "AK1*HC*000102*");
        JsonObject ak1 = materialize(t.getBytes(StandardCharsets.UTF_8)).getAsJsonObject("header").getAsJsonObject("ak1");
        assertTrue(ak1.get("ak102").getAsJsonPrimitive().isString());
        assertEquals("000102", ak1.get("ak102").getAsString());
        assertEquals("0001", materialize(Fixtures.F999).getAsJsonObject("header").getAsJsonArray("loop2000").get(0)
            .getAsJsonObject().getAsJsonObject("ak2").get("ak202").getAsString());
    }

    @Test
    void repeatedElementsSplitOnTheRepetitionSeparator() {
        // The 834 map marks COB04 (a simple element) and DMG05 (composite C056) as ^-repeating.
        StructureIndex idx = StructureIndex.fromClasspath("005010X220A1").orElseThrow();
        assertEquals(Integer.valueOf(9), idx.segment("COB").fields.get(3).repeat);
        assertEquals("C056", idx.segment("DMG").fields.get(4).composite);
        Materializer m = new Materializer(idx, Separators.DEFAULT);

        Map<String, Object> cob = m.materializeSegment(segment("COB*P*POLICY01*1*1^30^35"), idx.segment("COB"));
        assertEquals(List.of("1", "30", "35"), cob.get("cob04"));
        assertEquals(List.of("30"), m.materializeSegment(segment("COB*P*POLICY01*1*30"), idx.segment("COB")).get("cob04"),
            "a repeating element is an array even with one value");

        Map<String, Object> dmg = m.materializeSegment(segment("DMG*D8*19800115*F**:RET:2106-3^:RET:2186-5"),
            idx.segment("DMG"));
        List<?> races = (List<?>) dmg.get("dmg05");
        assertEquals(2, races.size(), "each repetition is its own composite");
        assertEquals(Map.of("c05602", "RET", "c05603", "2106-3"), races.get(0));
        assertEquals(Map.of("c05602", "RET", "c05603", "2186-5"), races.get(1));
        assertEquals("1980-01-15", dmg.get("dmg02"));
    }

    @Test
    void trailingElementsBeyondTheLayoutAreKeptGenerically() throws Exception {
        String t = Fixtures.text(Fixtures.F835).replace("SE*42*0001~", "SE*42*0001*EXTRA~");
        JsonObject tx = materialize(t.getBytes(StandardCharsets.UTF_8));
        assertEquals("EXTRA", tx.getAsJsonObject("se").get("se03").getAsString());
    }

    @Test
    void unknownSegmentIsKeptGenericallyAndCountsAsAParserError() throws Exception {
        // imsweb files an unmapped segment under the loop it is in and reports it (non-fatal).
        String t = Fixtures.text(Fixtures.F835).replace("PLB*", "ZZZ*1*2~\nPLB*").replace("SE*42*0001~", "SE*43*0001~");
        X12Parse.ParsedFile p = X12Parse.parse(t.getBytes(StandardCharsets.UTF_8), false, Clock.systemUTC());
        assertEquals(List.of("Unable to find a matching segment format in loop 2110"), p.errors(),
            "counted in parserErrorCount");
        JsonObject line = materialize(p, 0).getAsJsonArray("detail").get(0).getAsJsonObject().getAsJsonArray("loop2000")
            .get(0).getAsJsonObject().getAsJsonArray("loop2100").get(1).getAsJsonObject().getAsJsonArray("loop2110")
            .get(0).getAsJsonObject();
        assertEquals("1", line.getAsJsonObject("zzz").get("zzz01").getAsString());
        assertEquals("2", line.getAsJsonObject("zzz").get("zzz02").getAsString());
    }

    @Test
    void secondOccurrenceOfASingleUseSegmentKeepsTheSchemaShape() throws Exception {
        String trn = "TRN*1*EFT000000101*1000000000~\n";
        String t = Fixtures.text(Fixtures.F835).replace(trn, trn + trn.replace("101*", "102*"))
            .replace("SE*42*0001~", "SE*43*0001~");
        X12Parse.ParsedFile p = X12Parse.parse(t.getBytes(StandardCharsets.UTF_8), false, Clock.systemUTC());
        assertEquals(List.of("TRN in loop HEADER appears too many times"), p.errors(), "counted in parserErrorCount");
        JsonElement header = materialize(p, 0).getAsJsonObject("header").get("trn");
        assertTrue(header.isJsonObject(), "TRN is single-use in the schema: " + header);
        assertEquals("EFT000000101", header.getAsJsonObject().get("trn02").getAsString(), "first occurrence");
    }

    /** Every key the materializer writes for a committed fixture is a schema property of the matching type. */
    @Test
    void everyFixtureMaterializesToItsGeneratedSchema() throws Exception {
        for (String fixture : List.of(Fixtures.F835, Fixtures.F837P, Fixtures.F837I, Fixtures.F277CA, Fixtures.F999)) {
            X12Parse.ParsedFile p = X12Parse.parse(Fixtures.bytes(fixture), false, Clock.systemUTC());
            Materializer m = new StructureResolver().materializerFor(p.gs08(), p.separators()).orElseThrow();
            for (X12Parse.Transaction tx : p.transactions()) {
                JsonObject json = JsonParser.parseString(m.toJson(m.materializeTransaction(tx.loop()))).getAsJsonObject();
                assertConforms(json, m.index().tableSchemaId, fixture);
            }
        }
    }

    private static void assertConforms(JsonObject object, String schemaId, String path) {
        Map<String, JsonObject> properties = new HashMap<>();
        for (JsonElement p : schema(schemaId).getAsJsonArray("properties")) {
            properties.put(p.getAsJsonObject().get("name").getAsString(), p.getAsJsonObject());
        }
        for (Map.Entry<String, JsonElement> e : object.entrySet()) {
            String at = path + "." + e.getKey();
            JsonObject property = properties.get(e.getKey());
            assertNotNull(property, at + " is not a property of " + schemaId);
            boolean multi = property.has("multi") && property.get("multi").getAsBoolean();
            assertEquals(multi, e.getValue().isJsonArray(), at + " array-ness must follow multi=" + multi);
            if (multi) {
                for (JsonElement item : e.getValue().getAsJsonArray()) {
                    assertValue(item, property, at + "[]");
                }
            } else {
                assertValue(e.getValue(), property, at);
            }
        }
    }

    private static void assertValue(JsonElement value, JsonObject property, String at) {
        String ref = property.has("references") ? property.getAsJsonObject("references").get("schemaId").getAsString() : null;
        if (ref != null && ref.startsWith("schema:type:")) {
            assertTrue(value.isJsonObject(), at + " composes " + ref + ": " + value);
            assertConforms(value.getAsJsonObject(), ref, at);
            return;
        }
        boolean string = value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
        switch (property.get("dataType").getAsString()) {
            case "decimal", "integer" -> assertTrue(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(),
                at + " is a number: " + value);
            case "date" -> assertTrue(string && value.getAsString().matches("\\d{4}-\\d{2}-\\d{2}"), at + " is a date: " + value);
            default -> {
                assertTrue(string, at + " is a string: " + value);
                if (property.has("format") && "time".equals(property.get("format").getAsString())) {
                    assertTrue(value.getAsString().matches("\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?"), at + " is a time: " + value);
                }
            }
        }
    }

    /** {@code schema:table|type:x12.<GS08>.<name>} from the generated classpath tree. */
    private static JsonObject schema(String id) {
        String[] parts = id.substring(id.indexOf(':', "schema:".length()) + 1).split("\\.");
        List<String> dirs = id.startsWith("schema:table:") ? List.of("transactions") : List.of("loops", "segments", "composites");
        for (String dir : dirs) {
            String resource = "schemas/" + parts[1] + "/" + dir + "/" + parts[2] + ".json";
            try (InputStream in = MaterializerTest.class.getClassLoader().getResourceAsStream(resource)) {
                if (in != null) {
                    return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        throw new AssertionError("no generated schema for " + id);
    }

    private static Segment segment(String text) {
        Segment seg = new Segment(new com.imsweb.x12.Separators('~', '*', ':'));
        seg.addElements(text);
        return seg;
    }
}

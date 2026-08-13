package com.zerobias.module.hl7.materializer;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.GenericModelClassFactory;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.validation.impl.ValidationContextFactory;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Phase 3 validation (DESIGN §5): the full structure-index-driven materializer,
 * run against the <em>real</em> generated index (produced by the codegen into
 * {@code $HL7_INDEX_DIR/structure-index/v27.json} by manual-test.sh, exactly as
 * the build would) and a generically-parsed message.
 *
 * <p>Parsing is forced generic via {@link GenericModelClassFactory} to mirror
 * production, where the typed HAPI structure jars are NOT on the runtime
 * classpath (DESIGN §4.1) — even though they happen to be on the test classpath
 * for the codegen.
 */
class MaterializerIT {

    private static final Gson GSON = new Gson();
    private static final String CR = "\r";

    private Materializer materializer() {
        // Prefer the classpath index the real build generates into target/classes
        // (so this runs in CI); fall back to HL7_INDEX_DIR for the manual harness.
        StructureIndex index = StructureIndex.fromClasspath("v27");
        if (index == null) {
            String dir = System.getenv("HL7_INDEX_DIR");
            assumeTrue(dir != null && !dir.isBlank(),
                "no classpath structure-index and HL7_INDEX_DIR unset; skipping");
            index = StructureIndex.fromFile(Path.of(dir, "structure-index", "v27.json"));
        }
        return new Materializer(index);
    }

    private Message parseGeneric(String er7) throws Exception {
        HapiContext ctx = new DefaultHapiContext();
        ctx.setValidationContext(ValidationContextFactory.noValidation());
        ctx.setModelClassFactory(new GenericModelClassFactory());   // force generic parse
        PipeParser parser = ctx.getPipeParser();
        return parser.parse(er7);
    }

    @Test
    void section5WorkedExample() throws Exception {
        Materializer m = materializer();
        // The DESIGN §5 PID, with MSH framing. PID-3 CX: idNumber^^^assigningAuthority(HD)^typeCode.
        String er7 = String.join(CR,
            "MSH|^~\\&|EPIC|HOSP|RECV|DEST|20260529103000||ADT^A01^ADT_A01|MSG1|P|2.7",
            "EVN|A01|20260529103000",
            "PID|1||5551212^^^EPIC&&ISO^MR||SMITH^JOHN||19800101|M",
            "PV1|1|I") + CR;

        JsonObject root = GSON.fromJson(m.toTypedJson(parseGeneric(er7)), JsonObject.class);
        JsonObject pid = root.getAsJsonObject("pid");

        // NOTE: bean names are HAPI's actual decapitalization (IDNumber, namespaceID,
        // dateTimeOfBirth, …) — they match the generated schemas. DESIGN §5's
        // idNumber/namespaceId/surname/dateOfBirth are illustrative, not the contract.

        // PID-3 patientIdentifierList: CX[] with a recursed HD composite
        JsonArray ids = pid.getAsJsonArray("patientIdentifierList");
        assertEquals(1, ids.size());
        JsonObject cx = ids.get(0).getAsJsonObject();
        assertEquals("5551212", cx.get("IDNumber").getAsString());
        assertEquals("MR", cx.get("identifierTypeCode").getAsString());
        JsonObject hd = cx.getAsJsonObject("assigningAuthority");
        assertEquals("EPIC", hd.get("namespaceID").getAsString());
        assertEquals("ISO", hd.get("universalIDType").getAsString());

        // PID-5 patientName: XPN[] with a recursed FN composite (familyName is an object,
        // even though "SMITH" was written under-delimited — driven by the index type)
        JsonArray names = pid.getAsJsonArray("patientName");
        assertEquals(1, names.size());
        JsonObject xpn = names.get(0).getAsJsonObject();
        assertEquals("SMITH", xpn.getAsJsonObject("familyName").get("surname").getAsString());
        assertEquals("JOHN", xpn.get("givenName").getAsString());

        // PID-7 dateTimeOfBirth: DTM is a PRIMITIVE in v2.7 (v2.5.1's TS composite
        // was retired), so the value is a flat string, not nested under "time". The
        // date is precision-preserving (NO fabricated midnight — the validated
        // normalizer's contract).
        assertEquals("1980-01-01", pid.get("dateTimeOfBirth").getAsString());

        // PID-8 administrativeSex: CWE in v2.7 (was IS in v2.5.1), so the bare code
        // "M" lands in CWE-1 identifier — emitted as the raw code (tagged, not resolved)
        assertEquals("M", pid.getAsJsonObject("administrativeSex").get("identifier").getAsString());

        // MSH-3 sendingApplication is an HD composite
        assertEquals("EPIC",
            root.getAsJsonObject("msh").getAsJsonObject("sendingApplication").get("namespaceID").getAsString());
    }

    @Test
    void oruPreservesGroupNesting() throws Exception {
        Materializer m = materializer();
        String er7 = String.join(CR,
            "MSH|^~\\&|LAB|HOSP|RECV|DEST|20260529103000||ORU^R01^ORU_R01|MSG2|P|2.7",
            "PID|1||9001234^^^LAB^MR||DOE^JANE||19750210|F",
            "OBR|1|||CBC^Complete Blood Count",
            "OBX|1|NM|WBC^White Blood Count||7.2|10*3/uL|||||F",
            "OBX|2|NM|HGB^Hemoglobin||14.1|g/dL|||||F") + CR;

        JsonObject root = GSON.fromJson(m.toTypedJson(parseGeneric(er7)), JsonObject.class);

        // Group nesting is preserved (DESIGN §5): ORU_R01 = MSH + PATIENT_RESULT[
        //   PATIENT{pid…}, ORDER_OBSERVATION[{obr, OBSERVATION[{obx}]}] ]. Segments are
        // NOT hoisted to the top level — which OBX belongs to which order survives.
        assertTrue(root.has("msh"), "msh at message level");
        assertTrue(!root.has("pid") && !root.has("obx") && !root.has("obr"),
            "segments are nested under their groups, not top-level: " + root.keySet());

        JsonArray patientResult = root.getAsJsonArray("patient_result");
        assertEquals(1, patientResult.size());
        JsonObject pr = patientResult.get(0).getAsJsonObject();

        // PID inside PATIENT — a single object (its own ref is non-repeating), even though
        // PATIENT_RESULT itself repeats.
        JsonObject pid = pr.getAsJsonObject("patient").getAsJsonObject("pid");
        assertEquals("DOE", pid.getAsJsonArray("patientName").get(0).getAsJsonObject()
            .getAsJsonObject("familyName").get("surname").getAsString());

        // One order; its two results nested as OBSERVATION instances beneath it.
        JsonArray orders = pr.getAsJsonArray("order_observation");
        assertEquals(1, orders.size());
        JsonObject order = orders.get(0).getAsJsonObject();
        assertEquals("CBC", order.getAsJsonObject("obr")
            .getAsJsonObject("universalServiceIdentifier").get("identifier").getAsString());

        JsonArray observations = order.getAsJsonArray("observation");
        assertEquals(2, observations.size());
        assertEquals("7.2", observations.get(0).getAsJsonObject().getAsJsonObject("obx")
            .getAsJsonArray("observationValue").get(0).getAsString());
        assertEquals("14.1", observations.get(1).getAsJsonObject().getAsJsonObject("obx")
            .getAsJsonArray("observationValue").get(0).getAsString());
    }

    @Test
    void explicitNullVsAbsent() throws Exception {
        Materializer m = materializer();
        // The HL7 explicit-null sentinel ("") must survive as JSON null with the key
        // PRESENT (serializeNulls), distinct from an absent field whose key is omitted.
        // Exercised on a PRIMITIVE field (PID-1 setIDPID, SI): a composite field's ""
        // would surface the null nested under its first component, not at the field's
        // top level, so a primitive is the faithful explicit-null probe. (In v2.7
        // PID-8 administrativeSex is a CWE composite, so it is no longer usable here.)
        // PID-7 (dateTimeOfBirth) is left absent → key omitted.
        String er7 = String.join(CR,
            "MSH|^~\\&|EPIC|HOSP|RECV|DEST|20260529103000||ADT^A01^ADT_A01|MSG3|P|2.7",
            "PID|\"\"||5551212^^^EPIC^MR||SMITH^JOHN") + CR;

        JsonObject pid = GSON.fromJson(m.toTypedJson(parseGeneric(er7)), JsonObject.class)
            .getAsJsonObject("pid");
        // dateTimeOfBirth (PID-7) was absent → key omitted entirely
        assertTrue(!pid.has("dateTimeOfBirth"), "absent field omitted");
        // setIDPID (PID-1) was "" → key present, value JSON null
        assertTrue(pid.has("setIDPID"), "explicit-null field key is present");
        assertTrue(pid.get("setIDPID").isJsonNull(), "HL7 \"\" → JSON null");
    }
}

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
 * {@code $HL7_INDEX_DIR/structure-index/v251.json} by manual-test.sh, exactly as
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
        String dir = System.getenv("HL7_INDEX_DIR");
        assumeTrue(dir != null && !dir.isBlank(),
            "HL7_INDEX_DIR not set (manual-test.sh generates the index); skipping");
        StructureIndex index = StructureIndex.fromFile(Path.of(dir, "structure-index", "v251.json"));
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
            "MSH|^~\\&|EPIC|HOSP|RECV|DEST|20260529103000||ADT^A01^ADT_A01|MSG1|P|2.5.1",
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

        // PID-7 dateTimeOfBirth: TS is a composite in v2.5.1 (TS-1 = time), so the
        // value nests under "time" — faithful to the schema. The date is
        // precision-preserving (NO fabricated midnight — the validated normalizer's
        // contract; DESIGN §5's flat "…T00:00:00Z" is illustrative, predates it).
        assertEquals("1980-01-01", pid.getAsJsonObject("dateTimeOfBirth").get("time").getAsString());

        // PID-8 administrativeSex: table-bound value emitted as the raw code (tagged, not resolved)
        assertEquals("M", pid.get("administrativeSex").getAsString());

        // MSH-3 sendingApplication is an HD composite
        assertEquals("EPIC",
            root.getAsJsonObject("msh").getAsJsonObject("sendingApplication").get("namespaceID").getAsString());
    }

    @Test
    void oruRoundTripsWithRepeatingObx() throws Exception {
        Materializer m = materializer();
        String er7 = String.join(CR,
            "MSH|^~\\&|LAB|HOSP|RECV|DEST|20260529103000||ORU^R01^ORU_R01|MSG2|P|2.5.1",
            "PID|1||9001234^^^LAB^MR||DOE^JANE||19750210|F",
            "OBR|1|||CBC^Complete Blood Count",
            "OBX|1|NM|WBC^White Blood Count||7.2|10*3/uL|||||F",
            "OBX|2|NM|HGB^Hemoglobin||14.1|g/dL|||||F") + CR;

        JsonObject root = GSON.fromJson(m.toTypedJson(parseGeneric(er7)), JsonObject.class);

        assertTrue(root.has("pid"), "pid present");
        assertTrue(root.has("obr"), "obr present");
        // two OBX → array (repeating segment, flattened from its group)
        JsonArray obx = root.getAsJsonArray("obx");
        assertEquals(2, obx.size());
        assertEquals("7.2", obx.get(0).getAsJsonObject().getAsJsonArray("observationValue").get(0).getAsString());
        // PID sits in a repeating group in ORU_R01 → flattened as an array
        assertEquals("DOE", root.getAsJsonArray("pid").get(0).getAsJsonObject()
            .getAsJsonArray("patientName").get(0).getAsJsonObject()
            .getAsJsonObject("familyName").get("surname").getAsString());
    }

    @Test
    void explicitNullVsAbsent() throws Exception {
        Materializer m = materializer();
        // PID-6 mothersMaidenName explicit-null (""), PID-7 absent.
        String er7 = String.join(CR,
            "MSH|^~\\&|EPIC|HOSP|RECV|DEST|20260529103000||ADT^A01^ADT_A01|MSG3|P|2.5.1",
            "PID|1||5551212^^^EPIC^MR||SMITH^JOHN|\"\"||M") + CR;

        JsonObject pid = GSON.fromJson(m.toTypedJson(parseGeneric(er7)), JsonObject.class)
            .getAsJsonObject("pid");
        // dateTimeOfBirth (PID-7) was empty → key omitted entirely
        assertTrue(!pid.has("dateTimeOfBirth"), "absent field omitted");
        // administrativeSex (PID-8) present
        assertEquals("M", pid.get("administrativeSex").getAsString());
    }
}

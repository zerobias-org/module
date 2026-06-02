package com.zerobias.module.hl7.materializer;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import com.google.gson.JsonObject;

/**
 * INTERIM materializer (DESIGN §5): emits the common envelope fields plus a few
 * patient basics via {@link Terser}, normalized through {@link Hl7Normalizer}.
 *
 * <p>This exists so the receive→buffer→browse path works end-to-end before the
 * full structure-index-driven {@code Materializer} (Phase 3) lands. The full
 * version will walk every segment/field per the generated structure index and
 * produce the complete typed bean graph; this one is deliberately shallow.
 */
public final class EnvelopeMaterializer implements MessageMaterializer {

    @Override
    public String toTypedJson(Message message) throws HL7Exception {
        final Terser t = new Terser(message);
        final JsonObject o = new JsonObject();
        put(o, "controlId", t.get("/MSH-10"));
        put(o, "sendingApplication", t.get("/MSH-3"));
        put(o, "sendingFacility", t.get("/MSH-4"));
        put(o, "messageCode", t.get("/MSH-9-1"));
        put(o, "triggerEvent", t.get("/MSH-9-2"));
        final String msgDtm = t.get("/MSH-7");
        if (msgDtm != null && !msgDtm.isEmpty()) {
            o.addProperty("messageDateTime", Hl7Normalizer.normalizeDateTime(msgDtm));
        }
        // Patient basics, best-effort (segment absent on non-patient messages).
        try {
            put(o, "patientFamilyName", t.get("/PID-5-1"));
            put(o, "patientGivenName", t.get("/PID-5-2"));
            final String dob = t.get("/PID-7");
            if (dob != null && !dob.isEmpty()) {
                o.addProperty("dateOfBirth", Hl7Normalizer.normalizeDate(dob));
            }
            put(o, "administrativeSex", t.get("/PID-8"));
        } catch (HL7Exception noPidSegment) {
            // not a patient-bearing message — fine
        }
        return o.toString();
    }

    private static void put(JsonObject o, String key, String value) {
        if (value != null && !value.isEmpty()) {
            o.addProperty(key, value);
        }
    }
}

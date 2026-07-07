package com.zerobias.module.hl7.materializer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Per-message version routing (DESIGN §5 / §11.5): the registry maps each message's
 * MSH-12 to the right slot, lazily loading bundled indexes and degrading unbundled
 * versions to the envelope schema. Exercises the baked-in classpath slots (v23–v27).
 */
class MaterializerRegistryIT {

    private static Materializer v27() {
        StructureIndex idx = StructureIndex.fromClasspath("v27");
        assertNotNull(idx, "v27 structure-index must be on the classpath");
        return new Materializer(idx);
    }

    @Test
    void routesEachVersionToItsOwnBundledSlot() {
        MaterializerRegistry r = MaterializerRegistry.routing("v27", v27(), true);

        // A 2.4 message resolves to the v24 slot: typed, with a v24 table schema id...
        assertEquals("v24", r.slotFor("2.4"));
        assertEquals("schema:table:hl7v2.v24.ADT_A01", r.schemaIdFor("2.4", "ADT_A01"));
        assertTrue(r.materializerFor("2.4") instanceof Materializer,
            "v24 is bundled → typed materializer, lazily loaded from the classpath");

        // ...and the configured default slot still resolves to its own version.
        assertEquals("schema:table:hl7v2.v27.ADT_A01", r.schemaIdFor("2.7", "ADT_A01"));

        // 2.5.1 → v251 (multi-dot slot), also bundled.
        assertEquals("v251", r.slotFor("2.5.1"));
        assertEquals("schema:table:hl7v2.v251.ORU_R01", r.schemaIdFor("2.5.1", "ORU_R01"));
    }

    @Test
    void unbundledVersionDegradesToEnvelopeButIsStillRecorded() {
        MaterializerRegistry r = MaterializerRegistry.routing("v27", v27(), true);

        assertEquals("v99", r.slotFor("9.9"));   // slot derived even if not bundled
        assertEquals(MaterializerRegistry.ENVELOPE_SCHEMA, r.schemaIdFor("9.9", "ADT_A01"));
        assertFalse(r.materializerFor("9.9") instanceof Materializer,
            "unbundled version → generic materializer, never a drop");
    }

    @Test
    void blankOrMissingVersionFallsBackToDefaultSlot() {
        MaterializerRegistry r = MaterializerRegistry.routing("v27", v27(), true);
        assertEquals("v27", r.slotFor(null));
        assertEquals("v27", r.slotFor(""));
        assertEquals("schema:table:hl7v2.v27.ADT_A01", r.schemaIdFor(null, "ADT_A01"));
    }

    @Test
    void pinnedIgnoresMsh12() {
        // Config-fixed deployment: one slot for everything, regardless of MSH-12.
        MaterializerRegistry typed = MaterializerRegistry.pinned("v27", v27());
        assertEquals("v27", typed.slotFor("2.4"));
        assertEquals("schema:table:hl7v2.v27.ADT_A01", typed.schemaIdFor("2.4", "ADT_A01"));

        // A degraded (no-index) pin carries the envelope schema, not a table id it can't serve.
        MaterializerRegistry envelope = MaterializerRegistry.pinned("v27", new EnvelopeMaterializer());
        assertEquals(MaterializerRegistry.ENVELOPE_SCHEMA, envelope.schemaIdFor("2.7", "ADT_A01"));
    }
}

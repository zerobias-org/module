package com.zerobias.module.x12.producer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DESIGN §7: the bundled pack catalog. The generator emits {@code packs.json} and every
 * schema it attributes must be one the registry can actually serve — a pack that declares
 * an id nothing resolves is the failure mode this catalog exists to surface.
 */
class PackCatalogTest {

    private static final SchemaRegistry SCHEMAS = SchemaRegistry.fromClasspath();

    @Test
    void theBundledCatalogCoversEveryGeneratedSchema() {
        PackCatalog catalog = PackCatalog.fromClasspath();
        assertFalse(catalog.packs().isEmpty(), "packs.json ships in the jar");

        // One pack per canonical guide, plus codes and core. A GS08 alias resolves to its
        // canonical guide via guides.txt before any lookup, so it gets no pack of its own.
        assertTrue(catalog.guides().contains("005010X221A1"), "the 835 guide");
        assertFalse(catalog.guides().contains("005010X231"), "an alias label has no pack of its own");
        assertNotNull(find(catalog, "x12-codes"), "code sets are their own pack (quarterly cadence)");
        assertNotNull(find(catalog, PackCatalog.CORE_PACK), "the receiver's own schemas");

        for (PackCatalog.Pack p : catalog.packs()) {
            assertEquals("x12", p.namespace(), p.name());
            assertEquals("bundled", p.source(), p.name());
            assertNull(p.version(), "a bundled pack's version IS the module's; " + p.name());
            assertEquals(p.schemaIds().size(), p.schemaCount(), p.name());
            assertTrue(p.schemaCount() > 0, p.name() + " declares schemas");
            assertEquals(List.of(), p.missingFrom(SCHEMAS),
                p.name() + " declares ids the registry cannot serve");
        }
    }

    @Test
    void guidePacksCarryTheirGuideAndStructureIndex() {
        PackCatalog.Pack p = find(PackCatalog.fromClasspath(), "x12-guide-005010X221A1");
        assertNotNull(p);
        assertEquals("005010X221A1", p.gs08());
        assertEquals("835", p.transactionType());
        assertNull(p.aliasOf(), "the canonical label");
        assertEquals("structure-index/005010X221A1.json", p.structureIndex());
        assertEquals("x12.005010X221A1.*", p.idScope());
        assertFalse(p.core());
        assertTrue(p.schemaIds().contains("schema:table:x12.005010X221A1.835"),
            "the table id belongs to the guide pack even though it shares no prefix with its types");
        assertTrue(p.schemaIds().contains("schema:type:x12.005010X221A1.ST"));
    }

    @Test
    void aliasLabelsResolveToTheirCanonicalGuidePack() {
        PackCatalog catalog = PackCatalog.fromClasspath();
        assertNull(find(catalog, "x12-guide-005010X231"), "the alias label X231 gets no pack of its own");
        PackCatalog.Pack canonical = find(catalog, "x12-guide-005010X231A1");
        assertNotNull(canonical, "it resolves to X231A1 via guides.txt");
        assertEquals("005010X231A1", canonical.gs08());
        assertNull(canonical.aliasOf(), "the canonical label");
        assertEquals("999", canonical.transactionType());
    }

    @Test
    void theCorePackIsMarkedAndNeverAGuide() {
        PackCatalog.Pack core = find(PackCatalog.fromClasspath(), PackCatalog.CORE_PACK);
        assertNotNull(core);
        assertTrue(core.core(), "nothing may supersede the module's own contract");
        assertNull(core.gs08());
        assertNull(core.structureIndex());
        assertTrue(core.schemaIds().contains("schema:shared:x12.transaction-envelope"));
    }

    @Test
    void describeReportsResolutionStatus() {
        PackCatalog.Pack p = find(PackCatalog.fromClasspath(), "x12-guide-005010X221A1");
        Map<String, Object> d = p.describe(SCHEMAS);
        assertEquals("active", d.get("status"));
        assertEquals(List.of(), d.get("missingSchemas"));

        // A pack declaring an id nothing serves is reported degraded, not hidden.
        PackCatalog broken = PackCatalog.parse(
            "[{\"name\":\"x12-guide-ghost\",\"namespace\":\"x12\",\"source\":\"uploaded\","
            + "\"version\":\"1.2.3\",\"gs08\":\"005010X999\",\"idScope\":\"x12.005010X999.*\","
            + "\"schemaIds\":[\"schema:type:x12.005010X999.NOPE\"]}]");
        PackCatalog.Pack ghost = broken.packs().get(0);
        assertEquals(1, ghost.schemaCount(), "schemaCount falls back to the id list");
        assertEquals("uploaded", ghost.source());
        assertEquals("1.2.3", ghost.version());
        Map<String, Object> gd = ghost.describe(SCHEMAS);
        assertEquals("degraded", gd.get("status"));
        assertEquals(List.of("schema:type:x12.005010X999.NOPE"), gd.get("missingSchemas"));
    }

    @Test
    void malformedOrAbsentManifestsDegradeToEmpty() {
        for (String json : List.of("", "{}", "null", "not json", "[1,2,3]", "[{\"namespace\":\"x12\"}]")) {
            assertEquals(0, PackCatalog.parse(json).size(), json);
        }
        assertEquals(0, PackCatalog.empty().size());
        assertEquals(0, PackCatalog.fromDirectory(null).size());
        assertEquals(0, PackCatalog.empty().schemaCount());
    }

    private static PackCatalog.Pack find(PackCatalog catalog, String name) {
        for (PackCatalog.Pack p : catalog.packs()) {
            if (name.equals(p.name())) {
                return p;
            }
        }
        return null;
    }
}

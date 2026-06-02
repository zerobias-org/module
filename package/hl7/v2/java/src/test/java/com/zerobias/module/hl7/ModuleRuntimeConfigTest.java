package com.zerobias.module.hl7;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parsing of the opaque MODULE_CONFIG (DESIGN §3.2). It drives the extension
 * activeExtensions filter plus the daemon-level ack-durability and retention
 * knobs, so a parse bug silently loads the wrong packs or mis-configures the
 * buffer at boot. Malformed config must degrade to safe defaults, never crash.
 */
class ModuleRuntimeConfigTest {

    @Test
    void absentOrEmptyMeansNoFilter() {
        assertEquals(Set.of(), ModuleRuntimeConfig.parse(null).activeExtensions());
        assertEquals(Set.of(), ModuleRuntimeConfig.parse("").activeExtensions());
        assertEquals(Set.of(), ModuleRuntimeConfig.parse("{}").activeExtensions());
    }

    @Test
    void ackDurabilityDefaultsToNormalAndParsesFull() {
        assertFalse(ModuleRuntimeConfig.parse("{}").fullDurability(), "default = normal (not full)");
        assertFalse(ModuleRuntimeConfig.parse("{\"ackDurability\":\"normal\"}").fullDurability());
        assertTrue(ModuleRuntimeConfig.parse("{\"ackDurability\":\"full\"}").fullDurability());
        assertTrue(ModuleRuntimeConfig.parse("{\"ackDurability\":\"FULL\"}").fullDurability(), "case-insensitive");
        // unknown value is not 'full' → safe (normal)
        assertFalse(ModuleRuntimeConfig.parse("{\"ackDurability\":\"bogus\"}").fullDurability());
    }

    @Test
    void retentionAbsentMeansUnbounded() {
        var r = ModuleRuntimeConfig.parse("{}").retention();
        assertNull(r.maxAge());
        assertNull(r.maxBytes());
    }

    @Test
    void retentionParsesBytesAndIsoDuration() {
        var r = ModuleRuntimeConfig.parse(
            "{\"retention\":{\"maxBytes\":10737418240,\"maxAge\":\"P7D\"}}").retention();
        assertEquals(10737418240L, r.maxBytes());
        assertEquals(Duration.ofDays(7), r.maxAge());
    }

    @Test
    void retentionAxesAreIndependentAndMalformedAgeDisablesOnlyAge() {
        // only one axis set
        assertNull(ModuleRuntimeConfig.parse("{\"retention\":{\"maxBytes\":1024}}").retention().maxAge());
        assertEquals(1024L, ModuleRuntimeConfig.parse("{\"retention\":{\"maxBytes\":1024}}").retention().maxBytes());
        // a bad ISO-8601 maxAge disables age-based eviction but keeps maxBytes + rest of config
        var r = ModuleRuntimeConfig.parse(
            "{\"activeExtensions\":[\"epic\"],\"retention\":{\"maxBytes\":2048,\"maxAge\":\"7 days\"}}");
        assertNull(r.retention().maxAge(), "non-ISO maxAge → null, not a crash");
        assertEquals(2048L, r.retention().maxBytes());
        assertEquals(Set.of("epic"), r.activeExtensions(), "bad maxAge must not discard the rest");
    }

    @Test
    void malformedConfigDegradesAllFieldsToSafeDefaults() {
        var r = ModuleRuntimeConfig.parse("not json at all");
        assertEquals(Set.of(), r.activeExtensions());
        assertFalse(r.fullDurability());
        assertNull(r.retention().maxAge());
        assertNull(r.retention().maxBytes());
    }

    @Test
    void readsActiveExtensions() {
        assertEquals(Set.of("epic-adt", "cerner"),
            ModuleRuntimeConfig.parse("{\"activeExtensions\":[\"epic-adt\",\"cerner\"]}").activeExtensions());
    }

    @Test
    void nonArrayActiveExtensionsIsIgnored() {
        // shape mismatch (string, not array) → no filter, not a crash
        assertEquals(Set.of(),
            ModuleRuntimeConfig.parse("{\"activeExtensions\":\"epic-adt\"}").activeExtensions());
    }

    @Test
    void malformedJsonDoesNotCrash() {
        assertEquals(Set.of(), ModuleRuntimeConfig.parse("not json at all").activeExtensions());
        assertEquals(Set.of(), ModuleRuntimeConfig.parse("{\"activeExtensions\":[").activeExtensions());
    }
}

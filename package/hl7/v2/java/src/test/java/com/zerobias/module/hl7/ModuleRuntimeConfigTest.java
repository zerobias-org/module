package com.zerobias.module.hl7;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Parsing of the opaque MODULE_CONFIG (DESIGN §3.2). It drives the extension
 * activeExtensions filter, so a parse bug silently loads/skips the wrong packs.
 */
class ModuleRuntimeConfigTest {

    @Test
    void absentOrEmptyMeansNoFilter() {
        assertEquals(Set.of(), ModuleRuntimeConfig.parse(null).activeExtensions());
        assertEquals(Set.of(), ModuleRuntimeConfig.parse("").activeExtensions());
        assertEquals(Set.of(), ModuleRuntimeConfig.parse("{}").activeExtensions());
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

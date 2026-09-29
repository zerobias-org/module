package com.zerobias.module.x12;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Process-env resolution (DESIGN §3). There are no listener ports in this module, so
 * the only hard failure is a garbage INTERNAL_PORT; everything else has a default.
 */
class ModuleConfigTest {

    @Test
    void appliesDefaults() {
        ModuleConfig c = ModuleConfig.resolve(Map.of());
        assertEquals(8889, c.internalPort());
        assertEquals("/var/lib/module/buffer.db", c.bufferDbPath());
        assertEquals("/opt/module/runtimeConfig.yml", c.runtimeConfigYml());
    }

    @Test
    void readsOverrides() {
        ModuleConfig c = ModuleConfig.resolve(Map.of(
            "INTERNAL_PORT", "9000",
            "BUFFER_DB", "/tmp/b.db",
            "RUNTIME_CONFIG_YML", "/tmp/rc.yml"));
        assertEquals(9000, c.internalPort());
        assertEquals("/tmp/b.db", c.bufferDbPath());
        assertEquals("/tmp/rc.yml", c.runtimeConfigYml());
    }

    @Test
    void rejectsNonNumericAndOutOfRangePort() {
        assertThrows(IllegalStateException.class, () -> ModuleConfig.resolve(Map.of("INTERNAL_PORT", "abc")));
        assertThrows(IllegalStateException.class, () -> ModuleConfig.resolve(Map.of("INTERNAL_PORT", "70000")));
        assertThrows(IllegalStateException.class, () -> ModuleConfig.resolve(Map.of("INTERNAL_PORT", "0")));
    }
}

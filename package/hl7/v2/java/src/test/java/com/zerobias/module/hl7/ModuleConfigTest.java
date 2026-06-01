package com.zerobias.module.hl7;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The daemon precondition + port parsing (DESIGN §3.2). The mandatory-port refusal
 * is startup-critical safety: a daemon listener with no port is a misconfiguration,
 * not a fallback. Exercised via the {@code from(...)} seam so we don't touch process env.
 */
class ModuleConfigTest {

    @Test
    void refusesWithoutListenerPort() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> ModuleConfig.from("8889", null, null));
        assertTrue(e.getMessage().contains("LISTENER_PORT_MLLP"), e.getMessage());
        assertThrows(IllegalStateException.class, () -> ModuleConfig.from("8889", "", null));
        assertThrows(IllegalStateException.class, () -> ModuleConfig.from("8889", "   ", null));
    }

    @Test
    void rejectsNonNumericAndOutOfRangePorts() {
        assertThrows(IllegalStateException.class, () -> ModuleConfig.from("8889", "abc", null));
        assertThrows(IllegalStateException.class, () -> ModuleConfig.from("8889", "70000", null));
        assertThrows(IllegalStateException.class, () -> ModuleConfig.from("8889", "0", null));
        assertThrows(IllegalStateException.class, () -> ModuleConfig.from("notaport", "2575", null));
    }

    @Test
    void parsesValidAndAppliesDefaults() {
        ModuleConfig c = ModuleConfig.from("9000", "2575", "/custom/ext");
        assertEquals(9000, c.internalPort());
        assertEquals(2575, c.mllpPort());
        assertEquals("/custom/ext", c.extensionDir());

        // defaults: internal port 8889, EXTENSION_DIR /opt/module/extensions
        ModuleConfig d = ModuleConfig.from(null, "2575", null);
        assertEquals(8889, d.internalPort());
        assertEquals(2575, d.mllpPort());
        assertEquals("/opt/module/extensions", d.extensionDir());
    }
}

package com.zerobias.module.hl7;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Listener resolution + the daemon precondition (DESIGN §3.2). The mandatory-port
 * refusal is startup-critical safety: a daemon listener with no port is a
 * misconfiguration, not a fallback. Exercised via the {@code resolve/build/
 * listenersFromEnv} seams so we touch neither process env nor the filesystem.
 */
class ModuleConfigTest {

    @Test
    void refusesWithNoListeners() {
        // No file, no LISTENER_PORT_* env -> refuse to boot.
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> ModuleConfig.resolve(Optional.empty(), Map.of()));
        assertTrue(e.getMessage().contains("No listener ports"), e.getMessage());
        assertThrows(IllegalStateException.class,
            () -> ModuleConfig.build("8889", List.of(), null));
    }

    @Test
    void envFallbackParsesSingleAndMultiplePorts() {
        // Single MLLP port (today's common case).
        List<ListenerSpec> one = ModuleConfig.listenersFromEnv(Map.of("LISTENER_PORT_MLLP", "2575"));
        assertEquals(List.of(new ListenerSpec("mllp", 2575)), one);

        // Many named ports; the LISTENER_PORTS index var is NOT a listener.
        List<ListenerSpec> many = ModuleConfig.listenersFromEnv(Map.of(
            "LISTENER_PORT_MLLP", "2575",
            "LISTENER_PORT_EPIC", "3000",
            "LISTENER_PORTS", "mllp,epic",
            "UNRELATED", "x"));
        assertEquals(2, many.size());
        assertTrue(many.contains(new ListenerSpec("mllp", 2575)));
        assertTrue(many.contains(new ListenerSpec("epic", 3000)));
    }

    @Test
    void fileWinsOverEnvWhenPresentAndNonEmpty() {
        RuntimeConfigFile file = RuntimeConfigFile.parse(
            "{\"listenerPorts\":[{\"name\":\"mllp\",\"port\":2575},{\"name\":\"epic-adt\",\"port\":3001}]}");
        // Env also has ports, but the canonical file is authoritative.
        ModuleConfig c = ModuleConfig.resolve(Optional.of(file),
            Map.of("LISTENER_PORT_MLLP", "9999"));
        assertEquals(List.of(new ListenerSpec("mllp", 2575), new ListenerSpec("epic-adt", 3001)),
            c.listeners());
    }

    @Test
    void emptyFileFallsBackToEnv() {
        RuntimeConfigFile empty = RuntimeConfigFile.parse("{\"listenerPorts\":[]}");
        ModuleConfig c = ModuleConfig.resolve(Optional.of(empty),
            Map.of("LISTENER_PORT_MLLP", "2575"));
        assertEquals(List.of(new ListenerSpec("mllp", 2575)), c.listeners());
    }

    @Test
    void rejectsNonNumericAndOutOfRangePorts() {
        assertThrows(IllegalStateException.class,
            () -> ModuleConfig.listenersFromEnv(Map.of("LISTENER_PORT_MLLP", "abc")));
        assertThrows(IllegalStateException.class,
            () -> ModuleConfig.build("8889", List.of(new ListenerSpec("mllp", 70000)), null));
        assertThrows(IllegalStateException.class,
            () -> ModuleConfig.build("8889", List.of(new ListenerSpec("mllp", 0)), null));
        assertThrows(IllegalStateException.class,
            () -> ModuleConfig.build("notaport", List.of(new ListenerSpec("mllp", 2575)), null));
    }

    @Test
    void appliesInternalPortAndExtensionDirDefaults() {
        ModuleConfig c = ModuleConfig.build("9000", List.of(new ListenerSpec("mllp", 2575)), "/custom/ext");
        assertEquals(9000, c.internalPort());
        assertEquals("/custom/ext", c.extensionDir());

        ModuleConfig d = ModuleConfig.build(null, List.of(new ListenerSpec("mllp", 2575)), null);
        assertEquals(8889, d.internalPort());
        assertEquals("/opt/module/extensions", d.extensionDir());
        assertEquals(2575, d.listeners().get(0).port());
    }
}

package com.zerobias.module.hl7;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parsing the canonical {@code DeploymentRuntimeConfig} file into listener specs.
 * The file is authoritative for the many-named-ports model: verbatim names, resolved
 * ports. Malformed/unresolved entries must be skipped (not crash the daemon) so a
 * typo'd deploy can't wedge an always-on receiver.
 */
class RuntimeConfigFileTest {

    @Test
    void parsesResolvedListenerPortsVerbatim() {
        // Realistic shape: the hydra DeploymentRuntimeConfig with extra fields the
        // module ignores (daemonMode, config, durability) and verbatim port names.
        String json = "{"
            + "\"daemonMode\":true,"
            + "\"listenerPorts\":["
            + "  {\"name\":\"mllp\",\"port\":2575,\"protocol\":\"tcp\",\"bindAddress\":\"0.0.0.0\"},"
            + "  {\"name\":\"sender-01\",\"port\":3001},"
            + "  {\"name\":\"sender.01\",\"port\":3002}"
            + "],"
            + "\"config\":{\"hl7Version\":\"2.7\"}"
            + "}";
        List<ListenerSpec> ports = RuntimeConfigFile.parse(json).listeners();
        assertEquals(List.of(
            new ListenerSpec("mllp", 2575),
            new ListenerSpec("sender-01", 3001),
            // names that would collide under env-key normalization stay distinct here
            new ListenerSpec("sender.01", 3002)), ports);
    }

    @Test
    void skipsUnresolvedAndMalformedEntries() {
        String json = "{\"listenerPorts\":["
            + "  {\"name\":\"good\",\"port\":2575},"
            + "  {\"name\":\"noport\",\"port\":null},"   // unresolved -> skip
            + "  {\"name\":\"\",\"port\":4000},"         // blank name -> skip
            + "  {\"port\":4001},"                       // no name -> skip
            + "  \"garbage\""                            // not an object -> skip
            + "]}";
        assertEquals(List.of(new ListenerSpec("good", 2575)), RuntimeConfigFile.parse(json).listeners());
    }

    @Test
    void toleratesMissingArrayAndJunk() {
        assertTrue(RuntimeConfigFile.parse("{}").listeners().isEmpty());
        assertTrue(RuntimeConfigFile.parse("{\"listenerPorts\":\"nope\"}").listeners().isEmpty());
        assertTrue(RuntimeConfigFile.parse("null").listeners().isEmpty());
    }
}

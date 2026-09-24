package com.zerobias.module.x12;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extracting the opaque {@code config} block from a runtime-config file, whether the
 * node's JSON {@code DeploymentRuntimeConfig} or the image's {@code runtimeConfig.yml}.
 * Only {@code config} is read — there are no listener ports in this module.
 */
class RuntimeConfigFileTest {

    @Test
    void readsConfigFromNodeJson() {
        RuntimeConfigFile f = RuntimeConfigFile.parse(
            "{\"daemonMode\":true,\"durability\":[{\"volumeName\":\"x12-buffer\"}],"
            + "\"config\":{\"consumedSuffix\":\".done\",\"sources\":[{\"name\":\"a\",\"path\":\"/a\"}]}}");
        assertEquals(".done", f.config().get("consumedSuffix").getAsString());
        assertEquals(1, f.config().getAsJsonArray("sources").size());
    }

    @Test
    void readsConfigFromRuntimeConfigYml() {
        String yml = "# comment\ndaemonMode: true\nresources:\n  memoryMb: 1024\n"
            + "config:\n  sources:\n    - name: inbox\n      path: /var/lib/x12/inbox\n"
            + "      pattern: \"*.{x12,edi,txt,835,837,277,999,dat}\"\n      pollIntervalSec: 30\n"
            + "      stableForSec: 60\n  consumedSuffix: \".done\"\n  errorSuffix: \".error\"\n"
            + "  ackDurability: normal\n  retention:\n    maxBytes: 10737418240   # 10 GiB\n    maxAge: P90D\n";
        RuntimeConfigFile f = RuntimeConfigFile.parse(yml);
        assertEquals("*.{x12,edi,txt,835,837,277,999,dat}",
            f.config().getAsJsonArray("sources").get(0).getAsJsonObject().get("pattern").getAsString());
        assertEquals(10737418240L, f.config().getAsJsonObject("retention").get("maxBytes").getAsLong());
        assertEquals("P90D", f.config().getAsJsonObject("retention").get("maxAge").getAsString());
        // the whole document round-trips through ModuleRuntimeConfig
        ModuleRuntimeConfig c = ModuleRuntimeConfig.fromConfigObject(f.config());
        assertEquals("inbox", c.sources().get(0).name());
        assertEquals(java.time.Duration.ofDays(90), c.retention().maxAge());
    }

    @Test
    void aMissingConfigBlockIsEmptyButABrokenOneFails(@TempDir Path dir) throws Exception {
        assertEquals(0, RuntimeConfigFile.parse("{}").config().size());
        assertEquals(0, RuntimeConfigFile.parse("daemonMode: true\n").config().size());
        assertEquals(0, RuntimeConfigFile.parse("").config().size());
        assertThrows(ModuleRuntimeConfig.InvalidConfigException.class,
            () -> RuntimeConfigFile.parse("{\"config\":\"nope\"}"));
        assertThrows(ModuleRuntimeConfig.InvalidConfigException.class, () -> RuntimeConfigFile.parse("- a\n- b\n"));
        assertThrows(RuntimeException.class, () -> RuntimeConfigFile.parse("{\"config\":"));

        assertTrue(RuntimeConfigFile.loadPath(Path.of("/definitely/missing.yml")).isEmpty(), "absent, not broken");
        Path garbage = Files.writeString(dir.resolve("runtimeConfig.yml"), "config: [unclosed\n");
        ModuleRuntimeConfig.InvalidConfigException e = assertThrows(ModuleRuntimeConfig.InvalidConfigException.class,
            () -> RuntimeConfigFile.loadPath(garbage));
        assertTrue(e.getMessage().contains(garbage.toString()), e.getMessage());
    }

    @Test
    void theShippedRuntimeConfigYmlPassesStrictParsing() {
        // The image falls back to this file when MODULE_CONFIG is absent; a key the parser
        // rejects would stop every bare `docker run`.
        ModuleRuntimeConfig c = ModuleRuntimeConfig.fromConfigObject(
            RuntimeConfigFile.loadPath(Path.of("../runtimeConfig.yml")).orElseThrow().config());
        assertTrue(c.fullDurability());
        assertEquals(ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES, c.maxFileBytes());
        assertEquals("/var/lib/x12/inbox", c.sources().get(0).path());
    }
}

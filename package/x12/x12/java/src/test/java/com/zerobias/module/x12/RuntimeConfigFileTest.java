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
 * Only {@code config} is read — there are no listener ports in this module. A missing file is
 * absent (the next channel is tried); a present one that cannot be used is fatal.
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
    void aMissingConfigBlockIsEmptyAndAMissingFileIsAbsent() {
        assertEquals(0, RuntimeConfigFile.parse("{}").config().size());
        assertEquals(0, RuntimeConfigFile.parse("{\"config\":null}").config().size());
        assertEquals(0, RuntimeConfigFile.parse("daemonMode: true\n").config().size());
        assertEquals(0, RuntimeConfigFile.parse("").config().size());
        assertTrue(RuntimeConfigFile.loadPath(java.nio.file.Path.of("/definitely/missing.yml")).isEmpty());
    }

    @Test
    void aFileThatIsPresentButBrokenFailsInsteadOfBeingIgnored(@TempDir Path dir) throws Exception {
        // Ignoring it ran the daemon on defaults nobody wrote, with nothing to show for it.
        assertThrows(ModuleRuntimeConfig.InvalidConfigException.class, () -> RuntimeConfigFile.parse("{\"config\":\"nope\"}"));
        assertThrows(ModuleRuntimeConfig.InvalidConfigException.class, () -> RuntimeConfigFile.parse("[1,2]"));
        assertThrows(RuntimeException.class, () -> RuntimeConfigFile.parse("{\"config\":"));
        for (String junk : new String[] {"{\"config\":", "config: [unclosed\n", "config: 7\n"}) {
            Path f = Files.writeString(dir.resolve("runtime-" + junk.length() + ".json"), junk);
            ModuleRuntimeConfig.InvalidConfigException e = assertThrows(ModuleRuntimeConfig.InvalidConfigException.class,
                () -> RuntimeConfigFile.loadPath(f), junk);
            assertTrue(e.getMessage().contains(f.toString()), "names the file: " + e.getMessage());
        }
    }
}

package com.zerobias.module.x12;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parsing of the opaque MODULE_CONFIG (DESIGN §3, §4.1) and its resolution chain:
 * env → runtime config file (JSON or the image's runtimeConfig.yml) → defaults.
 * Malformed input must degrade to safe defaults, never crash; directory validation
 * is separate and fatal by design.
 */
class ModuleRuntimeConfigTest {

    private static final String FULL = "{"
        + "\"sources\":[{\"name\":\"payer-a\",\"path\":\"/in/a\",\"pattern\":\"*.835\",\"pollIntervalSec\":5,\"stableForSec\":2},"
        + "             {\"name\":\"payer-b\",\"path\":\"/in/b\"}],"
        + "\"consumedSuffix\":\".ok\",\"errorSuffix\":\".bad\","
        + "\"ackDurability\":\"full\","
        + "\"retention\":{\"maxBytes\":10737418240,\"maxAge\":\"P90D\"},"
        + "\"allowBareTransactionSets\":true,"
        + "\"allowFileManagement\":true}";

    @Test
    void absentOrEmptyMeansDefaults() {
        for (String s : new String[] {null, "", "{}", "not json", "[1,2]"}) {
            ModuleRuntimeConfig c = ModuleRuntimeConfig.parse(s);
            assertEquals(1, c.sources().size(), "default source for input: " + s);
            assertEquals("inbox", c.sources().get(0).name());
            assertEquals("/var/lib/x12/inbox", c.sources().get(0).path());
            assertEquals(30, c.sources().get(0).pollIntervalSec());
            assertEquals(60, c.sources().get(0).stableForSec());
            assertEquals(".done", c.consumedSuffix());
            assertEquals(".error", c.errorSuffix());
            assertTrue(c.fullDurability(), "ackDurability defaults to full (fsync per commit)");
            assertFalse(c.retention().isBounded());
            assertFalse(c.allowBareTransactionSets());
            assertFalse(c.allowFileManagement(), "file management is opt-in, never a default");
        }
    }

    @Test
    void parsesEveryField() {
        ModuleRuntimeConfig c = ModuleRuntimeConfig.parse(FULL);
        assertEquals(List.of(
            new SourceConfig("payer-a", "/in/a", "*.835", 5, 2),
            new SourceConfig("payer-b", "/in/b", "*", 30, 60)), c.sources(), "per-source defaults applied");
        assertEquals(".ok", c.consumedSuffix());
        assertEquals(".bad", c.errorSuffix());
        assertTrue(c.fullDurability());
        assertEquals(10737418240L, c.retention().maxBytes());
        assertEquals(Duration.ofDays(90), c.retention().maxAge());
        assertTrue(c.allowBareTransactionSets());
        assertTrue(c.allowFileManagement());
    }

    @Test
    void fileManagementIsOnlyEnabledByALiteralTrue() {
        // A typo, a string, or a missing key must all leave the write surface shut: this is
        // the flag that decides whether an API caller can put files on the volume.
        for (String s : new String[] {"{}", "{\"allowFileManagement\":false}", "{\"allowFileManagement\":\"true\"}",
                "{\"allowFileManagement\":1}", "{\"allowFileManagment\":true}", "{\"allowFileManagement\":null}"}) {
            assertFalse(ModuleRuntimeConfig.parse(s).allowFileManagement(), s);
        }
        assertTrue(ModuleRuntimeConfig.parse("{\"allowFileManagement\":true}").allowFileManagement());
    }

    @Test
    void ackDurabilityIsCaseInsensitiveAndOnlyAnExplicitNormalWeakensIt() {
        assertTrue(ModuleRuntimeConfig.parse("{\"ackDurability\":\"FULL\"}").fullDurability());
        assertFalse(ModuleRuntimeConfig.parse("{\"ackDurability\":\"normal\"}").fullDurability());
        assertFalse(ModuleRuntimeConfig.parse("{\"ackDurability\":\"NORMAL\"}").fullDurability());
        assertTrue(ModuleRuntimeConfig.parse("{\"ackDurability\":\"bogus\"}").fullDurability(), "unknown keeps full");
        assertTrue(ModuleRuntimeConfig.parse("{\"sources\":[]}").fullDurability(), "absent means full");
    }

    @Test
    void theImageDefaultsAreFullDurability() {
        // The shipped runtimeConfig.yml is what a deployment without MODULE_CONFIG runs with.
        ModuleRuntimeConfig c = ModuleRuntimeConfig.resolve(Map.of(), Path.of("../runtimeConfig.yml").toString());
        assertTrue(c.fullDurability(), "runtimeConfig.yml ackDurability");
        assertEquals(ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES, c.maxFileBytes());
    }

    @Test
    void malformedSourceEntriesAreSkippedAndEmptyListFallsBackToDefault() {
        ModuleRuntimeConfig c = ModuleRuntimeConfig.parse(
            "{\"sources\":[{\"name\":\"ok\",\"path\":\"/x\"},{\"name\":\"nopath\"},\"junk\",{\"path\":\"/noname\"}]}");
        assertEquals(List.of(new SourceConfig("ok", "/x", "*", 30, 60)), c.sources());

        ModuleRuntimeConfig empty = ModuleRuntimeConfig.parse("{\"sources\":[]}");
        assertEquals("inbox", empty.sources().get(0).name(), "empty sources[] → default source");
        ModuleRuntimeConfig wrongType = ModuleRuntimeConfig.parse("{\"sources\":\"nope\"}");
        assertEquals("inbox", wrongType.sources().get(0).name());
    }

    @Test
    void badMaxAgeDisablesOnlyAgeAxis() {
        ModuleRuntimeConfig c = ModuleRuntimeConfig.parse(
            "{\"consumedSuffix\":\".x\",\"retention\":{\"maxBytes\":2048,\"maxAge\":\"90 days\"}}");
        assertNull(c.retention().maxAge());
        assertEquals(2048L, c.retention().maxBytes());
        assertEquals(".x", c.consumedSuffix(), "bad maxAge must not discard the rest");
    }

    @Test
    void resolveOrderIsEnvThenFileThenDefaults(@TempDir Path dir) throws Exception {
        Path yml = dir.resolve("runtimeConfig.yml");
        Files.writeString(yml, "daemonMode: true\nconfig:\n  consumedSuffix: \".yml-done\"\n"
            + "  sources:\n    - name: yml\n      path: /from/yml\n      pattern: \"*.{x12,edi}\"\n"
            + "      pollIntervalSec: 7\n      stableForSec: 3\n  retention:\n    maxAge: P7D\n");
        Path json = dir.resolve("runtime.json");
        Files.writeString(json, "{\"listenerPorts\":[],\"config\":{\"consumedSuffix\":\".json-done\"}}");

        // 1. MODULE_CONFIG wins over everything.
        ModuleRuntimeConfig env = ModuleRuntimeConfig.resolve(
            Map.of("MODULE_CONFIG", "{\"consumedSuffix\":\".env-done\"}", "RUNTIME_CONFIG_FILE", json.toString()),
            yml.toString());
        assertEquals(".env-done", env.consumedSuffix());

        // 2. RUNTIME_CONFIG_FILE (node JSON) before the image yml.
        ModuleRuntimeConfig file = ModuleRuntimeConfig.resolve(
            Map.of("RUNTIME_CONFIG_FILE", json.toString()), yml.toString());
        assertEquals(".json-done", file.consumedSuffix());

        // 3. The image's runtimeConfig.yml `config:` block (dev fallback).
        ModuleRuntimeConfig fromYml = ModuleRuntimeConfig.resolve(Map.of(), yml.toString());
        assertEquals(".yml-done", fromYml.consumedSuffix());
        assertEquals(List.of(new SourceConfig("yml", "/from/yml", "*.{x12,edi}", 7, 3)), fromYml.sources());
        assertEquals(Duration.ofDays(7), fromYml.retention().maxAge());

        // 4. Nothing present → defaults.
        ModuleRuntimeConfig dflt = ModuleRuntimeConfig.resolve(Map.of(), dir.resolve("missing.yml").toString());
        assertEquals(".done", dflt.consumedSuffix());
        // An unreadable RUNTIME_CONFIG_FILE falls through to the yml, never crashes.
        ModuleRuntimeConfig badPointer = ModuleRuntimeConfig.resolve(
            Map.of("RUNTIME_CONFIG_FILE", dir.resolve("nope.json").toString()), yml.toString());
        assertEquals(".yml-done", badPointer.consumedSuffix());
    }

    @Test
    void validateSourcesReportsMissingDuplicateAndSuffixProblems(@TempDir Path dir) throws Exception {
        Path good = Files.createDirectory(dir.resolve("good"));
        Path file = Files.writeString(dir.resolve("notadir"), "x");
        ModuleRuntimeConfig ok = new ModuleRuntimeConfig(
            List.of(new SourceConfig("a", good.toString(), "*", 1, 0)), ".done", ".error", false,
            com.zerobias.module.x12.buffer.RetentionConfig.none(), false, false);
        assertEquals(List.of(), ok.validateSources());

        ModuleRuntimeConfig bad = new ModuleRuntimeConfig(
            List.of(new SourceConfig("a", good.toString(), "*", 1, 0),
                    new SourceConfig("a", dir.resolve("missing").toString(), "*", 1, 0),
                    new SourceConfig("b", file.toString(), "*", 1, 0)),
            ".same", ".same", false, com.zerobias.module.x12.buffer.RetentionConfig.none(), false, false);
        List<String> problems = bad.validateSources();
        assertTrue(problems.stream().anyMatch(p -> p.contains("duplicate source name: a")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("does not exist")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("not a directory")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("must differ")), problems.toString());
        // the probe leaves nothing behind in the good dir
        try (var s = Files.list(good)) {
            assertEquals(0, s.count(), "writability probe cleaned up");
        }
    }

    @Test
    void twoSourcesOnTheSameRealDirectoryAreRejected(@TempDir Path dir) throws Exception {
        Path in = Files.createDirectory(dir.resolve("in"));
        Path link = Files.createSymbolicLink(dir.resolve("alias"), in);
        Path nested = Files.createDirectory(in.resolve("nested"));
        for (String other : new String[] {link.toString(), in + "/", in + "/nested/.."}) {
            ModuleRuntimeConfig c = new ModuleRuntimeConfig(
                List.of(new SourceConfig("a", in.toString(), "*", 1, 0), new SourceConfig("b", other, "*", 1, 0)),
                ".done", ".error", false, com.zerobias.module.x12.buffer.RetentionConfig.none(), false, false);
            List<String> problems = c.validateSources();
            assertTrue(problems.stream().anyMatch(p -> p.contains("'a' and 'b' point at the same directory")),
                other + " -> " + problems);
        }
        ModuleRuntimeConfig nestedOk = new ModuleRuntimeConfig(
            List.of(new SourceConfig("a", in.toString(), "*", 1, 0), new SourceConfig("b", nested.toString(), "*", 1, 0)),
            ".done", ".error", false, com.zerobias.module.x12.buffer.RetentionConfig.none(), false, false);
        assertEquals(List.of(), nestedOk.validateSources(), "nested is fine: each poller scans flat");
    }
}

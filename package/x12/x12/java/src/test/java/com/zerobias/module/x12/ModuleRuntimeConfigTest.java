package com.zerobias.module.x12;

import com.zerobias.module.x12.ModuleRuntimeConfig.InvalidConfigException;
import com.zerobias.module.x12.buffer.RetentionConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parsing of the opaque MODULE_CONFIG (DESIGN §3, §4.1) and its resolution chain:
 * env → runtime config file (JSON or the image's runtimeConfig.yml) → defaults.
 * Defaults apply only when no config is present; a present config that is malformed, carries
 * an unknown key or a wrong-typed or out-of-range value fails the boot. Directory validation
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
        + "\"allowFileManagement\":true,"
        + "\"maxFileBytes\":1048576}";

    @Test
    void absentOrEmptyMeansDefaults() {
        for (String s : new String[] {null, "", "  ", "{}"}) {
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
            assertEquals(ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES, c.maxFileBytes());
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
        assertEquals(1048576L, c.maxFileBytes());
    }

    @Test
    void fileManagementIsOnlyEnabledByALiteralTrue() {
        // Absent or false leaves the write surface shut: this is the flag that decides whether
        // an API caller can put files on the volume.
        for (String s : new String[] {"{}", "{\"allowFileManagement\":false}"}) {
            assertFalse(ModuleRuntimeConfig.parse(s).allowFileManagement(), s);
        }
        // A string, a number, a null or a misspelt key is a mistake: the boot stops, so the
        // surface never opens on one (and the operator learns the flag did not take).
        for (String s : new String[] {"{\"allowFileManagement\":\"true\"}", "{\"allowFileManagement\":1}",
                "{\"allowFileManagment\":true}", "{\"allowFileManagement\":null}"}) {
            assertThrows(InvalidConfigException.class, () -> ModuleRuntimeConfig.parse(s), s);
        }
        assertTrue(ModuleRuntimeConfig.parse("{\"allowFileManagement\":true}").allowFileManagement());
    }

    @Test
    void ackDurabilityIsCaseInsensitiveAndAnythingElseIsRefused() {
        assertTrue(ModuleRuntimeConfig.parse("{\"ackDurability\":\"FULL\"}").fullDurability());
        assertFalse(ModuleRuntimeConfig.parse("{\"ackDurability\":\"normal\"}").fullDurability());
        assertFalse(ModuleRuntimeConfig.parse("{\"ackDurability\":\"NORMAL\"}").fullDurability());
        assertTrue(ModuleRuntimeConfig.parse("{\"consumedSuffix\":\".ok\"}").fullDurability(), "absent means full");
        // Before, an unknown value quietly meant full; refusing it is what tells the operator.
        assertThrows(InvalidConfigException.class, () -> ModuleRuntimeConfig.parse("{\"ackDurability\":\"bogus\"}"));
        assertThrows(InvalidConfigException.class, () -> ModuleRuntimeConfig.parse("{\"ackDurability\":false}"));
    }

    @Test
    void malformedConfigFailsInsteadOfFallingBackToDefaults() {
        for (String bad : new String[] {
            "not json",
            "{\"sources\":",
            "[1,2]",
            "\"a string\"",
            "{\"sources\":[{\"name\":\"ok\",\"path\":\"/x\"},{\"name\":\"nopath\"}]}",
            "{\"sources\":[\"junk\"]}",
            "{\"sources\":[{\"path\":\"/noname\"}]}",
            "{\"sources\":[{\"name\":\" \",\"path\":\"/x\"}]}",
            "{\"sources\":[]}",
            "{\"sources\":\"nope\"}",
            "{\"sources\":[{\"name\":\"a\",\"path\":\"/a\"},{\"name\":\"a\",\"path\":\"/b\"}]}",
            "{\"sources\":[{\"name\":\"a\",\"path\":\"/a\",\"pollIntervalSec\":\"30\"}]}",
            "{\"sources\":[{\"name\":\"a\",\"path\":\"/a\",\"pollIntervalSec\":0}]}",
            "{\"sources\":[{\"name\":\"a\",\"path\":\"/a\",\"pollIntervalSec\":1.5}]}",
            "{\"sources\":[{\"name\":\"a\",\"path\":\"/a\",\"pollIntervalSec\":4294967296}]}",
            "{\"sources\":[{\"name\":\"a\",\"path\":\"/a\",\"stableForSec\":-1}]}",
            "{\"sources\":[{\"name\":\"a\",\"path\":\"/a\",\"pattern\":\"*.{x12\"}]}",
            "{\"sources\":[{\"name\":\"a\",\"path\":\"/a\",\"pattern\":\"\"}]}",
            "{\"sources\":[{\"name\":\"a\",\"path\":\"/a\",\"patern\":\"*\"}]}",
            "{\"consumedSuffix\":7}",
            "{\"errorSuffix\":\"\"}",
            "{\"consumedSuffix\":\"/../x\"}",
            "{\"allowBareTransactionSets\":\"yes\"}",
            "{\"maxFileBytes\":0}",
            "{\"maxFileBytes\":\"64MB\"}",
            "{\"maxFileBytes\":" + Long.MAX_VALUE + "}",
            "{\"retention\":\"P90D\"}",
            "{\"retention\":null}",
            "{\"retention\":{\"maxBytes\":\"10GB\"}}",
            "{\"retention\":{\"maxBytes\":0}}",
            "{\"retention\":{\"maxAge\":\"90 days\"}}",
            "{\"retention\":{\"maxAge\":\"PT0S\"}}",
            "{\"retention\":{\"maxAge\":\"P90D\",\"maxbytes\":1}}",
            "{\"retension\":{\"maxAge\":\"P90D\"}}",
        }) {
            InvalidConfigException e = assertThrows(InvalidConfigException.class, () -> ModuleRuntimeConfig.parse(bad), bad);
            assertTrue(e.getMessage().startsWith("invalid module config: "), e.getMessage());
        }
    }

    @Test
    void aTypoedRetentionFailsTheBootInsteadOfDisablingEviction() {
        // The case that motivated fail-fast: before, each of these parsed to "unbounded".
        assertTrue(ModuleRuntimeConfig.parse("{\"retention\":{\"maxBytes\":2048,\"maxAge\":\"P7D\"}}").retention()
            .isBounded());
        for (String typo : new String[] {"{\"retention\":{\"max_age\":\"P7D\"}}", "{\"Retention\":{\"maxAge\":\"P7D\"}}",
                "{\"retention\":{\"maxAge\":\"7d\"}}", "{\"retention\":[{\"maxAge\":\"P7D\"}]}"}) {
            InvalidConfigException e = assertThrows(InvalidConfigException.class, () -> ModuleRuntimeConfig.parse(typo));
            assertTrue(e.getMessage().contains("etention"), e.getMessage());
        }
    }

    @Test
    void typeErrorsNameTheTypeNotTheValue() {
        // A value in the wrong place may be anything; the log gets its JSON type only.
        InvalidConfigException e = assertThrows(InvalidConfigException.class,
            () -> ModuleRuntimeConfig.parse("{\"allowFileManagement\":\"s3cr3t-value\"}"));
        assertTrue(e.getMessage().contains("got a string"), e.getMessage());
        assertFalse(e.getMessage().contains("s3cr3t-value"), e.getMessage());
        InvalidConfigException bad = assertThrows(InvalidConfigException.class,
            () -> ModuleRuntimeConfig.parse("{\"sources\":[{\"name\":\"a\",\"path\":\"/a\"}],\"s3cr3t\":1"));
        assertFalse(bad.getMessage().contains("s3cr3t"), "a JSON syntax error reports a position: " + bad.getMessage());
    }

    @Test
    void nestedSourcesAreFineAtLoad() {
        // Each poller scans its own directory flat; only the same directory is a clash (validateSources).
        assertEquals(2, ModuleRuntimeConfig.parse(
            "{\"sources\":[{\"name\":\"a\",\"path\":\"/in\"},{\"name\":\"b\",\"path\":\"/in/b\"}]}").sources().size());
    }

    @Test
    void maxFileBytesIsBounded() {
        List<SourceConfig> one = List.of(new SourceConfig("a", "/a", "*", 1, 0));
        assertEquals(ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES,
            new ModuleRuntimeConfig(one, ".done", ".error", true, RetentionConfig.none(), false, false).maxFileBytes());
        assertEquals(ModuleRuntimeConfig.MAX_MAX_FILE_BYTES, ModuleRuntimeConfig.parse(
            "{\"maxFileBytes\":" + ModuleRuntimeConfig.MAX_MAX_FILE_BYTES + "}").maxFileBytes(), "the cap itself is allowed");
        for (long bad : new long[] {0, -1, ModuleRuntimeConfig.MAX_MAX_FILE_BYTES + 1}) {
            assertThrows(InvalidConfigException.class, () -> new ModuleRuntimeConfig(one, ".done", ".error", true,
                RetentionConfig.none(), false, false, bad), "constructed with " + bad);
            assertThrows(InvalidConfigException.class, () -> ModuleRuntimeConfig.parse("{\"maxFileBytes\":" + bad + "}"),
                "parsed " + bad);
        }
    }

    @Test
    void everyKeyTheShippedConfigCarriesIsKnown() throws Exception {
        // The image's runtimeConfig.yml is what gradle's testDocker injects as MODULE_CONFIG.
        RuntimeConfigFile shipped = RuntimeConfigFile.loadPath(Path.of("../runtimeConfig.yml")).orElseThrow();
        assertTrue(ModuleRuntimeConfig.KEYS.containsAll(shipped.config().keySet()), shipped.config().keySet().toString());
        ModuleRuntimeConfig c = ModuleRuntimeConfig.fromConfigObject(shipped.config());
        assertTrue(c.retention().isBounded(), "the shipped retention parses");
        assertFalse(c.allowFileManagement());
    }

    @Test
    void theImageDefaultsAreFullDurability() {
        // The shipped runtimeConfig.yml is what a deployment without MODULE_CONFIG runs with.
        ModuleRuntimeConfig c = ModuleRuntimeConfig.resolve(Map.of(), Path.of("../runtimeConfig.yml").toString());
        assertTrue(c.fullDurability(), "runtimeConfig.yml ackDurability");
        assertEquals(ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES, c.maxFileBytes());
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
        // A RUNTIME_CONFIG_FILE that does not exist is absent: fall through to the yml.
        ModuleRuntimeConfig badPointer = ModuleRuntimeConfig.resolve(
            Map.of("RUNTIME_CONFIG_FILE", dir.resolve("nope.json").toString()), yml.toString());
        assertEquals(".yml-done", badPointer.consumedSuffix());
    }

    @Test
    void presentButBrokenConfigFailsWhicheverChannelItCameThrough(@TempDir Path dir) throws Exception {
        Path garbage = Files.writeString(dir.resolve("runtime.json"), "{\"config\":");
        assertThrows(InvalidConfigException.class, () -> ModuleRuntimeConfig.resolve(
            Map.of("RUNTIME_CONFIG_FILE", garbage.toString()), dir.resolve("missing.yml").toString()));
        Path wrongType = Files.writeString(dir.resolve("runtimeConfig.yml"), "config:\n  maxFileBytes: lots\n");
        assertThrows(InvalidConfigException.class, () -> ModuleRuntimeConfig.resolve(Map.of(), wrongType.toString()));
        assertThrows(InvalidConfigException.class, () -> ModuleRuntimeConfig.resolve(
            Map.of("MODULE_CONFIG", "{\"ackDurability\":\"sometimes\"}"), wrongType.toString()),
            "MODULE_CONFIG is checked on its own merits");
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

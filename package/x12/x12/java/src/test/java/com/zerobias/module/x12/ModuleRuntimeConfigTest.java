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
 * Defaults apply only when no config is present; a present-but-malformed config fails
 * the boot. Directory validation is separate and fatal by design.
 */
class ModuleRuntimeConfigTest {

    private static final String FULL = "{"
        + "\"sources\":[{\"name\":\"payer-a\",\"path\":\"/in/a\",\"pattern\":\"*.835\",\"pollIntervalSec\":5,\"stableForSec\":2},"
        + "             {\"name\":\"payer-b\",\"path\":\"/in/b\"}],"
        + "\"consumedSuffix\":\".ok\",\"errorSuffix\":\".bad\","
        + "\"ackDurability\":\"normal\","
        + "\"maxFileBytes\":1048576,"
        + "\"retention\":{\"maxBytes\":10737418240,\"maxAge\":\"P90D\"},"
        + "\"allowBareTransactionSets\":true,"
        + "\"allowFileManagement\":true}";

    private static ModuleRuntimeConfig config(List<SourceConfig> sources) {
        return new ModuleRuntimeConfig(sources, ".done", ".error", true, RetentionConfig.none(), false, ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES);
    }

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
            assertTrue(c.fullDurability(), "the rename-is-the-ack guarantee needs fsync per commit");
            assertEquals(64L * 1024 * 1024, c.maxFileBytes());
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
        assertFalse(c.fullDurability());
        assertEquals(1048576L, c.maxFileBytes());
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
    void ackDurabilityIsCaseInsensitiveAndAnythingElseIsRejected() {
        assertTrue(ModuleRuntimeConfig.parse("{\"ackDurability\":\"FULL\"}").fullDurability());
        assertFalse(ModuleRuntimeConfig.parse("{\"ackDurability\":\"Normal\"}").fullDurability());
        assertThrows(InvalidConfigException.class, () -> ModuleRuntimeConfig.parse("{\"ackDurability\":\"bogus\"}"));
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
            "{\"sources\":[]}",
            "{\"sources\":\"nope\"}",
            "{\"sources\":[{\"name\":\"a\",\"path\":\"/a\",\"pollIntervalSec\":\"30\"}]}",
            "{\"sources\":[{\"name\":\"a\",\"path\":\"/a\",\"pollIntervalSec\":0}]}",
            "{\"sources\":[{\"name\":\"a\",\"path\":\"/a\",\"pollIntervalSec\":1.5}]}",
            "{\"sources\":[{\"name\":\"a\",\"path\":\"/a\",\"stableForSec\":-1}]}",
            "{\"sources\":[{\"name\":\"a\",\"path\":\"/a\",\"pattern\":\"*.{x12\"}]}",
            "{\"sources\":[{\"name\":\"a\",\"path\":\"/a\",\"patern\":\"*\"}]}",
            "{\"consumedSuffix\":7}",
            "{\"errorSuffix\":\"\"}",
            "{\"consumedSuffix\":\"/../x\"}",
            "{\"allowBareTransactionSets\":\"yes\"}",
            "{\"maxFileBytes\":0}",
            "{\"maxFileBytes\":\"64MB\"}",
            "{\"maxFileBytes\":2147483648}",
            "{\"retention\":\"P90D\"}",
            "{\"retention\":{\"maxBytes\":\"10GB\"}}",
            "{\"retention\":{\"maxAge\":\"90 days\"}}",
            "{\"retention\":{\"maxAge\":\"PT0S\"}}",
            "{\"retension\":{\"maxAge\":\"P90D\"}}",
        }) {
            assertThrows(InvalidConfigException.class, () -> ModuleRuntimeConfig.parse(bad), bad);
        }
    }

    @Test
    void duplicateNamesAndSameOrNestedPathsAreRejectedAtLoad() {
        assertThrows(InvalidConfigException.class, () -> ModuleRuntimeConfig.parse(
            "{\"sources\":[{\"name\":\"a\",\"path\":\"/in/a\"},{\"name\":\"a\",\"path\":\"/in/b\"}]}"));
        assertThrows(InvalidConfigException.class, () -> ModuleRuntimeConfig.parse(
            "{\"sources\":[{\"name\":\"a\",\"path\":\"/in/a\"},{\"name\":\"b\",\"path\":\"/in/x/../a/\"}]}"),
            "same directory once normalized");
        assertThrows(InvalidConfigException.class, () -> ModuleRuntimeConfig.parse(
            "{\"sources\":[{\"name\":\"a\",\"path\":\"/in\"},{\"name\":\"b\",\"path\":\"/in/b\"}]}"), "nested");
        assertEquals(2, ModuleRuntimeConfig.parse(
            "{\"sources\":[{\"name\":\"a\",\"path\":\"/in/a\"},{\"name\":\"b\",\"path\":\"/in/ab\"}]}").sources().size(),
            "a shared name prefix is not nesting");
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
        ModuleRuntimeConfig missingPointer = ModuleRuntimeConfig.resolve(
            Map.of("RUNTIME_CONFIG_FILE", dir.resolve("nope.json").toString()), yml.toString());
        assertEquals(".yml-done", missingPointer.consumedSuffix());
    }

    @Test
    void presentButBrokenConfigFailsWhicheverChannelItCameThrough(@TempDir Path dir) throws Exception {
        Path garbage = Files.writeString(dir.resolve("runtime.json"), "{\"config\":");
        assertThrows(InvalidConfigException.class, () -> ModuleRuntimeConfig.resolve(
            Map.of("RUNTIME_CONFIG_FILE", garbage.toString()), dir.resolve("missing.yml").toString()));
        Path wrongType = Files.writeString(dir.resolve("runtimeConfig.yml"), "config:\n  maxFileBytes: lots\n");
        assertThrows(InvalidConfigException.class, () -> ModuleRuntimeConfig.resolve(Map.of(), wrongType.toString()));
        assertThrows(InvalidConfigException.class, () -> ModuleRuntimeConfig.resolve(
            Map.of("MODULE_CONFIG", "{\"ackDurability\":\"sometimes\"}"), wrongType.toString()));
    }

    @Test
    void maxFileBytesIsBounded() {
        List<SourceConfig> one = List.of(new SourceConfig("a", "/a", "*", 1, 0));
        assertThrows(InvalidConfigException.class, () -> new ModuleRuntimeConfig(one, ".done", ".error", true,
            RetentionConfig.none(), false, 0));
        assertThrows(InvalidConfigException.class, () -> new ModuleRuntimeConfig(one, ".done", ".error", true,
            RetentionConfig.none(), false, ModuleRuntimeConfig.MAX_MAX_FILE_BYTES + 1));
        assertEquals(ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES, config(one).maxFileBytes());
        // One transaction set's typed JSON runs 3–4½× its X12 and SQLite stores no value over 1e9 bytes.
        assertEquals(128L * 1024 * 1024, ModuleRuntimeConfig.MAX_MAX_FILE_BYTES);
        assertThrows(InvalidConfigException.class,
            () -> ModuleRuntimeConfig.parse("{\"maxFileBytes\":" + (256L * 1024 * 1024) + "}"));
    }

    @Test
    void theBootProbeRenamesWithTheConfiguredSuffixes(@TempDir Path dir) throws Exception {
        Path good = Files.createDirectory(dir.resolve("good"));
        List<SourceConfig> one = List.of(new SourceConfig("a", good.toString(), "*", 1, 0));
        assertEquals(List.of(), new ModuleRuntimeConfig(one, ".ok", ".bad", true, RetentionConfig.none(), false, ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES)
            .validateSources());
        // A suffix no file name can carry (past the filesystem's 255-byte name limit) fails at boot,
        // not on the first file every scan.
        String tooLong = "." + "x".repeat(250);
        for (ModuleRuntimeConfig c : List.of(
                new ModuleRuntimeConfig(one, tooLong, ".error", true, RetentionConfig.none(), false, ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES),
                new ModuleRuntimeConfig(one, ".done", tooLong, true, RetentionConfig.none(), false, ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES))) {
            List<String> problems = c.validateSources();
            assertEquals(1, problems.size(), problems.toString());
            assertTrue(problems.get(0).contains("is not writable/renameable")
                && problems.get(0).contains("'" + tooLong + "'"), problems.get(0));
        }
        try (var s = Files.list(good)) {
            assertEquals(0, s.count(), "the probe cleans up after a failed rename too");
        }
    }

    @Test
    void validateSourcesReportsMissingDuplicateAndSuffixProblems(@TempDir Path dir) throws Exception {
        Path good = Files.createDirectory(dir.resolve("good"));
        Path file = Files.writeString(dir.resolve("notadir"), "x");
        ModuleRuntimeConfig ok = config(List.of(new SourceConfig("a", good.toString(), "*", 1, 0)));
        assertEquals(List.of(), ok.validateSources());

        ModuleRuntimeConfig bad = new ModuleRuntimeConfig(
            List.of(new SourceConfig("a", good.toString(), "*", 1, 0),
                    new SourceConfig("a", dir.resolve("missing").toString(), "*", 1, 0),
                    new SourceConfig("b", file.toString(), "*", 1, 0)),
            ".same", ".same", false, RetentionConfig.none(), false, ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES);
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
    void validateSourcesCatchesTwoSourcesAliasingOneDirectoryThroughASymlink(@TempDir Path dir) throws Exception {
        Path real = Files.createDirectory(dir.resolve("real"));
        Path alias = Files.createSymbolicLink(dir.resolve("alias"), real);
        ModuleRuntimeConfig c = config(List.of(new SourceConfig("a", real.toString(), "*", 1, 0),
            new SourceConfig("b", alias.toString(), "*", 1, 0)));
        List<String> problems = c.validateSources();
        assertTrue(problems.stream().anyMatch(p -> p.contains("same or nested")), problems.toString());
    }
}

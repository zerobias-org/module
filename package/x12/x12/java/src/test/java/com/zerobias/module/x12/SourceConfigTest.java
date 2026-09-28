package com.zerobias.module.x12;

import com.zerobias.module.x12.buffer.RetentionConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Per-source defaults, the case-insensitive glob (DESIGN §4.1) and the boot probe (DESIGN §3). */
class SourceConfigTest {

    @Test
    void appliesDefaultsForBlankOrNonPositiveValues() {
        SourceConfig s = new SourceConfig("a", "/a", " ", 0, -1);
        assertEquals("*", s.pattern());
        assertEquals(30, s.pollIntervalSec());
        assertEquals(60, s.stableForSec());
        assertEquals(0, new SourceConfig("a", "/a", "*", 1, 0).stableForSec(), "zero stability is allowed (tests)");
    }

    @Test
    void requiresNameAndPath() {
        assertThrows(IllegalArgumentException.class, () -> new SourceConfig("", "/a", "*", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new SourceConfig("a", null, "*", 1, 1));
    }

    @Test
    void theBootProbeRenamesWithTheConfiguredSuffixes(@TempDir Path dir) throws Exception {
        Path in = Files.createDirectory(dir.resolve("in"));
        SourceConfig s = new SourceConfig("a", in.toString(), "*", 1, 0);
        assertNull(s.validate(".ok", ".bad"));
        // A suffix no file name can carry (past the 255-byte name limit): a probe hard-coded to
        // .done passed it, and then every consumed file failed its rename instead of the boot.
        String tooLong = "." + "x".repeat(250);
        for (String[] suffixes : new String[][] {{tooLong, ".error"}, {".done", tooLong}}) {
            String problem = s.validate(suffixes[0], suffixes[1]);
            assertNotNull(problem, String.join(" / ", suffixes));
            assertTrue(problem.contains("not writable/renameable") && problem.contains("'" + tooLong + "'"), problem);
        }
        try (var listing = Files.list(in)) {
            assertEquals(0, listing.count(), "the probe cleans up after a failed rename too");
        }
        // and it is what the boot validation runs, with the config's own suffixes
        ModuleRuntimeConfig c = new ModuleRuntimeConfig(List.of(s), ".done", tooLong, true, RetentionConfig.none(),
            false, false);
        assertEquals(1, c.validateSources().size(), c.validateSources().toString());
    }

    @Test
    void globIsCaseInsensitiveAndSupportsAlternation() {
        SourceConfig s = new SourceConfig("a", "/a", "*.{x12,edi,835}", 1, 1);
        assertTrue(s.matchesFileName("remit.835"));
        assertTrue(s.matchesFileName("REMIT.X12"));
        assertTrue(s.matchesFileName("claims.Edi"));
        assertFalse(s.matchesFileName("remit.835.done"));
        assertFalse(s.matchesFileName("notes.txt"));
    }
}

package com.zerobias.module.x12;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Per-source defaults and the case-insensitive glob (DESIGN §4.1). */
class SourceConfigTest {

    @Test
    void absentPatternDefaultsButInvalidValuesAreRejected() {
        assertEquals("*", new SourceConfig("a", "/a", null, 1, 1).pattern());
        assertEquals(0, new SourceConfig("a", "/a", "*", 1, 0).stableForSec(), "zero stability is allowed (tests)");
        assertThrows(IllegalArgumentException.class, () -> new SourceConfig("a", "/a", " ", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new SourceConfig("a", "/a", "*", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new SourceConfig("a", "/a", "*", 1, -1));
        assertThrows(IllegalArgumentException.class, () -> new SourceConfig("a", "/a", "*.{x12", 1, 1), "bad glob");
    }

    @Test
    void containsOnlyDirectEntriesOfTheDirectory() {
        SourceConfig s = new SourceConfig("a", "/var/lib/x12/inbox", "*", 1, 1);
        assertTrue(s.contains(Path.of("/var/lib/x12/inbox/remit.835")));
        assertTrue(s.contains(Path.of("/var/lib/x12/inbox/./remit.835.done")));
        assertFalse(s.contains(Path.of("/var/lib/x12/inbox/../secrets/key.pem")), "no climbing out");
        assertFalse(s.contains(Path.of("/var/lib/x12/inbox/sub/remit.835")), "pollers never descend");
        assertFalse(s.contains(Path.of("/var/lib/x12/inbox")));
        assertFalse(SourceConfig.isEntryOf(Path.of("/var/lib/x12/inbox"), null));
        assertTrue(SourceConfig.isEntryOf(Path.of("/var/lib/x12/inbox/"), Path.of("/var/lib/x12/inbox/a")));
    }

    @Test
    void requiresNameAndPath() {
        assertThrows(IllegalArgumentException.class, () -> new SourceConfig("", "/a", "*", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new SourceConfig("a", null, "*", 1, 1));
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

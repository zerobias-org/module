package com.zerobias.module.x12;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Per-source defaults and the case-insensitive glob (DESIGN §4.1). */
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
    void globIsCaseInsensitiveAndSupportsAlternation() {
        SourceConfig s = new SourceConfig("a", "/a", "*.{x12,edi,835}", 1, 1);
        assertTrue(s.matchesFileName("remit.835"));
        assertTrue(s.matchesFileName("REMIT.X12"));
        assertTrue(s.matchesFileName("claims.Edi"));
        assertFalse(s.matchesFileName("remit.835.done"));
        assertFalse(s.matchesFileName("notes.txt"));
    }
}

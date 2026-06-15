package com.zerobias.module.hl7.materializer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Toolchain-independent tests for HL7 value normalization (DESIGN §2.4, §5). */
class Hl7NormalizerTest {

    @Test
    void explicitNull() {
        assertTrue(Hl7Normalizer.isExplicitNull("\"\""));
        assertFalse(Hl7Normalizer.isExplicitNull("x"));
        assertFalse(Hl7Normalizer.isExplicitNull(""));
    }

    @Test
    void dates() {
        assertEquals("1980-01-01", Hl7Normalizer.normalizeDate("19800101"));
        assertEquals("1980-01", Hl7Normalizer.normalizeDate("198001"));
        assertEquals("1980", Hl7Normalizer.normalizeDate("1980"));
        assertNull(Hl7Normalizer.normalizeDate("  "));
        assertEquals("notadate", Hl7Normalizer.normalizeDate("notadate"));
    }

    @Test
    void dateTimes() {
        assertEquals("2026-05-29T10:30:00", Hl7Normalizer.normalizeDateTime("20260529103000"));
        assertEquals("2026-05-29T10:30:00.5", Hl7Normalizer.normalizeDateTime("20260529103000.5"));
        assertEquals("2026-05-29T10:30:00-05:00", Hl7Normalizer.normalizeDateTime("20260529103000-0500"));
        assertEquals("2026-05-29T10:30:00Z", Hl7Normalizer.normalizeDateTime("20260529103000+0000"));
        assertEquals("2026-05-29T10:30", Hl7Normalizer.normalizeDateTime("202605291030"));
        assertEquals("2026-05-29", Hl7Normalizer.normalizeDateTime("20260529"));
    }

    @Test
    void times() {
        assertEquals("10:30:00", Hl7Normalizer.normalizeTime("103000"));
        assertEquals("10:30", Hl7Normalizer.normalizeTime("1030"));
        assertEquals("10:30:00.25", Hl7Normalizer.normalizeTime("103000.25"));
        assertEquals("10:30:00-05:00", Hl7Normalizer.normalizeTime("103000-0500"));
    }

    @Test
    void escapes() {
        assertEquals("SMITH&JONES", Hl7Normalizer.decodeEscapes("SMITH\\T\\JONES"));
        assertEquals("a|b", Hl7Normalizer.decodeEscapes("a\\F\\b"));
        assertEquals("a^b", Hl7Normalizer.decodeEscapes("a\\S\\b"));
        assertEquals("a~b", Hl7Normalizer.decodeEscapes("a\\R\\b"));
        assertEquals("a\\b", Hl7Normalizer.decodeEscapes("a\\E\\b"));
        assertEquals("l1\nl2", Hl7Normalizer.decodeEscapes("l1\\.br\\l2"));
        assertEquals("\n", Hl7Normalizer.decodeEscapes("\\X0A\\"));
        assertEquals("\r\n", Hl7Normalizer.decodeEscapes("\\X0D0A\\"));
        assertEquals("bold", Hl7Normalizer.decodeEscapes("\\H\\bold\\N\\"));
        assertEquals("ab", Hl7Normalizer.decodeEscapes("a\\Zxx\\b"));
        assertEquals("plain", Hl7Normalizer.decodeEscapes("plain"));
        assertEquals("a\\Fb", Hl7Normalizer.decodeEscapes("a\\Fb")); // unterminated preserved
        assertNull(Hl7Normalizer.decodeEscapes(null));
    }
}

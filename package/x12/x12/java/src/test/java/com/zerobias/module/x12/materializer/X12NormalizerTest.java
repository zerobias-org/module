package com.zerobias.module.x12.materializer;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class X12NormalizerTest {

    @Test
    void alphanumericIsTrimmed() {
        assertEquals("EXAMPLEPAYER", X12Normalizer.normalize("EXAMPLEPAYER   ", "AN", null));
        assertEquals("1", X12Normalizer.normalize("1", "ID", null));
        assertNull(X12Normalizer.normalize("   ", "AN", null));
        assertNull(X12Normalizer.normalize("", "R", null));
    }

    @Test
    void explicitDecimalBecomesBigDecimalNeverFloat() {
        Object v = X12Normalizer.normalize("220.00", "R", null);
        assertEquals(BigDecimal.class, v.getClass());
        assertEquals(new BigDecimal("220.00"), v);
        assertEquals("220.00", v.toString(), "scale preserved");
        assertEquals(new BigDecimal("-5"), X12Normalizer.normalize("-5", "R", null));
        assertEquals("12.34.5", X12Normalizer.normalize("12.34.5", "R", null), "unparseable stays a string");
    }

    @Test
    void impliedDecimalsAreShifted() {
        assertEquals(new BigDecimal("1234.56"), X12Normalizer.normalize("123456", "N2", 2));
        assertEquals(new BigDecimal("123456"), X12Normalizer.normalize("123456", "N0", 0));
        assertEquals(new BigDecimal("-0.05"), X12Normalizer.normalize("-5", "N2", 2));
        assertEquals(new BigDecimal("1.5"), X12Normalizer.normalize("15", "N1", null), "decimals from the type name");
        assertEquals("12A", X12Normalizer.normalize("12A", "N2", 2), "non-numeric stays a string");
    }

    @Test
    void datesBecomeIso() {
        assertEquals("2026-09-01", X12Normalizer.normalize("20260901", "DT", null));
        assertEquals("2026-09-22", X12Normalizer.normalize("260922", "DT", null), "YYMMDD -> 20YY");
        assertEquals("20260901-20260905", X12Normalizer.normalize("20260901-20260905", "DT", null), "RD8 ranges untouched");
    }

    @Test
    void timesBecomeColonSeparated() {
        assertEquals("12:00", X12Normalizer.normalize("1200", "TM", null));
        assertEquals("12:00:30", X12Normalizer.normalize("120030", "TM", null));
        assertEquals("12:00:30.5", X12Normalizer.normalize("1200305", "TM", null));
        assertEquals("12:00:30.55", X12Normalizer.normalize("12003055", "TM", null));
        assertEquals("12", X12Normalizer.normalize("12", "TM", null), "too short: unchanged");
    }

    @Test
    void unknownTypeIsTrimmedString() {
        assertEquals("x", X12Normalizer.normalize(" x ", null, null));
        assertEquals("AQ==", X12Normalizer.normalize("AQ==", "B", null));
    }
}

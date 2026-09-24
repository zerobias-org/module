package com.zerobias.module.x12.materializer;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class X12NormalizerTest {

    @Test
    void alphanumericIsTrimmed() {
        assertEquals("EXAMPLEPAYER", X12Normalizer.normalize("EXAMPLEPAYER   ", "AN", "string", null));
        assertEquals("1", X12Normalizer.normalize("1", "ID", "string", null));
        assertNull(X12Normalizer.normalize("   ", "AN", "string", null));
        assertNull(X12Normalizer.normalize("", "R", "decimal", null));
    }

    @Test
    void explicitDecimalBecomesBigDecimalNeverFloat() {
        Object v = X12Normalizer.normalize("220.00", "R", "decimal", null);
        assertEquals(BigDecimal.class, v.getClass());
        assertEquals(new BigDecimal("220.00"), v);
        assertEquals("220.00", v.toString(), "scale preserved");
        assertEquals(new BigDecimal("-5"), X12Normalizer.normalize("-5", "R", "decimal", null));
        assertEquals("12.34.5", X12Normalizer.normalize("12.34.5", "R", "decimal", null), "unparseable stays a string");
    }

    @Test
    void impliedDecimalsAreShifted() {
        assertEquals(new BigDecimal("1234.56"), X12Normalizer.normalize("123456", "N2", "decimal", 2));
        assertEquals(new BigDecimal("123456"), X12Normalizer.normalize("123456", "N0", "decimal", 0));
        assertEquals(new BigDecimal("-0.05"), X12Normalizer.normalize("-5", "N2", "decimal", 2));
        assertEquals(new BigDecimal("1.5"), X12Normalizer.normalize("15", "N1", "decimal", null), "decimals from the type name");
        assertEquals("12A", X12Normalizer.normalize("12A", "N2", "decimal", 2), "non-numeric stays a string");
    }

    @Test
    void numericTypeTheSchemaDeclaresStringKeepsLeadingZeros() {
        // GS06 / AK102 (data element 28) and ISA13 (I12) are N0 on the wire but control numbers.
        assertEquals("000000101", X12Normalizer.normalize("000000101", "N0", "string", null));
        assertEquals("0102", X12Normalizer.normalize("0102", "N0", "string", null));
        assertEquals(new BigDecimal("102"), X12Normalizer.normalize("0102", "N0", "decimal", 0));
    }

    @Test
    void datesBecomeIsoOnlyWhenTheyExist() {
        assertEquals("2026-09-01", X12Normalizer.normalize("20260901", "DT", "date", null));
        assertEquals("2026-09-22", X12Normalizer.normalize("260922", "DT", "date", null), "YYMMDD -> 20YY");
        assertEquals("2024-02-29", X12Normalizer.normalize("20240229", "DT", "date", null));
        assertEquals("20261345", X12Normalizer.normalize("20261345", "DT", "date", null), "month 13: raw");
        assertEquals("20250229", X12Normalizer.normalize("20250229", "DT", "date", null), "not a leap year: raw");
        assertEquals("261345", X12Normalizer.normalize("261345", "DT", "date", null));
        assertEquals("20260901-20260905", X12Normalizer.normalize("20260901-20260905", "DT", "date", null),
            "not a DT value: raw");
    }

    @Test
    void timesAlwaysCarrySecondsAndMustExist() {
        assertEquals("12:00:00", X12Normalizer.normalize("1200", "TM", "string", null));
        assertEquals("12:00:30", X12Normalizer.normalize("120030", "TM", "string", null));
        assertEquals("12:00:30.5", X12Normalizer.normalize("1200305", "TM", "string", null));
        assertEquals("12:00:30.55", X12Normalizer.normalize("12003055", "TM", "string", null));
        assertEquals("12", X12Normalizer.normalize("12", "TM", "string", null), "too short: unchanged");
        assertEquals("12003", X12Normalizer.normalize("12003", "TM", "string", null), "odd length is not truncated");
        assertEquals("2460", X12Normalizer.normalize("2460", "TM", "string", null), "hour 24: raw");
        assertEquals("120075", X12Normalizer.normalize("120075", "TM", "string", null), "second 75: raw");
    }

    @Test
    void dateTimePeriodFollowsItsFormatQualifier() {
        assertEquals("2026-09-01", X12Normalizer.dateTimePeriod("20260901", "D8"));
        assertEquals("2026-09-01/2026-09-05", X12Normalizer.dateTimePeriod("20260901-20260905", "RD8"));
        assertEquals("2026-09-01T13:45:00", X12Normalizer.dateTimePeriod("202609011345", "DT"));
        assertEquals("20260901", X12Normalizer.dateTimePeriod("20260901", "D6"), "other qualifier: verbatim");
        assertEquals("20260901", X12Normalizer.dateTimePeriod(" 20260901 ", null), "no qualifier: trimmed");
        assertEquals("20261345", X12Normalizer.dateTimePeriod("20261345", "D8"), "impossible date: raw");
        assertEquals("20260901-20261345", X12Normalizer.dateTimePeriod("20260901-20261345", "RD8"));
        assertEquals("202609012460", X12Normalizer.dateTimePeriod("202609012460", "DT"));
        assertEquals("20260901", X12Normalizer.dateTimePeriod("20260901", "RD8"), "value does not fit the qualifier");
    }

    @Test
    void unknownTypeIsTrimmedString() {
        assertEquals("x", X12Normalizer.normalize(" x ", null, null, null));
        assertEquals("AQ==", X12Normalizer.normalize("AQ==", "B", "byte", null));
    }
}

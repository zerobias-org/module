package com.zerobias.module.x12.parser;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeparatorsTest {

    private static final String ISA = "ISA*00*          *00*          *ZZ*EXAMPLEPAYER   *ZZ*EXAMPLEPROV    *260922*1200*^*00501*000000101*0*T*:~";

    @Test
    void readsTheFourDelimitersFromTheFixedWidthIsa() throws Exception {
        Separators s = Separators.fromIsa(ISA + "\r\nGS*HP~");
        assertEquals('*', s.element());
        assertEquals('^', s.repetition());
        assertEquals(':', s.component());
        assertEquals('~', s.segment());
        assertEquals("\r\n", s.lineBreak());
        assertTrue(s.hasRepetition());
        assertArrayEquals(new String[] {"HC", "99213"}, s.splitComponents("HC:99213"));
        assertArrayEquals(new String[] {"ABK:J069", "ABF:K3580"}, s.splitRepetitions("ABK:J069^ABF:K3580"));
        assertEquals("[~,*,:]", s.toImsweb().toString());
    }

    @Test
    void version4010RepetitionPlaceholderIsNotARepetitionSeparator() throws Exception {
        Separators s = Separators.fromIsa(ISA.replace("*^*", "*U*"));
        assertFalse(s.hasRepetition());
        assertArrayEquals(new String[] {"A^B"}, s.splitRepetitions("A^B"));
    }

    @Test
    void rejectsShortOrNonIsaOrInconsistentHeaders() {
        assertThrows(X12ParseException.class, () -> Separators.fromIsa("GS*HP*..."));
        assertThrows(X12ParseException.class, () -> Separators.fromIsa(ISA.substring(0, 50)));
        assertThrows(X12ParseException.class, () -> Separators.fromIsa(ISA.replace('*', '|').replace("|:~", "||~")));
        assertThrows(X12ParseException.class, () -> Separators.fromIsa(ISA.replace(":~", "A~")), "alphanumeric delimiter");
    }

    @Test
    void synthesizerBuildsA106CharIsaAndOneGroup() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC);
        String bare = "ST*837*0001*005010X222A1~\nBHT*0019*00*X*20260922*1200*CH~\nSE*3*0001~\nST*837*0002*005010X222A1~\nSE*2*0002~\n";
        EnvelopeSynthesizer.Wrapped w = EnvelopeSynthesizer.wrap(bare, clock);
        List<String> lines = List.of(w.text().split("\n"));
        assertEquals(106, lines.get(0).length(), "ISA incl. terminator");
        assertEquals("GS*HC*SYNTHETIC*SYNTHETIC*20260922*1200*1*X*005010X222A1~", lines.get(1));
        assertEquals("GE*2*1~", lines.get(lines.size() - 2));
        assertEquals("IEA*1*000000001~", lines.get(lines.size() - 1));
        assertEquals('~', w.separators().segment());
        assertEquals("\n", w.separators().lineBreak());
        // The wrapped text round-trips through the ISA reader with the same delimiters.
        assertEquals(w.separators(), Separators.fromIsa(w.text()));
    }
}

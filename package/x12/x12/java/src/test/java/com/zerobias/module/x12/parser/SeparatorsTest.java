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
        assertBadIsa(ISA.replace('*', '|').replace("|:~", "||~"), "not distinct");
        assertBadIsa(ISA.replace(":~", "A~"), "alphanumeric");
        assertBadIsa(ISA.replace("*^*", "*X*"), "alphanumeric");
        assertBadIsa(ISA.replace("*^*", "*:*"), "not distinct");
    }

    @Test
    void elementSeparatorMustSitAtEveryFixedIsaPosition() {
        // ISA06 one character short: ISA16 lands on the terminator and the terminator on the line
        // break, which used to split every later line whole ("unsupported-guide: GS08 '005010X221A1~'").
        String shortSender = ISA.replace("EXAMPLEPAYER   ", "EXAMPLEPAYER  ") + "\nGS*HP*EXAMPLEPAYER~\n";
        assertBadIsa(shortSender, "expected at ISA character 51");
        assertBadIsa(ISA.replace("*T*:~", "*TT:~"), "expected at ISA character 104");
    }

    private static void assertBadIsa(String isa, String reason) {
        X12ParseException e = assertThrows(X12ParseException.class, () -> Separators.fromIsa(isa));
        assertTrue(e.getMessage().startsWith("bad-isa:"), e.getMessage());
        assertTrue(e.getMessage().contains(reason), e.getMessage());
    }

    @Test
    void synthesizerReadsTheComponentSeparatorFromAComposite() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC);
        String bare = "ST*837*0001*005010X222A1~\nCLM*CLM0001*300.00***11>B>1~\nSV1*HC>99213*200.00~\nSE*4*0001~\n";
        EnvelopeSynthesizer.Wrapped w = EnvelopeSynthesizer.wrap(bare, clock);
        assertEquals('>', w.separators().component());
        assertTrue(w.text().startsWith("ISA*") && w.text().contains("*00501*000000001*0*T*>~"), w.text());
        assertEquals(':', EnvelopeSynthesizer.wrap("ST*837*0001*005010X222A1~\nSE*2*0001~\n", clock)
            .separators().component(), "no composite to read: the default");
        assertEquals('>', EnvelopeSynthesizer.wrap("ST:837:0001:005010X222A1~\nSE:2:0001~\n", clock)
            .separators().component(), "default when ':' is the element separator");
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

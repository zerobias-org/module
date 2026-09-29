package com.zerobias.module.x12.parser;

import com.imsweb.x12.Loop;
import com.imsweb.x12.reader.X12Reader;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static com.zerobias.module.x12.parser.Fixtures.F277CA;
import static com.zerobias.module.x12.parser.Fixtures.F835;
import static com.zerobias.module.x12.parser.Fixtures.F837I;
import static com.zerobias.module.x12.parser.Fixtures.F837P;
import static com.zerobias.module.x12.parser.Fixtures.F999;
import static com.zerobias.module.x12.parser.Fixtures.bytes;
import static com.zerobias.module.x12.parser.Fixtures.malformed;
import static com.zerobias.module.x12.parser.Fixtures.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The imsweb wrapper against every committed fixture (DESIGN §4.2b, §4.3, §13). */
class X12ParseTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void parses835FixtureIntoOneInterchangeGroupAndTransaction() throws Exception {
        X12Parse.ParsedFile p = X12Parse.parse(bytes(F835), false);
        assertEquals("005010X221A1", p.gs08());
        assertEquals("005010X221A1", p.rawGs08());
        assertEquals(X12Reader.FileType.ANSI835_5010_X221, p.fileType());
        assertFalse(p.synthetic());
        assertEquals(List.of(), p.errors(), "a well-formed fixture has no non-fatal errors");
        assertEquals(1, p.interchanges().size());
        X12Parse.Interchange isa = p.interchanges().get(0);
        assertEquals("000000101", isa.controlNumber());
        assertEquals("EXAMPLEPAYER", isa.senderId(), "ISA06 padding trimmed");
        assertEquals("EXAMPLEPROV", isa.receiverId());
        assertEquals(Instant.parse("2026-09-22T12:00:00Z"), isa.interchangeAt().orElseThrow(), "ISA09+ISA10");
        assertEquals(1, isa.groups().size());
        X12Parse.FunctionalGroup gs = isa.groups().get(0);
        assertEquals("101", gs.controlNumber());
        assertEquals(1, gs.transactions().size());
        X12Parse.Transaction tx = gs.transactions().get(0);
        assertEquals("835", tx.st01());
        assertEquals("0001", tx.st02());
        assertEquals(42, tx.segments().size(), "ST..SE inclusive = SE01");
        assertEquals("ST_LOOP", tx.loop().getId());
        // Tree shape mirrors the map: 2 CLP loops, 3 SVC loops, 5 CAS, 1 PLB.
        assertEquals(2, tx.loop().findAllLoops("2100").size());
        assertEquals(3, tx.loop().findAllLoops("2110").size());
        assertEquals(5, countSegments(tx.loop(), "CAS"));
        assertEquals(1, countSegments(tx.loop(), "PLB"));
        assertEquals(1, tx.loop().getSegment("SE") == null ? 0 : 1, "SE appended to ST_LOOP");
    }

    @Test
    void rawX12IsAReparseableSingleTransactionInterchange() throws Exception {
        X12Parse.ParsedFile p = X12Parse.parse(bytes(F835), false);
        X12Parse.Transaction tx = p.transactions().get(0);
        String raw = tx.rawX12();
        assertTrue(raw.startsWith("ISA*00*"), "ISA context line first");
        assertTrue(raw.contains("\nGS*HP*"), "GS context line, file's line break kept");
        assertTrue(raw.contains("CLP*CLM0001*1*300.00*220.00*40.00*12*EHP2026000001*11*1~"), "segments verbatim");
        assertTrue(raw.endsWith("IEA*1*000000101~\n"));
        X12Parse.ParsedFile again = X12Parse.parse(raw.getBytes(StandardCharsets.UTF_8), false);
        assertEquals(1, again.transactionCount());
        assertEquals(tx.segments(), again.transactions().get(0).segments());
    }

    @Test
    void parsesEveryWellFormedFixture() throws Exception {
        assertGuide(F837P, "005010X222A1", "837", 30, X12Reader.FileType.ANSI837_5010_X222, List.of());
        assertGuide(F837I, "005010X223A2", "837", 32, X12Reader.FileType.ANSI837_5010_X223, List.of());
        // imsweb's 277 X214 map marks 2200C (claim-level status) REQUIRED under the billing-provider
        // HL (2000C); the fixture's HL*3 carries none, so imsweb reports a non-fatal structural error.
        // That is exactly what parserErrorCount is for — the file still parses and materializes.
        assertGuide(F277CA, "005010X214", "277", 22, X12Reader.FileType.ANSI277_5010_X214,
            List.of("2200C is required but not found in 2000C iteration #1"));
        assertGuide(F999, "005010X231A1", "999", 10, X12Reader.FileType.ANSI837_5010_X231, List.of());
    }

    private static void assertGuide(String fixture, String gs08, String st01, int segs, X12Reader.FileType type,
                                    List<String> expectedErrors) throws Exception {
        X12Parse.ParsedFile p = X12Parse.parse(bytes(fixture), false);
        assertEquals(gs08, p.gs08(), fixture);
        assertEquals(type, p.fileType(), fixture);
        assertEquals(1, p.transactionCount(), fixture);
        X12Parse.Transaction tx = p.transactions().get(0);
        assertEquals(st01, tx.st01(), fixture);
        assertEquals(segs, tx.segments().size(), fixture + " ST..SE count");
        assertEquals(expectedErrors, p.errors(), fixture + " non-fatal errors");
    }

    @Test
    void gs08AliasIsCanonicalizedBeforeImswebSeesIt() throws Exception {
        // imsweb compares GS08 exactly against 005010X222A1; the wire commonly carries 005010X222.
        String t = text(F837P).replace("*005010X222A1~", "*005010X222~");
        assertTrue(t.contains("GS*HC*EXAMPLEPROV*EXAMPLEPAYER*20260922*1200*102*X*005010X222~"));
        X12Parse.ParsedFile p = X12Parse.parse(t.getBytes(StandardCharsets.UTF_8), false, CLOCK);
        assertEquals("005010X222A1", p.gs08(), "canonical");
        assertEquals("005010X222", p.rawGs08(), "as written");
        assertEquals(1, p.transactionCount());
        assertTrue(p.transactions().get(0).group().gsRaw().endsWith("*005010X222"), "raw GS kept verbatim");
        assertTrue(p.errors().isEmpty(), "no 'ANSI version not consistent' noise: " + p.errors());
    }

    @Test
    void st03AliasIsCanonicalizedTooBecauseTheX223MapCodesIt() throws Exception {
        // imsweb's 837I map lists 005010X223A2 as ST03's only valid code: a bare 005010X223 never starts ST_LOOP.
        String t = text(F837I).replace("*005010X223A2~", "*005010X223~");
        assertTrue(t.contains("ST*837*0001*005010X223~"));
        X12Parse.ParsedFile p = X12Parse.parse(t.getBytes(StandardCharsets.UTF_8), false, CLOCK);
        assertEquals("005010X223A2", p.gs08());
        assertEquals("005010X223", p.rawGs08());
        assertEquals(1, p.transactionCount());
        assertEquals("005010X223", p.transactions().get(0).st03(), "raw ST03 kept as written");
        assertTrue(p.transactions().get(0).rawX12().contains("ST*837*0001*005010X223~"));
    }

    @Test
    void repetitionSeparatorOtherThanCaretIsAcceptedDespiteImswebsControlMap() throws Exception {
        // imsweb's 00501 control map codes ISA11 as U|^ only; the x12.org 999 example uses '>'.
        String t = text(F835).replace("*^*00501*", "*>*00501*");
        X12Parse.ParsedFile p = X12Parse.parse(t.getBytes(StandardCharsets.UTF_8), false);
        assertEquals('>', p.separators().repetition());
        assertTrue(p.separators().hasRepetition());
        assertEquals(1, p.transactionCount());
        assertEquals(List.of(), p.errors());
        assertTrue(p.transactions().get(0).rawX12().contains("*>*00501*"), "raw ISA untouched");
    }

    @Test
    void componentSeparatorGreaterThanWorks() throws Exception {
        String t = text(F835).replace(':', '>');
        X12Parse.ParsedFile p = X12Parse.parse(t.getBytes(StandardCharsets.UTF_8), false);
        assertEquals('>', p.separators().component());
        assertEquals('^', p.separators().repetition());
        assertEquals("\n", p.separators().lineBreak());
        Loop l = p.transactions().get(0).loop().findAllLoops("2110").get(0);
        assertEquals("HC>99213", l.getSegment("SVC").getElements().get(0).getValue());
        assertEquals(List.of("HC", "99213"), l.getSegment("SVC").getElements().get(0).getSubValues(),
            "imsweb splits composites on the ISA16 char");
    }

    @Test
    void se01AndSe02MismatchesAreNonFatal() throws Exception {
        String t = text(F835).replace("SE*42*0001~", "SE*41*0002~");
        X12Parse.ParsedFile p = X12Parse.parse(t.getBytes(StandardCharsets.UTF_8), false);
        assertEquals(1, p.transactionCount());
        assertEquals(2, p.errors().size(), p.errors().toString());
        assertTrue(p.errors().get(0).contains("SE01=41 but ST..SE holds 42 segments"), p.errors().toString());
        assertTrue(p.errors().get(1).contains("SE02=0002 != ST02=0001"), p.errors().toString());
    }

    @Test
    void multipleTransactionsAndGroupsAreAllReturnedInDocumentOrder() throws Exception {
        String base = text(F835);
        String st = base.substring(base.indexOf("ST*835"), base.indexOf("GE*"));
        String twoInOneGroup = base.replace(st + "GE*1*101~", st + st.replace("*0001~", "*0002~") + "GE*2*101~");
        X12Parse.ParsedFile p = X12Parse.parse(twoInOneGroup.getBytes(StandardCharsets.UTF_8), false);
        assertEquals(2, p.transactionCount());
        assertEquals("0001", p.transactions().get(0).st02());
        assertEquals("0002", p.transactions().get(1).st02());
        assertEquals(List.of(), p.errors());

        // Two ISA interchanges concatenated in one file (DESIGN §4.3).
        String twoIsa = base + base.replace("000000101", "000000102").replace("*101*X*", "*102*X*").replace("GE*1*101", "GE*1*102");
        X12Parse.ParsedFile q = X12Parse.parse(twoIsa.getBytes(StandardCharsets.UTF_8), false);
        assertEquals(2, q.interchanges().size());
        assertEquals("000000102", q.interchanges().get(1).controlNumber());
        assertEquals("102", q.transactions().get(1).group().controlNumber());
    }

    @Test
    void bareTransactionSetIsWrappedOnlyWhenAllowed() throws Exception {
        String bare = Fixtures.bare835WithSt03();
        assertTrue(EnvelopeSynthesizer.isBare(bare));
        X12ParseException refused = assertThrows(X12ParseException.class,
            () -> X12Parse.parse(bare.getBytes(StandardCharsets.UTF_8), false, CLOCK));
        assertTrue(refused.getMessage().startsWith("bare-transaction-set"), refused.getMessage());

        X12Parse.ParsedFile p = X12Parse.parse(bare.getBytes(StandardCharsets.UTF_8), true, CLOCK);
        assertTrue(p.synthetic());
        assertEquals("005010X221A1", p.gs08(), "guide from ST03");
        assertEquals(1, p.transactionCount());
        X12Parse.Transaction tx = p.transactions().get(0);
        assertEquals(EnvelopeSynthesizer.SYNTHETIC_ID, tx.interchange().senderId());
        assertEquals(EnvelopeSynthesizer.ISA_CONTROL, tx.interchange().controlNumber());
        assertEquals(EnvelopeSynthesizer.GS_CONTROL, tx.group().controlNumber());
        assertEquals(Instant.parse("2026-09-22T12:00:00Z"), tx.interchange().interchangeAt().orElseThrow());
        assertEquals(42, tx.segments().size());
        assertTrue(tx.rawX12().startsWith("ISA*00*          *00*          *ZZ*SYNTHETIC      *ZZ*SYNTHETIC      *260922*1200*^*00501*000000001*0*T*:~"));
        assertEquals(List.of(), p.errors());
    }

    @Test
    void bareTransactionSetWithoutSt03IsRefused() {
        String bare = Fixtures.bare835WithSt03().replace("ST*835*0001*005010X221A1~", "ST*835*0001~");
        X12ParseException e = assertThrows(X12ParseException.class,
            () -> X12Parse.parse(bare.getBytes(StandardCharsets.UTF_8), true, CLOCK));
        assertTrue(e.getMessage().contains("ST03 is empty"), e.getMessage());
    }

    @Test
    void malformedFixturesFailWithStableKinds() {
        X12ParseException truncated = assertThrows(X12ParseException.class,
            () -> X12Parse.parse(malformed("truncated-no-iea.x12"), false));
        assertTrue(truncated.getMessage().startsWith("fatal:"), truncated.getMessage());
        assertEquals(List.of("Unable to find end of transaction"), truncated.fatalErrors(), "imsweb fatal verbatim");

        X12ParseException badSeps = assertThrows(X12ParseException.class,
            () -> X12Parse.parse(malformed("bad-separators.x12"), false));
        assertTrue(badSeps.getMessage().startsWith("bad-isa") || badSeps.getMessage().startsWith("bad-separators"),
            badSeps.getMessage());

        X12ParseException unknown = assertThrows(X12ParseException.class,
            () -> X12Parse.parse(malformed("unknown-guide-gs08.x12"), false));
        assertEquals("unsupported-guide: GS08 '005010X999' is not a supported implementation guide", unknown.getMessage());

        X12ParseException empty = assertThrows(X12ParseException.class,
            () -> X12Parse.parse(malformed("empty.x12"), false));
        assertTrue(empty.getMessage().startsWith("empty-file"), empty.getMessage());
    }

    @Test
    void closingSegmentFalsePositivesAreDroppedButRealOmissionsKept() throws Exception {
        // imsweb validates a loop before appending its closing segment, so a present AK9/IK5 is
        // still reported "required but not found". A genuinely missing IK5 must survive the filter.
        String t = text(F999).replace("IK5*R*5~\n", "").replace("SE*10*0001", "SE*9*0001");
        X12Parse.ParsedFile p = X12Parse.parse(t.getBytes(StandardCharsets.UTF_8), false);
        assertEquals(List.of("IK5 in loop 2000 is required but not found"), p.errors());
        assertEquals(1, p.transactionCount());
    }

    @Test
    void knownGuideWithoutImswebMapIsUnsupported() {
        String t = text(F835).replace("*005010X221A1~", "*005010X218~");
        X12ParseException e = assertThrows(X12ParseException.class,
            () -> X12Parse.parse(t.getBytes(StandardCharsets.UTF_8), false));
        assertTrue(e.getMessage().startsWith("unsupported-guide: no imsweb 005010 map for 005010X218"), e.getMessage());
    }

    @Test
    void transactionSetNotMatchingTheGuideMapIsUnsupported() {
        // A 276 under 005010X212: imsweb only maps the 277 side of X212.
        String t = text(F277CA).replace("ST*277*0001*005010X214~", "ST*276*0001*005010X212~").replace("*005010X214~", "*005010X212~");
        X12ParseException e = assertThrows(X12ParseException.class,
            () -> X12Parse.parse(t.getBytes(StandardCharsets.UTF_8), false));
        assertTrue(e.getMessage().startsWith("unsupported-transaction: ST01 276 under guide 005010X212"), e.getMessage());
    }

    @Test
    void bomAndLeadingWhitespaceAreTolerated() throws Exception {
        String t = "﻿\r\n" + text(F835);
        assertEquals(1, X12Parse.parse(t.getBytes(StandardCharsets.UTF_8), false).transactionCount());
    }

    @Test
    void expectedSt01StripsTheDisplaySuffix() {
        assertEquals("837", X12Parse.expectedSt01("837P"));
        assertEquals("277", X12Parse.expectedSt01("277CA"));
        assertEquals("835", X12Parse.expectedSt01("835"));
    }

    private static int countSegments(Loop loop, String id) {
        int n = 0;
        for (var s : loop.getSegments()) {
            if (id.equals(s.getId())) {
                n++;
            }
        }
        for (Loop l : loop.getLoops()) {
            n += countSegments(l, id);
        }
        assertNotNull(loop.getId());
        return n;
    }
}

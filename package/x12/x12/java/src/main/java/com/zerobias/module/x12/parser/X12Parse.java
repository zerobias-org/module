package com.zerobias.module.x12.parser;

import com.imsweb.x12.Loop;
import com.imsweb.x12.reader.X12Reader;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The imsweb {@link X12Reader} wrapper (DESIGN §4.2 step 3b): decodes the bytes, detects the
 * delimiters from the ISA, scans the envelope, and parses every functional group with the
 * {@link X12Reader.FileType} its own GS08 selects ({@link TransactionTypes#guide}), handing
 * back one {@link Interchange} per ISA with the imsweb {@link Loop} of every transaction set
 * plus its verbatim ST..SE segments.
 *
 * <p>Things imsweb does not do that this wrapper does:
 * <ul>
 *   <li><b>One guide per functional group.</b> An imsweb reader parses with one FileType and
 *       checks only the first GS08 against it, so an interchange carrying, say, a 999 group and
 *       a 277CA group would fail as a whole. Each group is fed to its own reader as
 *       ISA + GS..GE + IEA; the results are stitched back in document order.</li>
 *   <li><b>GS08 aliases.</b> imsweb compares GS08 <em>exactly</em> against the FileType's
 *       canonical id ({@code 005010X222A1}); the wire often carries {@code 005010X222} or
 *       {@code 005010X223A1}. The text fed to imsweb has GS08 rewritten to
 *       {@link TransactionTypes#canonical}; the raw segments kept per transaction are
 *       untouched.</li>
 *   <li><b>Bare ST..SE files</b> are wrapped by {@link EnvelopeSynthesizer} when allowed
 *       (DESIGN §4.3); {@link ParsedFile#synthetic()} reports it.</li>
 *   <li><b>Envelope integrity.</b> imsweb verifies none of SE01/SE02, GE01/GE02, IEA01/IEA02
 *       and accepts an ST without SE ({@code missing-se}) or a GS without GE; all are checked
 *       here and reported as non-fatal
 *       {@link ParsedFile#errors()} (they count toward {@code parserErrorCount}). An ISA without
 *       IEA is reported too, though imsweb then fails the file.</li>
 *   <li><b>One set of delimiters.</b> Every ISA in the file must declare the first one's
 *       delimiters; the segments are split once, with those.</li>
 *   <li><b>ST01 vs guide.</b> A 276 under {@code 005010X212} (whose only imsweb map is the
 *       277) is refused as {@code unsupported-transaction} instead of being fed to the
 *       wrong map.</li>
 * </ul>
 *
 * <p>Non-fatal errors are file-level (imsweb does not attribute them to a transaction set);
 * the caller stamps the file's count on every row.
 */
public final class X12Parse {

    /** ISA09 {@code YYMMDD} + ISA10 {@code HHMM}. */
    private static final DateTimeFormatter ISA_DATE = DateTimeFormatter.ofPattern("yyMMdd");
    private static final DateTimeFormatter ISA_TIME = DateTimeFormatter.ofPattern("HHmm");

    private X12Parse() {
    }

    // ---- result model ----------------------------------------------------------------

    /**
     * Everything parsed from one file. {@code gs08}, {@code rawGs08} and {@code fileType} are
     * the first functional group's; a file may carry groups of several guides, so anything
     * per transaction reads {@link Transaction#gs08()}.
     */
    public record ParsedFile(
            List<Interchange> interchanges,
            Separators separators,
            String gs08,
            String rawGs08,
            X12Reader.FileType fileType,
            List<String> errors,
            boolean synthetic) {

        /** Every transaction set in document order. */
        public List<Transaction> transactions() {
            List<Transaction> out = new ArrayList<>();
            for (Interchange i : interchanges) {
                for (FunctionalGroup g : i.groups()) {
                    out.addAll(g.transactions());
                }
            }
            return out;
        }

        public int transactionCount() {
            return transactions().size();
        }
    }

    /** One ISA..IEA. {@code isaTokens} are the 16 raw elements (index 1..16, untrimmed). */
    public record Interchange(String[] isaTokens, String isaRaw, String ieaRaw, List<FunctionalGroup> groups) {

        public String isa(int n) {
            return n < isaTokens.length ? isaTokens[n].trim() : "";
        }

        public String controlNumber() {
            return isa(13);
        }

        public String senderId() {
            return isa(6);
        }

        public String receiverId() {
            return isa(8);
        }

        /** ISA09 + ISA10 as a UTC instant (X12 carries no zone), or empty when malformed. */
        public Optional<Instant> interchangeAt() {
            try {
                LocalDate d = LocalDate.parse(isa(9), ISA_DATE);
                LocalTime t = LocalTime.parse(isa(10), ISA_TIME);
                return Optional.of(d.atTime(t).toInstant(ZoneOffset.UTC));
            } catch (RuntimeException e) {
                return Optional.empty();
            }
        }
    }

    /**
     * One GS..GE, parsed with its own guide: {@code gs08} is the canonical id
     * ({@link TransactionTypes#canonical}), {@link #rawGs08()} the spelling on the wire.
     */
    public record FunctionalGroup(String[] gsTokens, String gsRaw, String geRaw, String gs08,
                                  X12Reader.FileType fileType, List<Transaction> transactions, Loop loop) {

        public String gs(int n) {
            return n < gsTokens.length ? gsTokens[n].trim() : "";
        }

        public String controlNumber() {
            return gs(6);
        }

        public String rawGs08() {
            return gs(8);
        }
    }

    /**
     * One ST..SE: the imsweb {@link Loop} (id {@code ST_LOOP}) and the verbatim segments
     * from the file. {@link #rawX12} rebuilds a complete, re-parseable single-transaction
     * interchange: the ISA and GS context lines, the ST..SE segments, then GE and IEA trailers
     * counting exactly this one transaction and group.
     */
    public record Transaction(Loop loop, String st01, String st02, String st03, List<String> segments,
                              Interchange interchange, FunctionalGroup group, Separators separators) {

        public String controlNumber() {
            return st02;
        }

        /** The canonical guide of this transaction's functional group. */
        public String gs08() {
            return group.gs08();
        }

        public String rawX12() {
            StringBuilder sb = new StringBuilder();
            String term = separators.segment() + separators.lineBreak();
            char e = separators.element();
            sb.append(interchange.isaRaw()).append(term);
            sb.append(group.gsRaw()).append(term);
            for (String s : segments) {
                sb.append(s).append(term);
            }
            sb.append("GE").append(e).append('1').append(e).append(group.controlNumber()).append(term);
            sb.append("IEA").append(e).append('1').append(e).append(interchange.controlNumber()).append(term);
            return sb.toString();
        }
    }

    // ---- entry points ----------------------------------------------------------------

    public static ParsedFile parse(byte[] bytes, boolean allowBareTransactionSets, Clock clock) throws X12ParseException {
        if (bytes == null || bytes.length == 0) {
            throw new X12ParseException("empty-file: zero bytes");
        }
        return parse(decode(bytes), allowBareTransactionSets, clock);
    }

    public static ParsedFile parse(String rawText, boolean allowBareTransactionSets, Clock clock) throws X12ParseException {
        String text = stripLeading(rawText);
        if (text.isEmpty()) {
            throw new X12ParseException("empty-file: no segments");
        }
        boolean synthetic = false;
        Separators seps;
        if (EnvelopeSynthesizer.isBare(text)) {
            if (!allowBareTransactionSets) {
                throw new X12ParseException("bare-transaction-set: file starts at ST and allowBareTransactionSets is false");
            }
            EnvelopeSynthesizer.Wrapped w = EnvelopeSynthesizer.wrap(text, clock);
            text = w.text();
            seps = w.separators();
            synthetic = true;
        } else {
            seps = Separators.fromIsa(text);
        }

        Scan scan = scan(text, seps);
        if (scan.interchanges.isEmpty()) {
            throw new X12ParseException("no-isa: no interchange found");
        }

        // Guide selection per group BEFORE imsweb sees any text (DESIGN §4.2 step 3b).
        ScanGroup first = null;
        for (ScanInterchange si : scan.interchanges) {
            for (ScanGroup sg : si.groups) {
                String g = sg.gs08();
                if (g.isEmpty()) {
                    throw new X12ParseException("bad-gs: GS segment has no GS08 (" + abbreviate(sg.gsRaw) + ")");
                }
                sg.guide = TransactionTypes.guide(g).orElseThrow(() -> new X12ParseException(
                    "unsupported-guide: GS08 '" + g + "' is not a supported implementation guide"));
                String expectedSt01 = expectedSt01(sg.guide.transactionType());
                for (ScanTransaction st : sg.transactions) {
                    if (!st.st01.equals(expectedSt01)) {
                        throw new X12ParseException("unsupported-transaction: ST01 " + st.st01 + " under guide "
                            + sg.guide.canonicalGs08() + " (its map is the " + expectedSt01 + ")");
                    }
                }
                if (first == null) {
                    first = sg;
                }
            }
        }
        if (first == null) {
            throw new X12ParseException("bad-gs: no functional group (GS) after the ISA");
        }

        List<String> errors = new ArrayList<>(scan.errors);
        List<Interchange> interchanges = new ArrayList<>();
        for (ScanInterchange si : scan.interchanges) {
            List<FunctionalGroup> groups = new ArrayList<>();
            Interchange interchange = new Interchange(si.isaTokens, si.isaRaw, si.ieaRaw, groups);
            for (ScanGroup sg : si.groups) {
                if (si.isaRaw.isEmpty()) {
                    throw new X12ParseException("structure-mismatch: functional group " + sg.controlNumber()
                        + " is not inside an ISA..IEA interchange");
                }
                Loop gsLoop = parseGroup(scan, si, sg, errors);
                List<Loop> stLoops = childLoops(gsLoop, "ST_LOOP");
                if (stLoops.size() != sg.transactions.size()) {
                    throw new X12ParseException("structure-mismatch: imsweb produced " + stLoops.size()
                        + " transaction loop(s) but group " + sg.controlNumber() + " holds " + sg.transactions.size()
                        + " ST..SE span(s)");
                }
                List<Transaction> txs = new ArrayList<>();
                FunctionalGroup group = new FunctionalGroup(sg.gsTokens, sg.gsRaw, sg.geRaw, sg.guide.canonicalGs08(),
                    sg.guide.fileType(), txs, gsLoop);
                for (int t = 0; t < stLoops.size(); t++) {
                    ScanTransaction st = sg.transactions.get(t);
                    txs.add(new Transaction(stLoops.get(t), st.st01, st.st02, st.st03, List.copyOf(st.segments),
                        interchange, group, seps));
                }
                groups.add(group);
            }
            interchanges.add(interchange);
        }
        return new ParsedFile(List.copyOf(interchanges), seps, first.guide.canonicalGs08(), first.gs08(),
            first.guide.fileType(), List.copyOf(errors), synthetic);
    }

    /**
     * One functional group through imsweb as its own interchange; its non-fatal errors are
     * appended to {@code errors}. Returns the group's {@code GS_LOOP}.
     */
    private static Loop parseGroup(Scan scan, ScanInterchange si, ScanGroup sg, List<String> errors)
            throws X12ParseException {
        String canonical = sg.guide.canonicalGs08();
        X12Reader reader;
        try {
            reader = new X12Reader(sg.guide.fileType(), new StringReader(scan.imswebText(si, sg, canonical)));
        } catch (IOException | RuntimeException e) {
            throw new X12ParseException("parser-failure: imsweb threw " + e, List.of());
        }
        errors.addAll(dropClosingSegmentFalsePositives(reader.getErrors(), reader.getLoops()));
        if (!reader.getFatalErrors().isEmpty()) {
            throw new X12ParseException("fatal: imsweb could not parse group " + sg.controlNumber() + " as " + canonical
                + (errors.isEmpty() ? "" : "; errors: " + errors), reader.getFatalErrors());
        }
        List<Loop> isaLoops = reader.getLoops();
        List<Loop> gsLoops = isaLoops.size() == 1 ? childLoops(isaLoops.get(0), "GS_LOOP") : List.of();
        if (gsLoops.size() != 1) {
            throw new X12ParseException("structure-mismatch: imsweb produced " + isaLoops.size() + " interchange loop(s) and "
                + gsLoops.size() + " functional group loop(s) for group " + sg.controlNumber() + ", expected one of each");
        }
        return gsLoops.get(0);
    }

    private static final Pattern REQUIRED_NOT_FOUND = Pattern.compile("^(\\S+) in loop (\\S+) is required but not found$");

    /**
     * imsweb validates a loop's segments before its closing segment is appended (it exempts
     * only IEA/GE/SE), so a required closing segment such as {@code AK9} (HEADER) or
     * {@code IK5} (2000) in a 999 is reported "required but not found" for <em>every</em>
     * instance of the loop even when present. Keep exactly as many of those errors as loop
     * instances that really lack the segment; drop the rest.
     */
    static List<String> dropClosingSegmentFalsePositives(List<String> imswebErrors, List<Loop> roots) {
        List<String> out = new ArrayList<>();
        java.util.Map<String, Integer> budget = new java.util.HashMap<>();
        for (String e : imswebErrors) {
            java.util.regex.Matcher m = REQUIRED_NOT_FOUND.matcher(e);
            if (!m.matches()) {
                out.add(e);
                continue;
            }
            String seg = m.group(1);
            String loopId = m.group(2);
            int remaining = budget.computeIfAbsent(e, k -> {
                int[] counts = {0, 0}; // instances of loopId, instances lacking seg
                for (Loop root : roots) {
                    countMissing(root, loopId, seg, counts);
                }
                return counts[0] == 0 ? Integer.MAX_VALUE : counts[1]; // loop never built: not our false positive
            });
            if (remaining > 0) {
                out.add(e);
                budget.put(e, remaining == Integer.MAX_VALUE ? remaining : remaining - 1);
            }
        }
        return out;
    }

    private static void countMissing(Loop loop, String loopId, String seg, int[] counts) {
        if (loopId.equals(loop.getId())) {
            counts[0]++;
            boolean has = false;
            for (var s : loop.getSegments()) {
                if (seg.equals(s.getId())) {
                    has = true;
                    break;
                }
            }
            if (!has) {
                counts[1]++;
            }
        }
        for (Loop child : loop.getLoops()) {
            countMissing(child, loopId, seg, counts);
        }
    }

    /** Transaction set id (ST01) a display type maps to: {@code 837P → 837}, {@code 277CA → 277}. */
    public static String expectedSt01(String transactionType) {
        StringBuilder sb = new StringBuilder();
        for (char c : transactionType.toCharArray()) {
            if (Character.isDigit(c)) {
                sb.append(c);
            } else {
                break;
            }
        }
        return sb.length() == 0 ? transactionType : sb.toString();
    }

    private static List<Loop> childLoops(Loop parent, String id) {
        List<Loop> out = new ArrayList<>();
        for (Loop l : parent.getLoops()) {
            if (id.equals(l.getId())) {
                out.add(l);
            }
        }
        return out;
    }

    // ---- text handling ------------------------------------------------------------------

    /** UTF-8 when the bytes are valid UTF-8, else ISO-8859-1 (X12 extended character set). */
    static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    /** Drop a UTF-8 BOM and leading whitespace — imsweb needs the ISA at character 0. */
    static String stripLeading(String text) {
        if (text == null) {
            return "";
        }
        int i = 0;
        if (text.startsWith("﻿")) {
            i = 1;
        }
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return text.substring(i);
    }

    private static String abbreviate(String s) {
        return s == null ? "" : (s.length() > 60 ? s.substring(0, 60) + "…" : s);
    }

    // ---- envelope scan (our own, delimiter-driven, before imsweb) --------------------

    private static final class ScanTransaction {
        String st01;
        String st02;
        String st03;
        final List<String> segments = new ArrayList<>();
    }

    private static final class ScanGroup {
        String[] gsTokens;
        String gsRaw;
        String geRaw;
        TransactionTypes.Guide guide;
        final List<ScanTransaction> transactions = new ArrayList<>();

        String gs08() {
            return gsTokens.length > 8 ? gsTokens[8].trim() : "";
        }

        String controlNumber() {
            return gsTokens.length > 6 ? gsTokens[6].trim() : "";
        }
    }

    private static final class ScanInterchange {
        String[] isaTokens;
        String isaRaw;
        String ieaRaw;
        final List<ScanGroup> groups = new ArrayList<>();

        String controlNumber() {
            return isaTokens.length > 13 ? isaTokens[13].trim() : "";
        }
    }

    private static final class Scan {
        final List<ScanInterchange> interchanges = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        Separators seps;

        /**
         * One group as the interchange imsweb parses: ISA, GS, the group's ST..SE spans, and the
         * group's GE and the interchange's IEA when the file has them (imsweb fails a missing IEA,
         * which is what a truncated file must do). GS08 — and every ST03 that spells the same guide
         * (imsweb's X223 map lists {@code 005010X223A2} as ST03's only valid code, so a bare
         * {@code 005010X223} never starts ST_LOOP) — becomes the canonical id, and ISA11 becomes
         * {@code ^} where imsweb would reject it. The raw segments kept per transaction are never
         * touched.
         */
        String imswebText(ScanInterchange si, ScanGroup sg, String canonical) {
            StringBuilder sb = new StringBuilder();
            String term = seps.segment() + seps.lineBreak();
            sb.append(forImsweb(si.isaRaw, canonical)).append(term);
            sb.append(forImsweb(sg.gsRaw, canonical)).append(term);
            for (ScanTransaction t : sg.transactions) {
                for (String seg : t.segments) {
                    sb.append(forImsweb(seg, canonical)).append(term);
                }
            }
            if (sg.geRaw != null) {
                sb.append(sg.geRaw).append(term);
            }
            if (si.ieaRaw != null) {
                sb.append(si.ieaRaw).append(term);
            }
            return sb.toString();
        }

        /**
         * imsweb's 00501 control map lists {@code U} and {@code ^} as ISA11's only valid codes, so
         * an interchange using any other (legal) repetition separator, e.g. {@code >}, never
         * starts ISA_LOOP. imsweb never splits repetitions itself, so the character is
         * irrelevant to it; the materializer splits with the file's real {@link Separators}.
         */
        private String forImsweb(String seg, String canonical) {
            String[] tokens = seps.splitElements(seg);
            if ("ISA".equals(tokens[0]) && tokens.length > 11 && tokens[11].length() == 1
                    && !"^".equals(tokens[11]) && !"U".equals(tokens[11])) {
                tokens[11] = "^";
            } else if ("GS".equals(tokens[0]) && tokens.length > 8) {
                tokens[8] = canonical;
            } else if ("ST".equals(tokens[0]) && tokens.length > 3 && isAliasOf(tokens[3].trim(), canonical)) {
                tokens[3] = canonical;
            } else {
                return seg;
            }
            return String.join(String.valueOf(seps.element()), tokens);
        }

        private static boolean isAliasOf(String id, String canonical) {
            return id != null && !id.isEmpty() && !canonical.equals(id)
                && TransactionTypes.canonical(id).map(canonical::equals).orElse(false);
        }
    }

    private static Scan scan(String text, Separators seps) throws X12ParseException {
        Scan scan = new Scan();
        scan.seps = seps;
        List<String> segments = new ArrayList<>();
        for (String s : text.split(Pattern.quote(String.valueOf(seps.segment())))) {
            String t = s.strip();
            if (!t.isEmpty()) {
                segments.add(t);
            }
        }
        ScanInterchange isa = null;
        ScanGroup gs = null;
        ScanTransaction st = null;
        for (String seg : segments) {
            if (isIsa(seg)) {
                requireSameDelimiters(seg, seps, scan.interchanges.size() + 1);
            }
            String[] tokens = seps.splitElements(seg);
            String id = tokens[0].toUpperCase(Locale.ROOT);
            // A transaction set holds ST..SE only: an envelope segment never joins it, or a
            // transaction missing its SE would carry the next ST/GE/IEA and imsweb would get
            // that segment twice.
            switch (id) {
                case "ISA" -> {
                    closeGroup(scan, gs, st, "ISA");
                    closeInterchange(scan, isa);
                    isa = new ScanInterchange();
                    isa.isaTokens = tokens;
                    isa.isaRaw = seg;
                    scan.interchanges.add(isa);
                    gs = null;
                    st = null;
                }
                case "GS" -> {
                    closeGroup(scan, gs, st, "GS");
                    if (isa == null) {
                        scan.errors.add("GS before ISA");
                        isa = new ScanInterchange();
                        isa.isaTokens = new String[0];
                        isa.isaRaw = "";
                        scan.interchanges.add(isa);
                    }
                    gs = new ScanGroup();
                    gs.gsTokens = tokens;
                    gs.gsRaw = seg;
                    isa.groups.add(gs);
                    st = null;
                }
                case "ST" -> {
                    if (st != null) {
                        missingSe(scan, st, "ST");
                    }
                    if (gs == null) {
                        scan.errors.add("ST before GS");
                        gs = new ScanGroup();
                        gs.gsTokens = new String[0];
                        gs.gsRaw = "";
                        if (isa == null) {
                            isa = new ScanInterchange();
                            isa.isaTokens = new String[0];
                            isa.isaRaw = "";
                            scan.interchanges.add(isa);
                        }
                        isa.groups.add(gs);
                    }
                    st = new ScanTransaction();
                    st.st01 = tokens.length > 1 ? tokens[1].trim() : "";
                    st.st02 = tokens.length > 2 ? tokens[2].trim() : "";
                    st.st03 = tokens.length > 3 ? tokens[3].trim() : null;
                    st.segments.add(seg);
                    gs.transactions.add(st);
                }
                case "SE" -> {
                    if (st == null) {
                        scan.errors.add("SE without ST");
                    } else {
                        st.segments.add(seg);
                        Integer se01 = count(scan, "SE01", element(tokens, 1), "transaction " + st.st02);
                        if (se01 != null && se01 != st.segments.size()) {
                            scan.errors.add("SE01=" + se01 + " but ST..SE holds " + st.segments.size()
                                + " segments (transaction " + st.st02 + ")");
                        }
                        if (!element(tokens, 2).equals(st.st02)) {
                            scan.errors.add("SE02=" + element(tokens, 2) + " != ST02=" + st.st02);
                        }
                        st = null;
                    }
                }
                case "GE" -> {
                    if (st != null) {
                        missingSe(scan, st, "GE");
                        st = null;
                    }
                    if (gs == null) {
                        scan.errors.add("GE without GS");
                    } else {
                        gs.geRaw = seg;
                        Integer ge01 = count(scan, "GE01", element(tokens, 1), "group " + gs.controlNumber());
                        if (ge01 != null && ge01 != gs.transactions.size()) {
                            scan.errors.add("GE01=" + ge01 + " but group " + gs.controlNumber() + " holds "
                                + gs.transactions.size() + " transaction sets");
                        }
                        if (!element(tokens, 2).equals(gs.controlNumber())) {
                            scan.errors.add("GE02=" + element(tokens, 2) + " != GS06=" + gs.controlNumber());
                        }
                    }
                    gs = null;
                }
                case "IEA" -> {
                    closeGroup(scan, gs, st, "IEA");
                    if (isa == null) {
                        scan.errors.add("IEA without ISA");
                    } else {
                        isa.ieaRaw = seg;
                        Integer iea01 = count(scan, "IEA01", element(tokens, 1), "interchange " + isa.controlNumber());
                        if (iea01 != null && iea01 != isa.groups.size()) {
                            scan.errors.add("IEA01=" + iea01 + " but interchange " + isa.controlNumber() + " holds "
                                + isa.groups.size() + " functional groups");
                        }
                        if (!element(tokens, 2).equals(isa.controlNumber())) {
                            scan.errors.add("IEA02=" + element(tokens, 2) + " != ISA13=" + isa.controlNumber());
                        }
                    }
                    gs = null;
                    st = null;
                    isa = null;
                }
                default -> {
                    if (st != null) {
                        st.segments.add(seg);
                    } else if (isa != null) {
                        scan.errors.add("segment " + id + " outside any transaction set");
                    }
                }
            }
        }
        closeGroup(scan, gs, st, "the end of the file");
        closeInterchange(scan, isa);
        return scan;
    }

    /** A segment that opens an interchange, whatever element separator it was written with. */
    private static boolean isIsa(String seg) {
        return seg.length() > 3 && seg.regionMatches(true, 0, "ISA", 0, 3) && !Character.isLetterOrDigit(seg.charAt(3));
    }

    /** Every ISA in a file must declare the first one's delimiters: the segments are split once. */
    private static void requireSameDelimiters(String seg, Separators seps, int number) throws X12ParseException {
        Separators declared;
        try {
            declared = Separators.fromIsa(seg + seps.segment());
        } catch (X12ParseException e) {
            throw new X12ParseException(e.getMessage() + " (interchange " + number + ")");
        }
        if (!declared.sameDelimiters(seps)) {
            throw new X12ParseException("bad-isa: interchange " + number + " declares delimiters element='"
                + declared.element() + "', repetition='" + declared.repetition() + "', component='"
                + declared.component() + "' but the file's first ISA declares element='" + seps.element()
                + "', repetition='" + seps.repetition() + "', component='" + seps.component() + "'");
        }
    }

    /**
     * A transaction set or group still open when {@code next} (the next GS, the IEA, the next
     * ISA or the end of the file) arrives lacks its SE or GE.
     */
    private static void closeGroup(Scan scan, ScanGroup gs, ScanTransaction st, String next) {
        if (st != null) {
            missingSe(scan, st, next);
        }
        if (gs != null) {
            scan.errors.add("GS " + gs.controlNumber() + " has no GE");
        }
    }

    /**
     * Non-fatal, like the SE01/SE02 checks: the transaction set still holds every segment it
     * was sent with, and imsweb does not need SE to close ST_LOOP.
     */
    private static void missingSe(Scan scan, ScanTransaction st, String next) {
        scan.errors.add("missing-se: transaction " + st.st02 + " has no SE before " + next);
    }

    private static void closeInterchange(Scan scan, ScanInterchange isa) {
        if (isa != null && isa.ieaRaw == null && !isa.isaRaw.isEmpty()) {
            scan.errors.add("ISA " + isa.controlNumber() + " has no IEA");
        }
    }

    /** A trailer count element (SE01, GE01, IEA01), or null — with an error recorded — when it is not a number. */
    private static Integer count(Scan scan, String name, String declared, String subject) {
        try {
            return Integer.valueOf(declared);
        } catch (NumberFormatException e) {
            scan.errors.add(name + " '" + declared + "' is not a number (" + subject + ")");
            return null;
        }
    }

    private static String element(String[] tokens, int n) {
        return tokens.length > n ? tokens[n].trim() : "";
    }
}

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
 * The imsweb {@link X12Reader} wrapper (DESIGN §4.2b): decodes the bytes, detects the
 * delimiters from the ISA, reads GS08 from the first GS <em>before</em> choosing the
 * {@link X12Reader.FileType} via {@link TransactionTypes#fileTypeFor}, parses, and hands
 * back one {@link Interchange} per ISA with the imsweb {@link Loop} tree of every
 * transaction set plus its verbatim ST..SE segments.
 *
 * <p>Things imsweb does not do that this wrapper does:
 * <ul>
 *   <li><b>GS08 aliases.</b> imsweb compares the first GS08 <em>exactly</em> against the
 *       FileType's canonical id ({@code 005010X222A1}); the wire often carries
 *       {@code 005010X222} or {@code 005010X223A1}. The text fed to imsweb has every GS08
 *       rewritten to {@link TransactionTypes#canonical}; the raw segments kept per
 *       transaction are untouched.</li>
 *   <li><b>Bare ST..SE files</b> are wrapped by {@link EnvelopeSynthesizer} when allowed
 *       (DESIGN §4.3); {@link ParsedFile#synthetic()} reports it.</li>
 *   <li><b>SE01 / SE02 checks.</b> imsweb never verifies the segment count or the
 *       trailing control number; both are checked here and reported as non-fatal
 *       {@link ParsedFile#errors()} (they count toward {@code parserErrorCount}).</li>
 *   <li><b>ST01 vs guide.</b> A 276 under {@code 005010X212} (whose only imsweb map is the
 *       277) is refused as {@code unsupported-transaction} instead of being fed to the
 *       wrong map.</li>
 * </ul>
 *
 * <p>Non-fatal imsweb errors are file-level (imsweb does not attribute them to a
 * transaction set); the caller stamps the file's count on every row.
 */
public final class X12Parse {

    /** ISA09 {@code YYMMDD} + ISA10 {@code HHMM}. */
    private static final DateTimeFormatter ISA_DATE = DateTimeFormatter.ofPattern("yyMMdd");
    private static final DateTimeFormatter ISA_TIME = DateTimeFormatter.ofPattern("HHmm");

    private X12Parse() {
    }

    // ---- result model ----------------------------------------------------------------

    /** Everything parsed from one file. */
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
    public record Interchange(String[] isaTokens, String isaRaw, String ieaRaw, List<FunctionalGroup> groups, Loop loop) {

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

    /** One GS..GE. */
    public record FunctionalGroup(String[] gsTokens, String gsRaw, String geRaw, List<Transaction> transactions, Loop loop) {

        public String gs(int n) {
            return n < gsTokens.length ? gsTokens[n].trim() : "";
        }

        public String controlNumber() {
            return gs(6);
        }

        public String gs08() {
            return gs(8);
        }
    }

    /**
     * One ST..SE: the imsweb {@link Loop} (id {@code ST_LOOP}) and the verbatim segments
     * from the file. {@link #rawX12} rebuilds a complete, re-parseable single-transaction
     * interchange: the ISA and GS context lines, the ST..SE segments, then the file's own
     * GE and IEA (verbatim — their counts describe the original group, not this slice).
     */
    public record Transaction(Loop loop, String st01, String st02, String st03, List<String> segments,
                              Interchange interchange, FunctionalGroup group, Separators separators) {

        public String controlNumber() {
            return st02;
        }

        public String rawX12() {
            StringBuilder sb = new StringBuilder();
            String term = separators.segment() + separators.lineBreak();
            sb.append(interchange.isaRaw()).append(term);
            sb.append(group.gsRaw()).append(term);
            for (String s : segments) {
                sb.append(s).append(term);
            }
            if (group.geRaw() != null) {
                sb.append(group.geRaw()).append(term);
            }
            if (interchange.ieaRaw() != null) {
                sb.append(interchange.ieaRaw()).append(term);
            }
            return sb.toString();
        }

        public byte[] rawX12Bytes() {
            return rawX12().getBytes(StandardCharsets.UTF_8);
        }
    }

    // ---- entry points ----------------------------------------------------------------

    public static ParsedFile parse(byte[] bytes, boolean allowBareTransactionSets) throws X12ParseException {
        return parse(bytes, allowBareTransactionSets, Clock.systemUTC());
    }

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

        // Guide selection BEFORE imsweb sees the text (DESIGN §4.2b).
        String rawGs08 = null;
        String canonical = null;
        for (ScanInterchange si : scan.interchanges) {
            for (ScanGroup sg : si.groups) {
                String g = sg.gs08();
                if (g.isEmpty()) {
                    throw new X12ParseException("bad-gs: GS segment has no GS08 (" + abbreviate(sg.gsRaw) + ")");
                }
                Optional<String> c = TransactionTypes.canonical(g);
                if (c.isEmpty()) {
                    throw new X12ParseException("unsupported-guide: GS08 '" + g + "' is not a supported implementation guide");
                }
                if (canonical == null) {
                    canonical = c.get();
                    rawGs08 = g;
                } else if (!canonical.equals(c.get())) {
                    throw new X12ParseException("mixed-guides: file carries functional groups for both " + canonical
                        + " and " + c.get() + "; one guide per file is supported");
                }
            }
        }
        if (canonical == null) {
            throw new X12ParseException("bad-gs: no functional group (GS) after the ISA");
        }
        Optional<X12Reader.FileType> fileType = TransactionTypes.fileTypeFor(canonical);
        if (fileType.isEmpty()) {
            throw new X12ParseException("unsupported-guide: no imsweb 005010 map for " + canonical
                + " (GS08 '" + rawGs08 + "')");
        }
        String expectedSt01 = expectedSt01(TransactionTypes.transactionType(canonical, null));
        for (ScanInterchange si : scan.interchanges) {
            for (ScanGroup sg : si.groups) {
                for (ScanTransaction st : sg.transactions) {
                    if (!st.st01.equals(expectedSt01)) {
                        throw new X12ParseException("unsupported-transaction: ST01 " + st.st01 + " under guide "
                            + canonical + " (its map is the " + expectedSt01 + ")");
                    }
                }
            }
        }

        String fed = scan.needsRewrite(canonical) ? scan.rewriteForImsweb(canonical) : text;
        X12Reader reader;
        try {
            reader = new X12Reader(fileType.get(), new StringReader(fed));
        } catch (IOException | RuntimeException e) {
            throw new X12ParseException("parser-failure: imsweb threw " + e, List.of());
        }
        List<String> errors = new ArrayList<>(scan.errors);
        errors.addAll(dropClosingSegmentFalsePositives(reader.getErrors(), reader.getLoops()));
        if (!reader.getFatalErrors().isEmpty()) {
            throw new X12ParseException("fatal: imsweb could not parse the file as " + canonical
                + (errors.isEmpty() ? "" : "; errors: " + errors), reader.getFatalErrors());
        }

        // Pair the imsweb loop tree with the scanned spans (both in document order).
        List<Loop> isaLoops = reader.getLoops();
        if (isaLoops.size() != scan.interchanges.size()) {
            throw new X12ParseException("structure-mismatch: imsweb produced " + isaLoops.size()
                + " interchange loop(s) but the file holds " + scan.interchanges.size() + " ISA segment(s)");
        }
        List<Interchange> interchanges = new ArrayList<>();
        for (int i = 0; i < isaLoops.size(); i++) {
            ScanInterchange si = scan.interchanges.get(i);
            Loop isaLoop = isaLoops.get(i);
            List<Loop> gsLoops = childLoops(isaLoop, "GS_LOOP");
            if (gsLoops.size() != si.groups.size()) {
                throw new X12ParseException("structure-mismatch: imsweb produced " + gsLoops.size()
                    + " functional group loop(s) but interchange " + (i + 1) + " holds " + si.groups.size() + " GS segment(s)");
            }
            List<FunctionalGroup> groups = new ArrayList<>();
            Interchange interchange = new Interchange(si.isaTokens, si.isaRaw, si.ieaRaw, groups, isaLoop);
            for (int g = 0; g < gsLoops.size(); g++) {
                ScanGroup sg = si.groups.get(g);
                Loop gsLoop = gsLoops.get(g);
                List<Loop> stLoops = childLoops(gsLoop, "ST_LOOP");
                if (stLoops.size() != sg.transactions.size()) {
                    throw new X12ParseException("structure-mismatch: imsweb produced " + stLoops.size()
                        + " transaction loop(s) but group " + sg.controlNumber() + " holds " + sg.transactions.size()
                        + " ST..SE span(s)");
                }
                List<Transaction> txs = new ArrayList<>();
                FunctionalGroup group = new FunctionalGroup(sg.gsTokens, sg.gsRaw, sg.geRaw, txs, gsLoop);
                for (int t = 0; t < stLoops.size(); t++) {
                    ScanTransaction st = sg.transactions.get(t);
                    txs.add(new Transaction(stLoops.get(t), st.st01, st.st02, st.st03, List.copyOf(st.segments),
                        interchange, group, seps));
                }
                groups.add(group);
            }
            interchanges.add(interchange);
        }
        return new ParsedFile(List.copyOf(interchanges), seps, canonical, rawGs08, fileType.get(),
            List.copyOf(errors), synthetic);
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
        int gsIndex;
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
    }

    private static final class Scan {
        final List<String> segments = new ArrayList<>();
        final List<ScanInterchange> interchanges = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        Separators seps;

        /**
         * True when any GS08, or any ST03 spelling the same guide, is not the canonical id, or
         * when an ISA11 is a repetition separator imsweb's control map does not list.
         */
        boolean needsRewrite(String canonical) {
            for (ScanInterchange i : interchanges) {
                if (needsIsa11Rewrite(i)) {
                    return true;
                }
                for (ScanGroup g : i.groups) {
                    if (!canonical.equals(g.gs08())) {
                        return true;
                    }
                    for (ScanTransaction t : g.transactions) {
                        if (isAliasOf(t.st03, canonical)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        /**
         * imsweb's 00501 control map lists {@code U} and {@code ^} as ISA11's only valid codes, so
         * an interchange using any other (legal) repetition separator, e.g. {@code >}, never
         * starts ISA_LOOP. imsweb never splits repetitions itself, so the character is
         * irrelevant to it; the materializer splits with the file's real {@link Separators}.
         */
        private boolean needsIsa11Rewrite(ScanInterchange i) {
            return i.isaTokens.length > 11 && i.isaTokens[11].length() == 1
                && !"^".equals(i.isaTokens[11]) && !"U".equals(i.isaTokens[11]);
        }

        /**
         * The same segments re-joined with every GS08 — and every ST03 that spells the same
         * guide (imsweb's X223 map lists {@code 005010X223A2} as ST03's only valid code, so a
         * bare {@code 005010X223} never starts ST_LOOP) — replaced by the canonical id, and
         * ISA11 replaced by {@code ^} where imsweb would reject it. The raw segments kept per
         * transaction are never touched.
         */
        String rewriteForImsweb(String canonical) {
            StringBuilder sb = new StringBuilder();
            String term = seps.segment() + seps.lineBreak();
            for (String s : segments) {
                String[] tokens = seps.splitElements(s);
                if ("ISA".equals(tokens[0]) && tokens.length > 11 && tokens[11].length() == 1
                        && !"^".equals(tokens[11]) && !"U".equals(tokens[11])) {
                    tokens[11] = "^";
                    sb.append(String.join(String.valueOf(seps.element()), tokens));
                } else if ("GS".equals(tokens[0]) && tokens.length > 8) {
                    tokens[8] = canonical;
                    sb.append(String.join(String.valueOf(seps.element()), tokens));
                } else if ("ST".equals(tokens[0]) && tokens.length > 3 && isAliasOf(tokens[3].trim(), canonical)) {
                    tokens[3] = canonical;
                    sb.append(String.join(String.valueOf(seps.element()), tokens));
                } else {
                    sb.append(s);
                }
                sb.append(term);
            }
            return sb.toString();
        }

        private static boolean isAliasOf(String id, String canonical) {
            return id != null && !id.isEmpty() && !canonical.equals(id)
                && TransactionTypes.canonical(id).map(canonical::equals).orElse(false);
        }
    }

    private static Scan scan(String text, Separators seps) {
        Scan scan = new Scan();
        scan.seps = seps;
        for (String s : text.split(Pattern.quote(String.valueOf(seps.segment())))) {
            String t = s.strip();
            if (!t.isEmpty()) {
                scan.segments.add(t);
            }
        }
        ScanInterchange isa = null;
        ScanGroup gs = null;
        ScanTransaction st = null;
        for (String seg : scan.segments) {
            String[] tokens = seps.splitElements(seg);
            String id = tokens[0].toUpperCase(Locale.ROOT);
            if (st != null) {
                st.segments.add(seg);
            }
            switch (id) {
                case "ISA" -> {
                    isa = new ScanInterchange();
                    isa.isaTokens = tokens;
                    isa.isaRaw = seg;
                    scan.interchanges.add(isa);
                    gs = null;
                    st = null;
                }
                case "GS" -> {
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
                        String se01 = tokens.length > 1 ? tokens[1].trim() : "";
                        String se02 = tokens.length > 2 ? tokens[2].trim() : "";
                        try {
                            int declared = Integer.parseInt(se01);
                            if (declared != st.segments.size()) {
                                scan.errors.add("SE01=" + declared + " but ST..SE holds " + st.segments.size()
                                    + " segments (transaction " + st.st02 + ")");
                            }
                        } catch (NumberFormatException e) {
                            scan.errors.add("SE01 '" + se01 + "' is not a number (transaction " + st.st02 + ")");
                        }
                        if (!se02.equals(st.st02)) {
                            scan.errors.add("SE02=" + se02 + " != ST02=" + st.st02);
                        }
                        st = null;
                    }
                }
                case "GE" -> {
                    if (gs != null) {
                        gs.geRaw = seg;
                    }
                    if (st != null) {
                        scan.errors.add("GE inside transaction " + st.st02);
                        st = null;
                    }
                    gs = null;
                }
                case "IEA" -> {
                    if (isa != null) {
                        isa.ieaRaw = seg;
                    }
                    gs = null;
                    st = null;
                    isa = null;
                }
                default -> {
                    if (st == null && isa != null) {
                        scan.errors.add("segment " + id + " outside any transaction set");
                    }
                }
            }
        }
        if (st != null) {
            scan.errors.add("transaction " + st.st02 + " has no SE");
        }
        return scan;
    }
}

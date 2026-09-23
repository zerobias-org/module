package com.zerobias.module.x12.inbox;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zerobias.module.x12.materializer.Materializer;
import com.zerobias.module.x12.materializer.StructureResolver;
import com.zerobias.module.x12.parser.TransactionTypes;
import com.zerobias.module.x12.parser.X12Parse;
import com.zerobias.module.x12.parser.X12ParseException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Conformance run over the git-ignored x12.org example set (DESIGN §13), populated locally
 * by {@code java/scripts/fetch-x12org-examples.py}; skips itself when the directory is
 * absent so CI stays green. Every file whose guide has a v1 imsweb map must parse with zero
 * fatal errors and materialize a full tree with at least one transaction. Guides without a
 * map (270/271 X279) and transaction sets on the wrong side of a map (276 under X212) must
 * be refused with a stable {@code unsupported-*} kind, never a crash. Source defects x12.org
 * itself carries are read from each file's {@code .meta.json} {@code notes} (written by the
 * fetcher, never hardcoded here): the SE-count ones ({@code source SE01=…}) are expected as
 * non-fatal parser errors — imsweb never checks SE01, the wrapper does — while the 837 CLM
 * element-separator ones ({@code source CLM05 …}, see {@link #KNOWN_FATAL_NOTE}) are
 * expected to fail with {@code fatal:}.
 */
class X12OrgConformanceIT {

    private static final Path DIR = Path.of("src/test/resources/x12org");

    /**
     * The {@code notes} prefix the fetcher writes for an example that cannot parse because
     * the example text itself is defective: the CLM segment has one element separator too
     * few ({@code 827**22:B:1} where the TR3 has {@code 827***22:B:1}), so CLM05..CLM09 sit
     * one position left and imsweb's loop-start code validation rejects the claim (CLM07 'Y'
     * is not in A|B|C). Restoring the separator makes every one of them parse. Such a file
     * must fail with {@code fatal:} — if one starts parsing, x12.org fixed the page and a
     * refetch drops the note.
     */
    static final String KNOWN_FATAL_NOTE = "source CLM05";

    @Test
    void everyX12OrgExampleParsesAndMaterializes() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(DIR), "x12org examples not fetched (git-ignored); skipping");
        List<Path> files;
        try (Stream<Path> s = Files.walk(DIR)) {
            files = s.filter(p -> p.toString().endsWith(".x12")).sorted().toList();
        }
        Assumptions.assumeFalse(files.isEmpty(), "x12org directory is empty; skipping");

        StructureResolver resolver = new StructureResolver();
        List<String> failures = new ArrayList<>();
        int parsed = 0;
        int unsupported = 0;
        int defective = 0;
        int transactions = 0;
        int nonFatal = 0;
        for (Path f : files) {
            JsonObject meta = meta(f);
            String ts = meta.has("transactionSet") ? meta.get("transactionSet").getAsString() : "?";
            String gs08 = meta.has("gs08") ? meta.get("gs08").getAsString() : null;
            List<String> notes = new ArrayList<>();
            if (meta.has("notes") && meta.get("notes").isJsonArray()) {
                for (JsonElement n : meta.getAsJsonArray("notes")) {
                    notes.add(n.getAsString());
                }
            }
            String defect = notes.stream().filter(n -> n.startsWith(KNOWN_FATAL_NOTE)).findFirst().orElse(null);
            boolean mapped = gs08 != null && TransactionTypes.fileTypeFor(gs08).isPresent();
            boolean wrongSide = mapped && !X12Parse.expectedSt01(TransactionTypes.transactionType(gs08, null))
                .equals(firstSt01(f));
            try {
                X12Parse.ParsedFile p = X12Parse.parse(Files.readAllBytes(f), true);
                if (!mapped || wrongSide) {
                    failures.add(f + ": expected an unsupported-* refusal for " + ts + "/" + gs08 + " but it parsed");
                    continue;
                }
                if (defect != null) {
                    failures.add(f + ": meta notes a known source defect (" + defect + ") but it parsed; refetch it");
                    continue;
                }
                parsed++;
                assertTrue(p.transactionCount() >= 1, f + ": no transaction");
                transactions += p.transactionCount();
                nonFatal += p.errors().size();
                for (String note : notes) {
                    String expect = note.startsWith("source ") ? note.substring(7) : note;
                    boolean seen = p.errors().stream().anyMatch(e -> e.contains(expect));
                    if (!seen) {
                        failures.add(f + ": documented source defect not reported: '" + note + "' (errors: " + p.errors() + ")");
                    }
                }
                Materializer m = resolver.materializerFor(p.gs08(), p.separators())
                    .orElseThrow(() -> new AssertionError(f + ": no structure index for " + p.gs08()));
                for (X12Parse.Transaction tx : p.transactions()) {
                    Map<String, Object> tree = m.materializeTransaction(tx.loop());
                    JsonObject json = JsonParser.parseString(m.toJson(tree)).getAsJsonObject();
                    assertTrue(json.has("st") && json.has("se"), f + ": st/se missing: " + json.keySet());
                    assertTrue(json.keySet().size() >= 3, f + ": envelope-only tree " + json.keySet());
                    assertEquals(tx.st02(), json.getAsJsonObject("st").get("st02").getAsString(), f.toString());
                    assertFalse(json.has("header") && json.getAsJsonObject("header").entrySet().isEmpty(), f + ": empty header");
                    // rawX12 must round-trip through the parser.
                    X12Parse.ParsedFile again = X12Parse.parse(tx.rawX12().getBytes(StandardCharsets.UTF_8), true);
                    assertEquals(1, again.transactionCount(), f + ": rawX12 re-parse");
                }
            } catch (X12ParseException e) {
                if (defect != null) {
                    defective++;
                    if (!e.getMessage().startsWith("fatal:")) {
                        failures.add(f + ": known source defect expected a fatal parse, got: " + e.getMessage());
                    }
                } else if (!mapped || wrongSide) {
                    unsupported++;
                    if (!e.getMessage().startsWith("unsupported-")) {
                        failures.add(f + ": expected unsupported-* but got: " + e.getMessage());
                    }
                } else {
                    failures.add(f + ": " + e.getMessage());
                }
            }
        }
        System.out.printf("x12org conformance: %d files, %d parsed (%d transactions, %d non-fatal errors), "
            + "%d unsupported by design, %d known source defects%n",
            files.size(), parsed, transactions, nonFatal, unsupported, defective);
        if (!failures.isEmpty()) {
            fail(String.join("\n", failures));
        }
        assertTrue(parsed > 0);
    }

    private static JsonObject meta(Path x12) throws IOException {
        Path m = Path.of(x12.toString().replaceFirst("\\.x12$", ".meta.json"));
        if (!Files.exists(m)) {
            return new JsonObject();
        }
        return JsonParser.parseString(Files.readString(m)).getAsJsonObject();
    }

    /** ST01 of the first ST segment: the segment terminator is ISA char 106 (or {@code ~} for a bare file). */
    private static String firstSt01(Path f) throws IOException {
        String text = Files.readString(f, StandardCharsets.ISO_8859_1).strip();
        if (text.length() < 4) {
            return "";
        }
        char elem = text.startsWith("ISA") ? text.charAt(3) : text.charAt(2);
        char term = text.startsWith("ISA") && text.length() > 105 ? text.charAt(105) : '~';
        for (String seg : text.split(java.util.regex.Pattern.quote(String.valueOf(term)))) {
            String s = seg.strip();
            if (s.startsWith("ST" + elem)) {
                String[] t = s.split(java.util.regex.Pattern.quote(String.valueOf(elem)), -1);
                return t.length > 1 ? t[1].trim() : "";
            }
        }
        return "";
    }

    @SuppressWarnings("unused")
    private static int size(JsonArray a) {
        return a.size();
    }
}

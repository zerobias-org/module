package com.zerobias.module.hl7.filter;

import com.google.gson.Gson;
import com.zerobias.litefilter.Expression;
import com.zerobias.module.hl7.buffer.BufferRow;
import com.zerobias.module.hl7.buffer.BufferStore;
import com.zerobias.module.hl7.buffer.MessageStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 5 validation (DESIGN §2.6): the {@link Hl7SqlAdapter} renders RFC4515
 * filters to SQLite WHERE clauses that select <em>exactly</em> the rows
 * lite-filter's in-memory {@code matches()} evaluator accepts — proven by
 * running both over the same seeded buffer.
 *
 * <p>Parity is asserted only for operators the evaluator actually supports.
 * (The evaluator's {@code >}/{@code >=} are numeric-only and throw on date
 * strings, so {@code receivedAt>=<ISO>} is SQL-only — exercised separately in
 * {@link #designExampleFiltersRunOnSqlite}.)
 */
class Hl7SqlAdapterIT {

    private static final Gson GSON = new Gson();

    /** A buffer message: the JSON object the evaluator sees == the row's mapped_json. */
    private record Msg(String controlId, String status, String receivedAt, Map<String, Object> body) {
        Map<String, Object> asObject() {
            Map<String, Object> o = new LinkedHashMap<>(body);
            o.put("controlId", controlId);
            o.put("status", status);
            o.put("receivedAt", receivedAt);
            return o;
        }
    }

    private static Map<String, Object> nest(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private List<Msg> seed() {
        List<Msg> msgs = new ArrayList<>();
        msgs.add(new Msg("M1", "new", "2026-05-28T10:00:00Z", nest(
            "msh", nest("messageType", nest("messageCode", "ADT"),
                        "sendingApplication", nest("namespaceId", "EPIC")),
            "pid", nest("patientFamilyName", "SMITH", "ageYears", 45,
                        "patientIdentifierList", nest("idNumber", "5551212")))));
        msgs.add(new Msg("M2", "new", "2026-05-28T11:00:00Z", nest(
            "msh", nest("messageType", nest("messageCode", "ORU"),
                        "sendingApplication", nest("namespaceId", "CERNER")),
            "pid", nest("patientFamilyName", "SMYTHE", "ageYears", 30,
                        "patientIdentifierList", nest("idNumber", "9001234")))));
        msgs.add(new Msg("M3", "acked", "2026-05-28T12:00:00Z", nest(
            "msh", nest("messageType", nest("messageCode", "ADT"),
                        "sendingApplication", nest("namespaceId", "epic")),
            "pid", nest("patientFamilyName", "ASHWORTH", "ageYears", 60, "suffix", "JR",
                        "patientIdentifierList", nest("idNumber", "5559999")))));
        msgs.add(new Msg("M4", "new", "2025-12-31T23:59:59Z", nest(
            "msh", nest("messageType", nest("messageCode", "ORM"),
                        "sendingApplication", nest("namespaceId", "EPIC")),
            "pid", nest("patientFamilyName", "BOOTH", "ageYears", 48,
                        "patientIdentifierList", nest("idNumber", "5551313")))));
        return msgs;
    }

    private BufferStore load(Path dir, List<Msg> msgs) throws Exception {
        BufferStore store = new BufferStore(dir.resolve("buffer.db").toString(), false, Clock.systemUTC());
        for (Msg m : msgs) {
            Map<String, Object> obj = m.asObject();
            String json = GSON.toJson(obj);
            BufferRow row = new BufferRow(0, Instant.parse(m.receivedAt()), m.controlId(),
                "ADT_A01", "ADT", "A01", "EPIC", "HOSP", "2.5.1",
                "schema:table:hl7v2.v251.ADT_A01", ("raw-" + m.controlId()).getBytes(),
                json, MessageStatus.fromWire(m.status()), null, null, null);
            assertTrue(store.insert(row), "insert " + m.controlId());
        }
        return store;
    }

    private TreeSet<String> matching(List<Msg> msgs, Expression expr) {
        return msgs.stream()
            .filter(m -> expr.matches(m.asObject()))
            .map(Msg::controlId)
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private TreeSet<String> selected(BufferStore store, String where) throws Exception {
        return store.search(where, 1000).stream()
            .map(BufferRow::controlId)
            .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void evaluatorParityAcrossOperators(@TempDir Path dir) throws Exception {
        List<Msg> msgs = seed();
        List<String> filters = List.of(
            "(status=new)",
            "(status=NEW)",                                  // NOCASE equality
            "(msh.messageType.messageCode=ADT)",
            "(msh.sendingApplication.namespaceId=epic)",     // NOCASE on a JSON path
            "(pid.patientIdentifierList.idNumber=5551*)",    // wildcard -> LIKE
            "(pid.patientFamilyName:contains:MIT)",
            "(pid.patientFamilyName:startsWith:SM)",
            "(pid.patientFamilyName:endsWith:TH)",
            "(pid.ageYears:between:40,50)",
            "(pid.suffix=*)",                                // presence
            "(&(status=new)(msh.messageType.messageCode=ADT))",
            "(|(msh.messageType.messageCode=ADT)(msh.messageType.messageCode=ORU))",
            "(!(status=acked))",
            "(receivedAt:year:2026)"
        );

        try (BufferStore store = load(dir, msgs)) {
            for (String f : filters) {
                Expression expr = Hl7Filter.parse(f);
                String where = Hl7Filter.toWhereClause(expr);
                assertNotNull(where, "where for " + f);
                TreeSet<String> expected = matching(msgs, expr);
                TreeSet<String> actual = selected(store, where);
                assertEquals(expected, actual,
                    "filter " + f + "  ->  WHERE " + where);
            }
        }
    }

    @Test
    void designExampleFiltersRunOnSqlite(@TempDir Path dir) throws Exception {
        List<Msg> msgs = seed();
        try (BufferStore store = load(dir, msgs)) {
            // §2.6 example 1: ADT from EPIC, since 2026-05-27, un-acked. Uses the
            // date-only form the interface's FilterSyntax.md documents
            // (modified>=2025-01-01) — a full ISO datetime is NOT parseable, see
            // parserRejectsTimeOfDayInComparison(). receivedAt>= is SQL-only
            // (the evaluator can't compare date strings).
            String f1 = "(&(msh.sendingApplication.namespaceId=EPIC)"
                + "(msh.messageType.messageCode=ADT)"
                + "(receivedAt>=2026-05-27)"
                + "(status=new))";
            String w1 = Hl7Filter.toWhereClause(f1);
            assertTrue(w1.contains("unixepoch("), "date literal coerced to epoch-millis: " + w1);
            assertEquals(new TreeSet<>(List.of("M1")), selected(store, w1), "f1 -> " + w1);

            // §2.6 example 2: multiple message types.
            String f2 = "(|(msh.messageType.messageCode=ADT)(msh.messageType.messageCode=ORM))";
            assertEquals(new TreeSet<>(List.of("M1", "M3", "M4")), selected(store, Hl7Filter.toWhereClause(f2)));

            // §2.6 example 3: patient-id prefix, case-insensitive.
            String f3 = "(pid.patientIdentifierList.idNumber=5551*)";
            assertEquals(new TreeSet<>(List.of("M1", "M4")), selected(store, Hl7Filter.toWhereClause(f3)));
        }
    }

    @Test
    void parserRejectsTimeOfDayInComparison() {
        // Locks a real lite-filter limitation: its RFC4515 parser treats the first
        // colon as the start of a :function: operator, so an ISO datetime value
        // (2026-05-27T00:00:00Z) in a >= clause is mis-read as operator ':00:'.
        // Date-only literals and the :withinDays:/:year: extensions are the
        // supported ways to filter by time. (Fixing the parser is org/util scope.)
        assertThrows(IllegalArgumentException.class,
            () -> Hl7Filter.parse("(receivedAt>=2026-05-27T00:00:00Z)"));
    }

    @Test
    void withinDaysEmitsEpochMillisForm(@TempDir Path dir) throws Exception {
        // received_at is epoch-millis, so the relative-date op must convert.
        String where = Hl7Filter.toWhereClause("(receivedAt:withinDays:30)");
        assertTrue(where.contains("received_at >= (unixepoch('now', '-30 days') * 1000)"),
            "epoch-millis withinDays form: " + where);

        // A JSON-path date stays text and uses unixepoch on the extracted value.
        String json = Hl7Filter.toWhereClause("(pv1.admitDateTime:withinDays:7)");
        assertTrue(json.contains("unixepoch(json_extract(mapped_json, '$.pv1.admitDateTime'))"),
            "json-path withinDays form: " + json);

        try (BufferStore store = load(dir, seed())) {
            // executes without error against SQLite
            assertNotNull(store.search(where, 10));
        }
    }
}

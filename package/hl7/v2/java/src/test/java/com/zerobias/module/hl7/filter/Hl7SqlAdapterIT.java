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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * (As of lite-filter 1.0.4 the evaluator's {@code >}/{@code >=} compare ISO-8601
 * strings lexicographically — which matches chronological order — so a
 * {@code receivedAt>=<ISO>} datetime works in-memory too; its epoch-millis SQL
 * rendering is exercised in {@link #timeOfDayInComparisonRendersEpochMillis}.)
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
                "ADT_A01", "ADT", "A01", "EPIC", "HOSP", "2.7",
                "schema:table:hl7v2.v27.ADT_A01", ("raw-" + m.controlId()).getBytes(),
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
            // (modified>=2025-01-01); the full ISO-datetime form is covered by
            // timeOfDayInComparisonRendersEpochMillis().
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
    void timeOfDayInComparisonRendersEpochMillis(@TempDir Path dir) throws Exception {
        // Regression for the lite-filter parser bug (fixed in lite-filter 1.0.4): the
        // colons in a full ISO-8601 datetime's time-of-day (00:00:00) used to be
        // mis-read as a :function: operator, throwing "Unknown operator: :00:". The
        // datetime now parses and the adapter renders it to the epoch-millis form.
        String filter = "(receivedAt>=2026-05-27T00:00:00Z)";
        String where = Hl7Filter.toWhereClause(filter);
        assertTrue(where.contains("received_at >= (unixepoch('2026-05-27T00:00:00Z') * 1000)"),
            "time-of-day datetime coerced to epoch-millis: " + where);

        try (BufferStore store = load(dir, seed())) {
            // M1-M3 are 2026-05-28; M4 is 2025-12-31 and is excluded.
            assertEquals(new TreeSet<>(List.of("M1", "M2", "M3")), selected(store, where),
                filter + " -> " + where);
        }
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

    @Test
    void apostropheInValueIsEscapedNotInjected(@TempDir Path dir) throws Exception {
        // A value carrying a single quote (O'BRIEN) must be doubled into the SQL
        // literal ('O''BRIEN'), not break the statement. The selection result is the
        // proof: exactly the O'BRIEN row, no SQL error, no over/under-match.
        List<Msg> msgs = new ArrayList<>(seed());
        msgs.add(new Msg("M5", "new", "2026-05-28T13:00:00Z", nest(
            "msh", nest("messageType", nest("messageCode", "ADT"),
                        "sendingApplication", nest("namespaceId", "EPIC")),
            "pid", nest("patientFamilyName", "O'BRIEN", "ageYears", 50,
                        "patientIdentifierList", nest("idNumber", "5551414")))));
        try (BufferStore store = load(dir, msgs)) {
            String where = Hl7Filter.toWhereClause("(pid.patientFamilyName=O'BRIEN)");
            assertTrue(where.contains("O''BRIEN"), "apostrophe doubled in SQL literal: " + where);
            assertEquals(new TreeSet<>(List.of("M5")), selected(store, where));
        }
    }

    @Test
    void maliciousPropertyPathIsRejectedNotInjected() {
        // Property-path segments are restricted to [A-Za-z0-9_]; an injection attempt
        // in the attribute can never be rendered into the SQL as raw text. It is
        // rejected — at the adapter's segment guard or earlier at parse — never run.
        assertThrows(IllegalArgumentException.class,
            () -> Hl7Filter.toWhereClause("(pid.fam-ily=SMITH)")); // hyphen: adapter segment guard
        assertThrows(IllegalArgumentException.class,
            () -> Hl7Filter.toWhereClause("(pid.x');DROP TABLE messages;--=X)"));
    }

    @Test
    void provenancePropertiesRenderToDenormalizedColumns() {
        // The fast search axes (version / port / structure / sender) resolve to real
        // columns, not json_extract over mapped_json — so multi-axis search is indexed.
        assertTrue(Hl7Filter.toWhereClause("(hl7Version=2.4)").contains("hl7_version"));
        assertTrue(Hl7Filter.toWhereClause("(sourcePort=Scheduling SIU)").contains("source_port"));
        assertTrue(Hl7Filter.toWhereClause("(messageStructure=ADT_A01)").contains("message_structure"));
        assertTrue(Hl7Filter.toWhereClause("(sendingApp=EPIC)").contains("sending_app"));
        assertTrue(Hl7Filter.toWhereClause("(sendingFacility=GPAMB)").contains("sending_facility"));
        assertFalse(Hl7Filter.toWhereClause("(hl7Version=2.4)").contains("json_extract"),
            "denormalized column, not a JSON path");
        // A message-body field still routes through json_extract (unchanged).
        assertTrue(Hl7Filter.toWhereClause("(pid.setIDPID=1)").contains("json_extract"));
    }
}

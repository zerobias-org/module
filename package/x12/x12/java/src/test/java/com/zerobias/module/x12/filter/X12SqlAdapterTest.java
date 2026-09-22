package com.zerobias.module.x12.filter;

import com.google.gson.Gson;
import com.zerobias.litefilter.Expression;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.Status;
import com.zerobias.module.x12.buffer.TransactionRow;
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
 * DESIGN §2.6: the {@link X12SqlAdapter} renders RFC4515 filters to SQLite WHERE
 * clauses that select <em>exactly</em> the rows lite-filter's in-memory
 * {@code matches()} evaluator accepts — proven by running both over the same seeded
 * buffer. Envelope properties hit real columns; body paths go through json_extract.
 */
class X12SqlAdapterTest {

    private static final Gson GSON = new Gson();

    /** A buffered transaction: the JSON object the evaluator sees == mapped_json + envelope. */
    private record Tx(String key, String type, String gs08, String sender, String status, String receivedAt,
                      Map<String, Object> body) {
        Map<String, Object> asObject() {
            Map<String, Object> o = new LinkedHashMap<>(body);
            o.put("elementKey", key);
            o.put("transactionType", type);
            o.put("gs08", gs08);
            o.put("senderId", sender);
            o.put("status", status);
            o.put("receivedAt", receivedAt);
            o.put("sourceName", "inbox");
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

    private List<Tx> seed() {
        List<Tx> txs = new ArrayList<>();
        txs.add(new Tx("/in/a.835:1:0001", "835", "005010X221A1", "ABCPAYER", "new", "2026-09-02T10:00:00Z",
            nest("loop2100", nest("clp", nest("clp01", "CLAIM001", "clp02", 1, "clp04", 1500.25)))));
        txs.add(new Tx("/in/a.835:1:0002", "835", "005010X221A1", "abcpayer", "acked", "2026-09-03T11:00:00Z",
            nest("loop2100", nest("clp", nest("clp01", "CLAIM002", "clp02", 4, "clp04", 0)))));
        txs.add(new Tx("/in/b.837:7:0001", "837P", "005010X222A1", "SUBMIT1", "new", "2026-08-15T12:00:00Z",
            nest("loop2000A", nest("hl", nest("hl01", "1")), "note", "O'BRIEN")));
        txs.add(new Tx("/in/c.837:8:0001", "837I", "005010X223A2", "SUBMIT2", "new", "2025-12-31T23:59:59Z",
            nest("loop2000A", nest("hl", nest("hl01", "1")))));
        return txs;
    }

    private BufferStore load(Path dir, List<Tx> txs) throws Exception {
        BufferStore store = new BufferStore(dir.resolve("buffer.db").toString(), false, Clock.systemUTC());
        for (Tx t : txs) {
            TransactionRow row = new TransactionRow(0, t.key(), t.key().substring(0, t.key().indexOf(':')), "inbox",
                Instant.parse(t.receivedAt()), "000000001", "1", "0001", t.gs08(), t.type(), t.sender(), "RCV",
                null, "schema:table:x12." + t.gs08() + "." + t.type(), ("raw-" + t.key()).getBytes(),
                GSON.toJson(t.body()), 0, TransactionRow.ENVELOPE_FILE, Status.fromWire(t.status()),
                null, null, null);
            assertTrue(store.insertTransaction(row), "insert " + t.key());
        }
        return store;
    }

    private TreeSet<String> matching(List<Tx> txs, Expression expr) {
        return txs.stream().filter(t -> expr.matches(t.asObject())).map(Tx::key)
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private TreeSet<String> selected(BufferStore store, String where) throws Exception {
        return store.search(where, 1000).stream().map(TransactionRow::elementKey)
            .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void evaluatorParityAcrossOperators(@TempDir Path dir) throws Exception {
        List<Tx> txs = seed();
        List<String> filters = List.of(
            "(status=new)",
            "(status=NEW)",                                   // NOCASE equality on a column
            "(transactionType=835)",
            "(senderId=abcpayer)",                            // NOCASE on a column
            "(gs08=005010X22*)",                              // wildcard -> LIKE
            "(loop2100.clp.clp01=CLAIM*)",                    // wildcard on a JSON path
            "(loop2100.clp.clp02=1)",                         // DESIGN example: numeric body field
            "(loop2100.clp.clp02!=1)",
            "(loop2100.clp.clp04>1000)",                      // numeric ordering on a JSON path
            "(loop2100.clp.clp04<=1500.25)",
            "(loop2000A.hl.hl01=1)",                          // string "1" in the body still matches
            "(loop2100.clp.clp01:contains:M00)",
            "(loop2100.clp.clp01:startsWith:CLAIM)",
            "(loop2100.clp.clp01:endsWith:002)",
            "(loop2100.clp.clp04:between:1000,2000)",
            "(note=*)",                                       // presence
            "(&(transactionType=835)(senderId=ABCPAYER)(status=new))",
            "(|(transactionType=837P)(transactionType=837I))",   // DESIGN example 2
            "(!(status=acked))",
            "(receivedAt:year:2026)"
        );
        try (BufferStore store = load(dir, txs)) {
            for (String f : filters) {
                Expression expr = X12Filter.parse(f);
                String where = X12Filter.toWhereClause(expr);
                assertNotNull(where, "where for " + f);
                assertEquals(matching(txs, expr), selected(store, where), "filter " + f + "  ->  WHERE " + where);
            }
        }
    }

    @Test
    void designExampleFiltersRunOnSqlite(@TempDir Path dir) throws Exception {
        try (BufferStore store = load(dir, seed())) {
            // §2.6 example 1: 835 from ABCPAYER since 2026-09-01, un-acked (date-only literal).
            String f1 = "(&(transactionType=835)(senderId=ABCPAYER)(receivedAt>=2026-09-01)(status=new))";
            String w1 = X12Filter.toWhereClause(f1);
            assertTrue(w1.contains("unixepoch("), "date literal coerced to epoch-millis: " + w1);
            assertEquals(new TreeSet<>(List.of("/in/a.835:1:0001")), selected(store, w1), "f1 -> " + w1);

            String f2 = "(|(transactionType=837P)(transactionType=837I))";
            assertEquals(new TreeSet<>(List.of("/in/b.837:7:0001", "/in/c.837:8:0001")),
                selected(store, X12Filter.toWhereClause(f2)));

            String f3 = "(loop2100.clp.clp02=1)";
            assertEquals(new TreeSet<>(List.of("/in/a.835:1:0001")), selected(store, X12Filter.toWhereClause(f3)));

            // §2.6 example 4 renders the epoch-millis relative form and executes.
            String w4 = X12Filter.toWhereClause("(receivedAt:withinDays:1)");
            assertTrue(w4.contains("received_at >= (unixepoch('now', '-1 days') * 1000)"), w4);
            assertNotNull(store.search(w4, 10));
        }
    }

    @Test
    void timeOfDayInComparisonRendersEpochMillis(@TempDir Path dir) throws Exception {
        String where = X12Filter.toWhereClause("(receivedAt>=2026-09-01T00:00:00Z)");
        assertTrue(where.contains("received_at >= (unixepoch('2026-09-01T00:00:00Z') * 1000)"), where);
        try (BufferStore store = load(dir, seed())) {
            assertEquals(new TreeSet<>(List.of("/in/a.835:1:0001", "/in/a.835:1:0002")), selected(store, where));
        }
        // interchangeDate is also an epoch column
        assertTrue(X12Filter.toWhereClause("(interchangeDate<2026-01-01)").contains("interchange_at < (unixepoch("));
        // a JSON-path date stays text and uses unixepoch on the extracted value
        assertTrue(X12Filter.toWhereClause("(header.bpr.bpr16:withinDays:7)")
            .contains("unixepoch(json_extract(mapped_json, '$.header.bpr.bpr16'))"));
    }

    @Test
    void apostropheInValueIsEscapedNotInjected(@TempDir Path dir) throws Exception {
        try (BufferStore store = load(dir, seed())) {
            String where = X12Filter.toWhereClause("(note=O'BRIEN)");
            assertTrue(where.contains("O''BRIEN"), where);
            assertEquals(new TreeSet<>(List.of("/in/b.837:7:0001")), selected(store, where));
        }
    }

    @Test
    void maliciousPropertyPathIsRejectedNotInjected() {
        assertThrows(IllegalArgumentException.class, () -> X12Filter.toWhereClause("(loop-2100.clp=1)"));
        assertThrows(IllegalArgumentException.class,
            () -> X12Filter.toWhereClause("(x');DROP TABLE transactions;--=X)"));
    }

    @Test
    void envelopePropertiesRenderToDenormalizedColumns() {
        Map<String, String> expected = Map.ofEntries(
            Map.entry("elementKey", "element_key"), Map.entry("fileId", "file_id"),
            Map.entry("sourceName", "source_name"), Map.entry("transactionType", "transaction_type"),
            Map.entry("gs08", "gs08"), Map.entry("senderId", "sender_id"), Map.entry("receiverId", "receiver_id"),
            Map.entry("receivedAt", "received_at"), Map.entry("status", "status"), Map.entry("leaseId", "lease_id"),
            Map.entry("isaControlNumber", "isa_control"), Map.entry("gsControlNumber", "gs_control"),
            Map.entry("stControlNumber", "st_control"), Map.entry("envelope", "envelope"),
            Map.entry("parserErrorCount", "parser_error_count"), Map.entry("schemaId", "schema_id"));
        for (Map.Entry<String, String> e : expected.entrySet()) {
            String where = X12Filter.toWhereClause("(" + e.getKey() + "=x)");
            assertTrue(where.startsWith(e.getValue() + " "), e.getKey() + " -> " + where);
            assertFalse(where.contains("json_extract"), e.getKey() + " is a column, not a JSON path");
        }
        assertTrue(X12Filter.toWhereClause("(loop2100.clp.clp02=1)").contains("json_extract"));
        assertTrue(X12Filter.toWhereClause("(fileName=x)").contains("json_extract"), "fileName is not a column");
    }

    @Test
    void blankFilterIsNull() {
        assertEquals(null, X12Filter.toWhereClause((String) null));
        assertEquals(null, X12Filter.toWhereClause("   "));
    }
}

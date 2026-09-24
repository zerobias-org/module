package com.zerobias.module.x12.producer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.Status;
import com.zerobias.module.x12.buffer.TestRows;
import com.zerobias.module.x12.buffer.TransactionRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.zerobias.module.x12.producer.ProducerFixture.KEY_A1;
import static com.zerobias.module.x12.producer.ProducerFixture.KEY_A2;
import static com.zerobias.module.x12.producer.ProducerFixture.KEY_A3;
import static com.zerobias.module.x12.producer.ProducerFixture.KEY_B1;
import static com.zerobias.module.x12.producer.ProducerFixture.KEY_B2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The {@code /x12-receiver/ops/*} functions (DESIGN §2.5) over the seeded buffer. */
class X12OperationsTest {

    private static final Gson GSON = new Gson();
    private static final SchemaRegistry SCHEMAS = SchemaRegistry.fromClasspath();
    private static final String OPS = ObjectTree.RECEIVER + "/ops/";

    @TempDir
    Path dir;
    private TestRows.MutableClock clock;
    private BufferStore buffer;
    private ProducerFixture.StubPoller poller;
    private X12Operations ops;
    private X12ProducerFacade facade;

    @BeforeEach
    void seed() throws Exception {
        clock = new TestRows.MutableClock(TestRows.BASE.plusSeconds(3600));
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false, clock);
        ProducerFixture.seed(buffer, dir);
        poller = new ProducerFixture.StubPoller(dir);
        ops = new X12Operations(buffer, poller, SCHEMAS, ProducerFixture.REPRODUCES);
        facade = ProducerFixture.facade(buffer, SCHEMAS, poller, dir, ProducerFixture.REPRODUCES);
    }

    @AfterEach
    void close() throws Exception {
        buffer.close();
    }

    @Test
    void takeThenAckDrainsOldestFirst() throws Exception {
        Map<String, Object> lease = ops.invoke("take", Map.of("max", 2));
        String leaseId = (String) lease.get("leaseId");
        assertNotNull(leaseId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> txs = (List<Map<String, Object>>) lease.get("transactions");
        assertEquals(2, txs.size());
        assertEquals(KEY_A1, txs.get(0).get("elementKey"), "oldest received first");
        assertEquals(KEY_A2, txs.get(1).get("elementKey"));
        assertEquals("in_flight", txs.get(0).get("status"));
        assertEquals(leaseId, txs.get(0).get("leaseId"));
        assertTrue(txs.get(0).containsKey("header"), "typed body (mapped_json) spread into the element");
        assertEquals("835", txs.get(0).get("transactionType"));
        assertEquals(3L, lease.get("remaining"));
        assertEquals(2, buffer.count(Status.IN_FLIGHT));

        assertEquals(2, ops.invoke("ack", Map.of("leaseId", leaseId)).get("acked"));
        assertEquals(2, buffer.count(Status.ACKED));

        // the JSON on the wire
        JsonObject out = GSON.fromJson(facade.invokeFunction(OPS + "take", "{\"max\":1}"), JsonObject.class);
        assertEquals(KEY_A3, out.getAsJsonArray("transactions").get(0).getAsJsonObject().get("elementKey").getAsString());
        assertEquals(2, out.get("remaining").getAsLong());
        assertTrue(out.get("leaseId").isJsonPrimitive());
    }

    @Test
    void ackAndReleaseOfALeaseNoRowCarriesAreNotFound() throws Exception {
        String leaseId = (String) ops.invoke("take", Map.of("max", 2)).get("leaseId");
        assertEquals(0, ops.invoke("ack", Map.of("leaseId", leaseId, "elementKeys", List.of(KEY_B1))).get("acked"),
            "a live lease acking keys outside it: a count, not an error");
        assertEquals(2, ops.invoke("ack", Map.of("leaseId", leaseId)).get("acked"));

        for (String fn : List.of("ack", "release")) {
            for (String id : List.of(leaseId, "unknown")) {
                ProducerException e = assertThrows(ProducerException.class, () -> ops.invoke(fn, Map.of("leaseId", id)),
                    fn + " " + id);
                assertEquals(404, e.httpStatus());
                assertEquals("lease", e.toBody().get("type"), "finalized and unknown leases are both gone");
                assertEquals(id, e.toBody().get("id"));
            }
        }
        assertTrue(X12Operations.declaredErrors("ack").containsKey("not_found"), "the 404 is declared on the function");
    }

    @Test
    void emptyTakeSerializesNullLeaseId() throws Exception {
        String json = facade.invokeFunction(OPS + "take", "{\"filter\":\"(transactionType=999)\"}");
        JsonObject out = GSON.fromJson(json, JsonObject.class);
        assertTrue(json.contains("\"leaseId\":null"), json);
        assertTrue(out.get("leaseId").isJsonNull());
        assertEquals(0, out.getAsJsonArray("transactions").size());
        assertEquals(5, out.get("remaining").getAsLong());
    }

    @Test
    void partialAckAndReleaseAndReplay() throws Exception {
        Map<String, Object> lease = ops.invoke("take", Map.of("max", 3, "filter", "(sourceName=inbox)"));
        String leaseId = (String) lease.get("leaseId");
        assertEquals(1, ops.invoke("ack", Map.of("leaseId", leaseId, "elementKeys", List.of(KEY_A1))).get("acked"));
        assertEquals(1, ops.invoke("release", Map.of("leaseId", leaseId, "elementKeys", List.of(KEY_A2))).get("released"));
        assertEquals(Status.ACKED, buffer.byElementKey(KEY_A1).get().status());
        assertEquals(Status.NEW, buffer.byElementKey(KEY_A2).get().status());
        assertEquals(Status.IN_FLIGHT, buffer.byElementKey(KEY_A3).get().status());
        assertEquals(1, ops.invoke("release", Map.of("leaseId", leaseId)).get("released"), "rest of the lease");
        assertEquals(0, buffer.count(Status.IN_FLIGHT));

        // replay: force in_flight back to new regardless of TTL, optionally filtered
        ops.invoke("take", Map.of("max", 10));
        assertEquals(4, buffer.count(Status.IN_FLIGHT));
        assertEquals(2, ops.invoke("replay", Map.of("filter", "(transactionType=837P)")).get("replayed"));
        assertEquals(2, ops.invoke("replay", Map.of()).get("replayed"));
        assertEquals(0, buffer.count(Status.IN_FLIGHT));
    }

    @Test
    void leaseRevertsAtTtl() throws Exception {
        Map<String, Object> lease = ops.invoke("take", Map.of("max", 1, "leaseTtl", "PT1M"));
        assertNull(ops.invoke("take", Map.of("max", 1, "filter", "(elementKey=" + KEY_A1 + ")")).get("leaseId"),
            "leased row is not drainable before the TTL");
        clock.advance(Duration.ofMinutes(2));
        Map<String, Object> again = ops.invoke("take", Map.of("max", 1));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> txs = (List<Map<String, Object>>) again.get("transactions");
        assertEquals(KEY_A1, txs.get(0).get("elementKey"), "expired lease is drainable again");
        assertFalse(lease.get("leaseId").equals(again.get("leaseId")));
    }

    @Test
    void inputThatFailsItsSchemaIsRejectedAndNothingRuns() throws Exception {
        String leaseId = (String) ops.invoke("take", Map.of("max", 2)).get("leaseId");
        ops.invoke("ack", Map.of("leaseId", leaseId));
        assertEquals(2, buffer.count(Status.ACKED));

        // a misspelled optional key must not fall back to its default ("purge every acked row")
        ProducerException typo = assertThrows(ProducerException.class, () -> ops.invoke("purge", Map.of("olderthan", "P30D")));
        assertEquals(400, typo.httpStatus());
        assertTrue(typo.getMessage().contains("olderthan"), typo.getMessage());
        assertEquals(2, buffer.count(Status.ACKED), "purge deleted nothing");
        assertEquals(400, assertThrows(ProducerException.class,
            () -> facade.invokeFunction(OPS + "purge", "{\"olderthan\":\"P30D\"}")).httpStatus());
        assertEquals(2, buffer.count(Status.ACKED));

        assertEquals(400, assertThrows(ProducerException.class,
            () -> ops.invoke("take", Map.of("filtr", "(transactionType=835)"))).httpStatus());
        assertEquals(0, buffer.count(Status.IN_FLIGHT), "take leased nothing");

        List<Map<String, Object>> bad = List.of(
            Map.of("max", "lots"),                     // wrong type
            Map.of("max", "5"),                        // numeric strings are strings
            Map.of("max", 2.5),                        // not whole
            Map.of("max", 0),                          // below 1
            Map.of("leaseTtl", "5 minutes"),           // not ISO-8601
            Map.of("leaseTtl", "PT0S"),                // not positive
            Map.of("filter", "(bad"),                  // malformed RFC4515
            Map.of("filter", 7));                      // wrong type
        for (Map<String, Object> input : bad) {
            ProducerException e = assertThrows(ProducerException.class, () -> ops.invoke("take", input), input.toString());
            assertEquals(400, e.httpStatus(), input.toString());
            assertEquals("err.illegal.argument", e.key(), input.toString());
        }
        assertEquals(0, buffer.count(Status.IN_FLIGHT));

        assertEquals(400, assertThrows(ProducerException.class, () -> ops.invoke("ack", Map.of())).httpStatus(), "required");
        assertEquals(400, assertThrows(ProducerException.class, () -> ops.invoke("ack", Map.of("leaseId", " "))).httpStatus());
        assertEquals(400, assertThrows(ProducerException.class,
            () -> ops.invoke("ack", Map.of("leaseId", "L", "elementKeys", "notAnArray"))).httpStatus());
        assertEquals(400, assertThrows(ProducerException.class,
            () -> ops.invoke("ack", Map.of("leaseId", "L", "elementKeys", List.of(1)))).httpStatus());
        assertEquals(400, assertThrows(ProducerException.class,
            () -> ops.invoke("ack", Map.of("leaseId", "L", "elementKeys", List.of()))).httpStatus(),
            "an empty subset would ack the whole lease");
        assertEquals(400, assertThrows(ProducerException.class,
            () -> ops.invoke("purge", Map.of("olderThan", "-PT1H"))).httpStatus());
        assertEquals(400, assertThrows(ProducerException.class, () -> ops.invoke("raw", Map.of())).httpStatus());
        assertEquals(400, assertThrows(ProducerException.class, () -> ops.invoke("replay", Map.of("max", 1))).httpStatus(),
            "a key another function takes is still unknown here");

        // Gson hands JSON numbers over as doubles; whole ones are integers
        assertEquals(1, ((List<?>) ops.invoke("take", Map.of("max", 1.0)).get("transactions")).size());
    }

    @Test
    void validateMirrorsInvokeWithoutRunning() throws Exception {
        Map<String, Object> typo = ops.validateInput("purge", Map.of("olderthan", "P30D"), false);
        assertEquals(false, typo.get("valid"));
        @SuppressWarnings("unchecked")
        Map<String, Object> err = ((List<Map<String, Object>>) typo.get("errors")).get(0);
        assertEquals(Map.of("path", "olderthan", "message", "unknown property (expected olderThan)", "code", "unknown_property"), err);

        assertEquals(true, ops.validateInput("take", Map.of("max", 5000), false).get("valid"));
        assertEquals(1, ((List<?>) ops.validateInput("take", Map.of("max", 5000), false).get("warnings")).size());
        assertEquals(false, ops.validateInput("take", Map.of("max", 5000), true).get("valid"));
        // a lease longer than the cap is granted at the cap: said up front, like max
        Map<String, Object> longLease = ops.validateInput("take", Map.of("leaseTtl", "PT2H"), false);
        assertEquals(true, longLease.get("valid"));
        assertEquals(List.of(Map.of("path", "leaseTtl", "message", "is capped at PT1H")), longLease.get("warnings"));
        assertEquals(false, ops.validateInput("take", Map.of("leaseTtl", "PT2H"), true).get("valid"));
        assertEquals(List.of(), ops.validateInput("take", Map.of("leaseTtl", "PT1H"), false).get("warnings"),
            "the cap itself is granted as asked");
        assertEquals(List.of(), ops.validateInput("take", Map.of("leaseTtl", "PT0S"), false).get("warnings"),
            "an invalid duration is an error, not also a warning");
        assertEquals(true, ops.validateInput("rescan", null, false).get("valid"), "no input = empty input");
        assertEquals(false, ops.validateInput("take", "text", false).get("valid"));
        assertEquals(404, assertThrows(ProducerException.class, () -> ops.validateInput("nope", Map.of(), false)).httpStatus());
        assertEquals(0, buffer.count(Status.IN_FLIGHT));
    }

    @Test
    void purgeDeletesAckedRowsOnly() throws Exception {
        String leaseId = (String) ops.invoke("take", Map.of("max", 2)).get("leaseId");
        ops.invoke("ack", Map.of("leaseId", leaseId));
        assertEquals(0, ops.invoke("purge", Map.of("olderThan", "PT1H")).get("purged"), "just acked → not old enough");
        assertEquals(2, ops.invoke("purge", Map.of()).get("purged"), "default = every acked row");
        assertEquals(3, buffer.count());
        assertEquals(0, buffer.count(Status.ACKED));
        assertEquals(400, assertThrows(ProducerException.class, () -> ops.invoke("purge", Map.of("olderThan", "1h"))).httpStatus());
    }

    @Test
    void rawReturnsTheStoredX12Verbatim() throws Exception {
        Map<String, Object> raw = ops.invoke("raw", Map.of("elementKey", KEY_A1));
        assertEquals(KEY_A1, raw.get("elementKey"));
        assertEquals(TestRows.FILE_A, raw.get("fileId"));
        assertEquals("005010X221A1", raw.get("gs08"));
        assertEquals("835", raw.get("transactionType"));
        assertEquals("ST*835*0001~SE*2*0001~", raw.get("raw"));

        assertEquals(404, assertThrows(ProducerException.class, () -> ops.invoke("raw", Map.of("elementKey", "nope"))).httpStatus());
    }

    @Test
    void validateReportsTheStoredAndRederivedVerdicts() throws Exception {
        Map<String, Object> ok = ops.invoke("validate", Map.of("elementKey", KEY_A1));
        assertEquals(TestRows.SCHEMA_835, ok.get("schemaId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> stored = (Map<String, Object>) ok.get("stored");
        assertEquals(true, stored.get("valid"), String.valueOf(stored.get("errors")));
        assertEquals(List.of(), stored.get("errors"));
        @SuppressWarnings("unchecked")
        Map<String, Object> re = (Map<String, Object>) ok.get("rematerialized");
        assertEquals(true, re.get("valid"));
        assertEquals(TestRows.SCHEMA_835, re.get("schemaId"));
        assertEquals(true, ok.get("repsAgree"));
        assertEquals(List.of(), ok.get("parserErrors"));
        assertEquals(0, ok.get("parserErrorCount"));

        // an unbundled guide: stored rep fails on the schema check
        Map<String, Object> bad = ops.invoke("validate", Map.of("elementKey", KEY_B2));
        @SuppressWarnings("unchecked")
        Map<String, Object> badStored = (Map<String, Object>) bad.get("stored");
        assertEquals(false, badStored.get("valid"));
        assertEquals(List.of("schema not registered: " + ProducerFixture.SCHEMA_837P_ALT), badStored.get("errors"));

        // a row whose mapped_json is corrupt
        buffer.updateMapping(buffer.byElementKey(KEY_B1).get().id(), TestRows.SCHEMA_837P, "{not json");
        @SuppressWarnings("unchecked")
        Map<String, Object> corrupt = (Map<String, Object>) ops.invoke("validate", Map.of("elementKey", KEY_B1)).get("stored");
        assertEquals(false, corrupt.get("valid"));
        assertTrue(((List<?>) corrupt.get("errors")).get(0).toString().startsWith("mapped_json does not parse"));

        assertEquals(404, assertThrows(ProducerException.class,
            () -> ops.invoke("validate", Map.of("elementKey", "nope"))).httpStatus());
    }

    @Test
    void recastRewritesChangedRowsAndCountsFailures() throws Exception {
        ops.invoke("take", Map.of("max", 1));   // one in_flight row is excluded
        Map<String, Object> out = ops.invoke("recast", Map.of());
        assertEquals(Map.of("examined", 4, "recast", 0, "unchanged", 4, "failed", 0), out);
        assertEquals(1, ops.invoke("recast", Map.of("filter", "(transactionType=837P)", "max", 1)).get("examined"));

        // a hook that rewrites 835 rows and fails on 837P
        RecastHook hook = row -> {
            if ("837P".equals(row.transactionType())) {
                throw new IllegalStateException("no map");
            }
            return Optional.of(new RecastHook.Mapping(row.schemaId(), "{\"recast\":true}"));
        };
        X12Operations withHook = new X12Operations(buffer, poller, SCHEMAS, hook);
        Map<String, Object> r = withHook.invoke("recast", Map.of());
        assertEquals(Map.of("examined", 4, "recast", 2, "unchanged", 0, "failed", 2), r);
        TransactionRow a2 = buffer.byElementKey(KEY_A2).get();
        assertEquals("{\"recast\":true}", a2.mappedJson());
        assertEquals("{\"header\":{\"st\":{\"st02\":\"0001\"}}}", buffer.byElementKey(KEY_A1).get().mappedJson(), "in_flight row untouched");

        Map<String, Object> v = withHook.invoke("validate", Map.of("elementKey", KEY_A3));
        assertNotNull(v.get("rematerialized"));
        assertEquals(true, v.get("repsAgree"), "hook reproduces what it just wrote");
        @SuppressWarnings("unchecked")
        Map<String, Object> rv = (Map<String, Object>) withHook.invoke("validate", Map.of("elementKey", KEY_B1)).get("rematerialized");
        assertEquals(false, rv.get("valid"));
        assertEquals(false, withHook.invoke("validate", Map.of("elementKey", KEY_B1)).get("repsAgree"));
    }

    @Test
    void rescanDelegatesToThePoller() throws Exception {
        Map<String, Object> all = ops.invoke("rescan", Map.of());
        assertEquals(Map.of("scanned", 1, "discovered", 2, "consumed", 1, "errored", 1), all);
        assertNull(poller.lastRescanSource);
        assertEquals(1, poller.rescans);

        ops.invoke("rescan", Map.of("source", "inbox"));
        assertEquals("inbox", poller.lastRescanSource);
        assertEquals(2, poller.rescans);

        ProducerException unknown = assertThrows(ProducerException.class, () -> ops.invoke("rescan", Map.of("source", "nope")));
        assertEquals(404, unknown.httpStatus());
        assertEquals(2, poller.rescans, "unknown source never reaches the poller");

        assertEquals(404, assertThrows(ProducerException.class, () -> ops.invoke("nope", Map.of())).httpStatus());
    }

    @Test
    void operationsRefuseMissingCollaborators() {
        assertThrows(NullPointerException.class, () -> new X12Operations(buffer, null, SCHEMAS, ProducerFixture.REPRODUCES));
        assertThrows(NullPointerException.class, () -> new X12Operations(buffer, poller, SCHEMAS, null));
        assertThrows(NullPointerException.class, () -> new X12Operations(buffer, poller, null, ProducerFixture.REPRODUCES));
    }
}

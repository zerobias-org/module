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
    private static final String OPS = ObjectTreeApi.RECEIVER + "/ops/";

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
        ops = new X12Operations(buffer, () -> poller, SCHEMAS);
        facade = new X12ProducerFacade(buffer, new ObjectTree(buffer, SCHEMAS, () -> poller), SCHEMAS, ops);
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
        assertEquals(0, ops.invoke("ack", Map.of("leaseId", leaseId)).get("acked"), "already finalized → 0");
        assertEquals(0, ops.invoke("ack", Map.of("leaseId", "unknown")).get("acked"), "unknown lease → 0 (count is the signal)");

        // the JSON on the wire
        JsonObject out = GSON.fromJson(facade.invokeFunction(OPS + "take", "{\"max\":1}"), JsonObject.class);
        assertEquals(KEY_A3, out.getAsJsonArray("transactions").get(0).getAsJsonObject().get("elementKey").getAsString());
        assertEquals(2, out.get("remaining").getAsLong());
        assertTrue(out.get("leaseId").isJsonPrimitive());
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

        assertEquals(400, assertThrows(ProducerException.class, () -> ops.invoke("ack", Map.of())).httpStatus());
        assertEquals(400, assertThrows(ProducerException.class,
            () -> ops.invoke("ack", Map.of("leaseId", "L", "elementKeys", "notAnArray"))).httpStatus());
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
        assertEquals(400, assertThrows(ProducerException.class,
            () -> ops.invoke("take", Map.of("leaseTtl", "5 minutes"))).httpStatus());
        assertEquals(400, assertThrows(ProducerException.class,
            () -> ops.invoke("take", Map.of("filter", "(bad"))).httpStatus());
        assertEquals(400, assertThrows(ProducerException.class,
            () -> ops.invoke("take", Map.of("max", "lots"))).httpStatus());
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
        assertEquals(400, assertThrows(ProducerException.class, () -> ops.invoke("raw", Map.of())).httpStatus());
    }

    @Test
    void validateChecksTheStoredRepAndLeavesTheSeamNull() throws Exception {
        Map<String, Object> ok = ops.invoke("validate", Map.of("elementKey", KEY_A1));
        assertEquals(TestRows.SCHEMA_835, ok.get("schemaId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> stored = (Map<String, Object>) ok.get("stored");
        assertEquals(true, stored.get("valid"), String.valueOf(stored.get("errors")));
        assertEquals(List.of(), stored.get("errors"));
        assertNull(ok.get("rematerialized"));
        assertNull(ok.get("repsAgree"));
        assertEquals(List.of(), ok.get("parserErrors"));
        assertEquals(0, ok.get("parserErrorCount"));
        String json = facade.invokeFunction(OPS + "validate", "{\"elementKey\":\"" + KEY_A1 + "\"}");
        assertTrue(json.contains("\"rematerialized\":null"), json);

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
    void recastWithoutAMaterializerRewritesNothing() throws Exception {
        ops.invoke("take", Map.of("max", 1));   // one in_flight row is excluded
        Map<String, Object> out = ops.invoke("recast", Map.of());
        assertEquals(4, out.get("examined"));
        assertEquals(0, out.get("recast"));
        assertEquals(4, out.get("unchanged"));
        assertEquals(0, out.get("failed"));
        assertEquals("recast requires the materializer", out.get("note"));
        assertEquals(1, ops.invoke("recast", Map.of("filter", "(transactionType=837P)", "max", 1)).get("examined"));

        // the seam: a hook that rewrites 835 rows and fails on 837P
        RecastHook hook = row -> {
            if ("837P".equals(row.transactionType())) {
                throw new IllegalStateException("no map");
            }
            return Optional.of(new RecastHook.Mapping(row.schemaId(), "{\"recast\":true}"));
        };
        X12Operations withHook = new X12Operations(buffer, null, () -> poller, SCHEMAS, hook);
        Map<String, Object> r = withHook.invoke("recast", Map.of());
        assertEquals(4, r.get("examined"));
        assertEquals(2, r.get("recast"));
        assertEquals(0, r.get("unchanged"));
        assertEquals(2, r.get("failed"));
        assertFalse(r.containsKey("note"));
        TransactionRow a2 = buffer.byElementKey(KEY_A2).get();
        assertEquals("{\"recast\":true}", a2.mappedJson());
        assertEquals("{\"header\":{\"st\":{\"st02\":\"0001\"}}}", buffer.byElementKey(KEY_A1).get().mappedJson(), "in_flight row untouched");

        Map<String, Object> v = withHook.invoke("validate", Map.of("elementKey", KEY_A3));
        assertNotNull(v.get("rematerialized"));
        assertEquals(true, v.get("repsAgree"), "hook reproduces what it just wrote");
        @SuppressWarnings("unchecked")
        Map<String, Object> rv = (Map<String, Object>) withHook.invoke("validate", Map.of("elementKey", KEY_B1)).get("rematerialized");
        assertEquals(false, rv.get("valid"));
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

        X12Operations noPoller = new X12Operations(buffer, () -> null, SCHEMAS);
        assertEquals(404, assertThrows(ProducerException.class, () -> noPoller.invoke("rescan", Map.of())).httpStatus());

        assertEquals(404, assertThrows(ProducerException.class, () -> ops.invoke("nope", Map.of())).httpStatus());
    }
}

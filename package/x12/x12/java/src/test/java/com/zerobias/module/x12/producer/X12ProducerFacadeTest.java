package com.zerobias.module.x12.producer;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.Status;
import com.zerobias.module.x12.buffer.TestRows;
import com.zerobias.module.x12.buffer.TransactionRow;
import com.zerobias.module.x12.inbox.FileConsumer;
import com.zerobias.module.x12.inbox.InboxFixtures;
import com.zerobias.module.x12.materializer.StructureResolver;
import com.zerobias.module.x12.materializer.TransactionJson;
import com.zerobias.module.x12.parser.Fixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.zerobias.module.x12.buffer.TestRows.FILE_A;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The facade over the seeded buffer: the mandatory PagedResults envelope and its bounds, the
 * DESIGN §5 envelope overlay, strict function-body parsing, {@code validateFunctionInput},
 * and the errorModelBase bodies.
 */
class X12ProducerFacadeTest {

    private static final Gson GSON = new Gson();
    private static final SchemaRegistry SCHEMAS = SchemaRegistry.fromClasspath();
    private static final String R = ObjectTree.RECEIVER;

    @TempDir
    Path dir;
    private BufferStore buffer;
    private X12ProducerFacade facade;

    @BeforeEach
    void seed() throws Exception {
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false, new TestRows.MutableClock(TestRows.BASE.plusSeconds(3600)));
        ProducerFixture.seed(buffer, dir);
        facade = ProducerFixture.facade(buffer, SCHEMAS, new ProducerFixture.StubPoller(dir), dir, ProducerFixture.REPRODUCES);
    }

    @AfterEach
    void close() throws Exception {
        buffer.close();
    }

    @Test
    void paginatedOpsReturnThePagedResultsEnvelope() throws Exception {
        JsonObject page = GSON.fromJson(facade.getCollectionElements(R + "/transactions", null, 2, 2), JsonObject.class);
        assertTrue(page.has("items"), "PagedResults MUST carry items");
        assertEquals(5, page.get("count").getAsLong(), "count = total matches, not the page");
        assertEquals(2, page.get("pageSize").getAsInt());
        assertEquals(2, page.get("pageNumber").getAsInt(), "1-based on the wire");
        assertEquals(List.of(ProducerFixture.KEY_A3, ProducerFixture.KEY_A2), keys(page), "newest first → page 2 of size 2");

        JsonObject past = GSON.fromJson(facade.getCollectionElements(R + "/transactions", null, 9, 2), JsonObject.class);
        assertEquals(0, past.getAsJsonArray("items").size());
        assertEquals(5, past.get("count").getAsLong());

        JsonObject filtered = GSON.fromJson(facade.getCollectionElements(R + "/transactions",
            "(stControlNumber=0002)", 1, 10), JsonObject.class);
        assertEquals(1, filtered.get("count").getAsLong());

        String raw = X12ProducerFacade.pagedResults(List.of(), 0, 100, 1);
        assertEquals("{\"items\":[],\"count\":0,\"pageSize\":100,\"pageNumber\":1}", raw);
    }

    @Test
    void pageBoundsFollowTheInterface() throws Exception {
        for (int[] bad : new int[][] {{0, 10}, {-1, 10}, {1, 0}, {1, -5}, {1, 1001}}) {
            ProducerException e = assertThrows(ProducerException.class,
                () -> facade.getCollectionElements(R + "/transactions", null, bad[0], bad[1]), bad[0] + "/" + bad[1]);
            assertEquals(400, e.httpStatus());
            assertEquals("err.illegal.argument", e.key());
            assertEquals(400, assertThrows(ProducerException.class, () -> facade.getChildren(R, bad[0], bad[1])).httpStatus());
        }
        assertEquals(1000, GSON.fromJson(facade.getCollectionElements(R + "/transactions", null, 1, 1000), JsonObject.class)
            .get("pageSize").getAsInt(), "the maximum itself is allowed");
        assertEquals(0, GSON.fromJson(facade.getChildren(R + "/files", Integer.MAX_VALUE, 1000), JsonObject.class)
            .getAsJsonArray("items").size(), "a far page is empty, not an overflowed offset");
    }

    @Test
    void toElementOverlaysTheEnvelopeOverTheBody() throws Exception {
        TransactionRow r = TestRows.tx("1", "0001", 0);
        Map<String, Object> e = X12ProducerFacade.toElement(r);
        assertEquals(TestRows.key(FILE_A, "1", "0001"), e.get("elementKey"));
        assertEquals(FILE_A, e.get("fileId"));
        assertEquals("remit-a.835", e.get("fileName"));
        assertEquals("inbox", e.get("sourceName"));
        assertEquals("000000001", e.get("isaControlNumber"));
        assertEquals("1", e.get("gsControlNumber"));
        assertEquals("0001", e.get("stControlNumber"));
        assertEquals("005010X221A1", e.get("gs08"));
        assertEquals("835", e.get("transactionType"));
        assertEquals("PAYERA", e.get("senderId"));
        assertEquals("PROVIDER1", e.get("receiverId"));
        assertEquals(TestRows.BASE.minusSeconds(3600).toString(), e.get("interchangeDate"));
        assertEquals(TestRows.BASE.toString(), e.get("receivedAt"));
        assertEquals("new", e.get("status"));
        assertNull(e.get("leaseId"));
        assertEquals("file", e.get("envelope"));
        assertEquals(0, e.get("parserErrorCount"));
        assertTrue(e.containsKey("header"), "typed body keys are kept");

        // envelope is authoritative: a body claiming a different status is overridden
        TransactionRow lying = r.withMapping(r.schemaId(), "{\"status\":\"acked\",\"senderId\":\"X\"}");
        Map<String, Object> e2 = X12ProducerFacade.toElement(lying);
        assertEquals("new", e2.get("status"));
        assertEquals("PAYERA", e2.get("senderId"));

        JsonObject got = GSON.fromJson(facade.getCollectionElement(R + "/transactions", ProducerFixture.KEY_A1), JsonObject.class);
        assertEquals(ProducerFixture.KEY_A1, got.get("elementKey").getAsString());
        assertEquals(404, assertThrows(ProducerException.class,
            () -> facade.getCollectionElement(R + "/transactions", "nope")).httpStatus());
        assertEquals(404, assertThrows(ProducerException.class,
            () -> facade.getCollectionElement(R + "/by-source/sftp", ProducerFixture.KEY_A1)).httpStatus(), "outside the scope");
    }

    @Test
    void servedElementsKeepEveryNumberAsItWasStored() throws Exception {
        // A real ingest: the materializer stores CLP03 300.00 as a decimal keeping its scale, and an
        // N0 (LX01, widened past 2^53) exactly. Both must reach the wire as stored, in a collection
        // element and in take's output, never re-rounded through double.
        String text = Fixtures.text(Fixtures.F835).replace("LX*1~", "LX*1234567890123456789~");
        Path f = Files.write(dir.resolve("wide.835"), text.getBytes(StandardCharsets.UTF_8));
        FileConsumer consumer = new FileConsumer(buffer, null, ProducerFixture.config(dir), new StructureResolver(),
            buffer.clock());
        String fileId = InboxFixtures.consume(consumer, ProducerFixture.config(dir).sources().get(0), f, TestRows.BASE)
            .fileId();
        String key = TransactionJson.elementKey(fileId, "000000101", "101", "0001");
        String stored = buffer.byElementKey(key).orElseThrow().mappedJson();
        assertTrue(stored.contains("\"clp03\":300.00") && stored.contains("\"lx01\":1234567890123456789"), stored);

        String element = facade.getCollectionElement(R + "/transactions", key);
        assertTrue(element.contains("\"clp03\":300.00"), element);
        assertTrue(element.contains("\"bpr02\":450.00"), element);
        assertTrue(element.contains("\"lx01\":1234567890123456789"), element);

        String page = facade.getCollectionElements(R + "/transactions", "(elementKey=" + key + ")", 1, 10);
        assertTrue(page.contains("\"clp03\":300.00") && page.contains("\"lx01\":1234567890123456789"), page);

        String taken = facade.invokeFunction(R + "/ops/take", "{\"filter\":\"(elementKey=" + key + ")\"}");
        assertTrue(taken.contains("\"clp03\":300.00"), taken);
        assertTrue(taken.contains("\"lx01\":1234567890123456789"), taken);
    }

    @Test
    void invokeFunctionParsesTheBodyStrictly() throws Exception {
        for (String bad : List.of("{not json", "[1,2]", "\"take\"", "42")) {
            ProducerException e = assertThrows(ProducerException.class,
                () -> facade.invokeFunction(R + "/ops/take", bad), bad);
            assertEquals(400, e.httpStatus(), bad);
            assertEquals("err.illegal.argument", e.key(), bad);
        }
        assertEquals(0, buffer.count(Status.IN_FLIGHT), "nothing was leased by a rejected body");

        assertEquals("{\"replayed\":0}", facade.invokeFunction(R + "/ops/replay", ""), "blank body = no input");
        assertEquals("{\"replayed\":0}", facade.invokeFunction(R + "/ops/replay", null));
        assertEquals("{\"replayed\":0}", facade.invokeFunction(R + "/ops/replay", "null"));

        String out = facade.invokeFunction(R + "/ops/take", "{\"filter\":\"(transactionType=999)\",\"max\":5}");
        assertTrue(out.startsWith("{\"leaseId\":null,"), "serializeNulls on function output: " + out);

        assertEquals(400, assertThrows(ProducerException.class,
            () -> facade.invokeFunction(R + "/transactions", "{}")).httpStatus(), "not a function");
        assertEquals(404, assertThrows(ProducerException.class,
            () -> facade.invokeFunction(R + "/ops/nope", "{}")).httpStatus());
    }

    @Test
    void validateFunctionInputReportsWhatInvokeWouldReject() throws Exception {
        JsonObject typo = validate("purge", "{\"input\":{\"olderthan\":\"P30D\"}}");
        assertFalse(typo.get("valid").getAsBoolean());
        JsonObject err = typo.getAsJsonArray("errors").get(0).getAsJsonObject();
        assertEquals("olderthan", err.get("path").getAsString());
        assertEquals("unknown_property", err.get("code").getAsString());
        assertEquals(0, typo.getAsJsonArray("warnings").size());

        JsonObject ok = validate("take", "{\"input\":{\"filter\":\"(transactionType=835)\",\"max\":5,\"leaseTtl\":\"PT1M\"}}");
        assertTrue(ok.get("valid").getAsBoolean(), ok.toString());
        assertEquals(0, ok.getAsJsonArray("errors").size());

        JsonObject capped = validate("take", "{\"input\":{\"max\":5000}}");
        assertTrue(capped.get("valid").getAsBoolean(), "a capped max still runs");
        assertEquals("max", capped.getAsJsonArray("warnings").get(0).getAsJsonObject().get("path").getAsString());
        JsonObject strict = validate("take", "{\"input\":{\"max\":5000},\"strict\":true}");
        assertFalse(strict.get("valid").getAsBoolean(), "strict: warnings are errors");
        assertEquals("capped", strict.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());

        JsonArray missing = validate("raw", "{}").getAsJsonArray("errors");
        assertEquals("elementKey", missing.get(0).getAsJsonObject().get("path").getAsString());
        assertEquals("required", missing.get(0).getAsJsonObject().get("code").getAsString());
        assertEquals("type", validate("take", "{\"input\":[1]}").getAsJsonArray("errors").get(0).getAsJsonObject()
            .get("code").getAsString());
        assertEquals(0, buffer.count(Status.IN_FLIGHT) + buffer.count(Status.ACKED), "validation never runs the function");

        assertEquals(400, assertThrows(ProducerException.class,
            () -> facade.validateFunctionInput(R + "/ops/take", "{\"inputs\":{}}")).httpStatus());
        assertEquals(400, assertThrows(ProducerException.class,
            () -> facade.validateFunctionInput(R + "/ops/take", "{\"strict\":\"yes\"}")).httpStatus());
        assertEquals(400, assertThrows(ProducerException.class,
            () -> facade.validateFunctionInput(R + "/stats", "{}")).httpStatus(), "not a function");
        assertEquals(404, assertThrows(ProducerException.class,
            () -> facade.validateFunctionInput(R + "/ops/nope", "{}")).httpStatus());
    }

    @Test
    void facadeRefusesMissingCollaborators() {
        ObjectTree tree = new ObjectTree(buffer, SCHEMAS, new ProducerFixture.StubPoller(dir), ProducerFixture.config(dir));
        X12Operations ops = new X12Operations(buffer, new ProducerFixture.StubPoller(dir), SCHEMAS, ProducerFixture.REPRODUCES);
        assertThrows(NullPointerException.class, () -> new X12ProducerFacade(buffer, null, SCHEMAS, ops));
        assertThrows(NullPointerException.class, () -> new X12ProducerFacade(buffer, tree, null, ops));
        assertThrows(NullPointerException.class, () -> new X12ProducerFacade(buffer, tree, SCHEMAS, null));
    }

    @Test
    void errorBodiesFollowErrorModelBase() {
        Map<String, Object> nso = ProducerException.noSuchObject("/x").toBody();
        assertEquals("err.no.such.object", nso.get("key"));
        assertEquals(404, nso.get("statusCode"));
        assertEquals("object", nso.get("type"));
        assertEquals("/x", nso.get("id"));
        assertTrue(nso.containsKey("timestamp") && nso.containsKey("template"));

        Map<String, Object> gone = ProducerException.fileGone("/f").toBody();
        assertEquals("gone", gone.get("reason"));
        assertEquals("file", gone.get("type"));
        assertEquals("lease", ProducerException.noSuchLease("L").toBody().get("type"));
        assertEquals("schema", ProducerException.noSuchSchema("s").toBody().get("type"));

        Map<String, Object> ia = ProducerException.illegalArgument("bad").toBody();
        assertEquals("err.illegal.argument", ia.get("key"));
        assertEquals("bad", ia.get("msg"));
        assertEquals(400, ia.get("statusCode"));

        Map<String, Object> boom = ProducerException.unexpected().toBody();
        assertEquals("err.unexpected", boom.get("key"));
        assertEquals(500, boom.get("statusCode"));
        assertEquals("Unexpected error", boom.get("msg"));
        assertEquals("Unexpected error", boom.get("template"));
    }

    private JsonObject validate(String fn, String request) throws Exception {
        return GSON.fromJson(facade.validateFunctionInput(R + "/ops/" + fn, request), JsonObject.class);
    }

    private static List<String> keys(JsonObject page) {
        List<String> out = new ArrayList<>();
        page.getAsJsonArray("items").forEach(e -> out.add(e.getAsJsonObject().get("elementKey").getAsString()));
        return out;
    }
}

package com.zerobias.module.x12.producer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.TestRows;
import com.zerobias.module.x12.buffer.TransactionRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.zerobias.module.x12.buffer.TestRows.FILE_A;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The facade over a real tree: the mandatory PagedResults envelope and paging bounds, the
 * DESIGN §5 envelope overlay, the receive-only write rejections, and the error envelope.
 */
class X12ProducerFacadeTest {

    private static final Gson GSON = new Gson();
    private static final SchemaRegistry SCHEMAS = SchemaRegistry.fromClasspath();
    private static final String R = ObjectTree.RECEIVER;

    @TempDir
    Path dir;
    private BufferStore buffer;
    private ProducerFixture.StubPoller poller;
    private ObjectTree tree;
    private X12ProducerFacade facade;

    @BeforeEach
    void open() throws Exception {
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false);
        poller = new ProducerFixture.StubPoller(dir);
        tree = ProducerFixture.tree(buffer, SCHEMAS, poller);
        facade = ProducerFixture.facade(buffer, tree, SCHEMAS, poller);
    }

    @AfterEach
    void close() throws Exception {
        buffer.close();
    }

    @Test
    void constructionRejectsAMissingCollaborator() {
        // A missing tree, registry or function set is a wiring bug: it used to be swapped for a
        // stub that answered 404 to everything, which looks like an empty receiver, not a broken one.
        X12Operations ops = ProducerFixture.ops(buffer, poller, SCHEMAS);
        assertThrows(NullPointerException.class, () -> new X12ProducerFacade(buffer, null, SCHEMAS, ops, false));
        assertThrows(NullPointerException.class, () -> new X12ProducerFacade(buffer, tree, null, ops, false));
        assertThrows(NullPointerException.class, () -> new X12ProducerFacade(buffer, tree, SCHEMAS, null, false));
        assertThrows(NullPointerException.class, () -> new X12Operations(buffer, null, SCHEMAS, row -> null));
        assertThrows(NullPointerException.class, () -> new X12Operations(buffer, poller, SCHEMAS, null));
        assertThrows(NullPointerException.class,
            () -> new ObjectTree(buffer, SCHEMAS, null, ".done", List.of(), ".error"));
    }

    @Test
    void paginatedOpsReturnThePagedResultsEnvelope() throws Exception {
        buffer.insertTransaction(TestRows.tx("1", "0001", 0));
        buffer.insertTransaction(TestRows.tx("1", "0002", 1));
        buffer.insertTransaction(TestRows.tx("1", "0003", 2));

        JsonObject page = GSON.fromJson(
            facade.getCollectionElements(R + "/transactions", null, null, null, 2, 2, null), JsonObject.class);
        assertTrue(page.has("items"), "PagedResults MUST carry items");
        assertEquals(3, page.get("count").getAsLong(), "count = total matches, not the page");
        assertEquals(2, page.get("pageSize").getAsInt());
        assertEquals(2, page.get("pageNumber").getAsInt(), "1-based on the wire");
        assertEquals(1, page.getAsJsonArray("items").size());
        assertEquals("0001", page.getAsJsonArray("items").get(0).getAsJsonObject().get("stControlNumber").getAsString(),
            "newest first → page 2 of size 2 holds the oldest");

        JsonObject filtered = GSON.fromJson(facade.getCollectionElements(R + "/transactions",
            "(stControlNumber=0002)", null, null, 10, 1, null), JsonObject.class);
        assertEquals(1, filtered.get("count").getAsLong());

        List<Map<String, Object>> receiverKids = tree.children(R);
        JsonObject kids = GSON.fromJson(facade.getChildren(R, 1, 2), JsonObject.class);
        assertEquals(receiverKids.size(), kids.get("count").getAsInt());
        assertEquals(receiverKids.get(1).get("name"),
            kids.getAsJsonArray("items").get(0).getAsJsonObject().get("name").getAsString());

        assertEquals(400, assertThrows(ProducerException.class,
            () -> facade.getCollectionElements(R + "/transactions", "(bad", null, null, 10, 1, null)).httpStatus());
        assertEquals(400, assertThrows(ProducerException.class,
            () -> facade.getCollectionElements(R + "/transactions", null, null, null, 5000, 1, null)).httpStatus());

        String raw = X12ProducerFacade.pagedResults(List.of(), 0, 100, 1);
        assertEquals("{\"items\":[],\"count\":0,\"pageSize\":100,\"pageNumber\":1}", raw);
    }

    @Test
    void aPageNumberPastTheAddressableOffsetIsA400NotPageOne() throws Exception {
        buffer.insertTransaction(TestRows.tx("1", "0001", 0));
        // (2_147_485 - 1) * 1000 overflows an int to a negative offset, which SQLite reads as 0:
        // the caller got page 1 back as if it were page two million.
        int wraps = 2_147_485;
        assertTrue((wraps - 1) * 1000 < 0, "the int product wraps");
        for (String id : List.of(R + "/transactions", R + "/claims")) {
            ProducerException e = assertThrows(ProducerException.class,
                () -> facade.getCollectionElements(id, null, null, null, 1000, wraps, null), id);
            assertEquals(400, e.httpStatus(), id);
            assertEquals("err.illegal.argument", e.key(), id);
        }
        assertEquals(400, assertThrows(ProducerException.class,
            () -> facade.getChildren(R, 1000, wraps)).httpStatus());
        // the last addressable page is still served, empty
        JsonObject last = GSON.fromJson(facade.getCollectionElements(R + "/transactions", null, null, null,
            1000, 2_147_484, null), JsonObject.class);
        assertEquals(0, last.getAsJsonArray("items").size());
        assertEquals(1, last.get("count").getAsLong());
    }

    @Test
    void toElementOverlaysTheEnvelopeOverTheBody() throws Exception {
        TransactionRow r = TestRows.tx("1", "0001", 0);
        // The body comes from the object graph; toElement overlays the envelope on top.
        Map<String, Object> body = Map.of("header", Map.of("st", Map.of("st02", "0001")));
        Map<String, Object> e = X12ProducerFacade.toElement(r, body);
        assertEquals(FILE_A + ":000000001:1:0001", e.get("elementKey"));
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
        Map<String, Object> lyingBody = Map.of("status", "acked", "senderId", "X");
        Map<String, Object> e2 = X12ProducerFacade.toElement(r, lyingBody);
        assertEquals("new", e2.get("status"));
        assertEquals("PAYERA", e2.get("senderId"));

        buffer.insertTransaction(r);
        JsonObject got = GSON.fromJson(facade.getCollectionElement(R + "/transactions", r.elementKey()), JsonObject.class);
        assertEquals(r.elementKey(), got.get("elementKey").getAsString());
        assertEquals(404, assertThrows(ProducerException.class,
            () -> facade.getCollectionElement(R + "/transactions", "nope")).httpStatus());
    }

    @Test
    void writeSurfaceIsRejectedAndFunctionOutputKeepsNulls() throws Exception {
        for (ProducerException e : List.of(
                assertThrows(ProducerException.class, () -> facade.createCollectionElement("/x", "{}")),
                assertThrows(ProducerException.class, () -> facade.updateCollectionElement("/x", "k", "{}")),
                assertThrows(ProducerException.class, () -> facade.deleteCollectionElement("/x", "k")))) {
            assertEquals(400, e.httpStatus());
            assertEquals("err.unsupported.operation", e.key());
        }

        String out = facade.invokeFunction(R + "/ops/take", "{\"max\":5}");
        assertEquals("{\"leaseId\":null,\"transactions\":[],\"remaining\":0}", out, "serializeNulls on function output");
        assertEquals(400, assertThrows(ProducerException.class,
            () -> facade.invokeFunction(R + "/transactions", "{}")).httpStatus(), "not a function");

        assertTrue(GSON.fromJson(facade.getDocumentData(R + "/stats"), JsonObject.class).has("bufferDepth"));
        assertEquals(400, assertThrows(ProducerException.class, () -> facade.downloadBinary(R + "/stats")).httpStatus(),
            "a document is not a binary");
        assertEquals(400, assertThrows(ProducerException.class, () -> facade.getObject("")).httpStatus());
        assertEquals(400, assertThrows(ProducerException.class, () -> facade.getSchema(null)).httpStatus());
        assertEquals(404, assertThrows(ProducerException.class, () -> facade.getObject(R + "/nope")).httpStatus());
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
        assertEquals("schema", ProducerException.noSuchSchema("s").toBody().get("type"));

        Map<String, Object> ia = ProducerException.illegalArgument("bad").toBody();
        assertEquals("err.illegal.argument", ia.get("key"));
        assertEquals("bad", ia.get("msg"));
        assertEquals(400, ia.get("statusCode"));
    }
}

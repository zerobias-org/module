package com.zerobias.module.x12.producer;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.TestRows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.zerobias.module.x12.buffer.TestRows.FILE_A;
import static com.zerobias.module.x12.buffer.TestRows.FILE_B;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DESIGN §2.1 object hierarchy over a seeded buffer, driven through the facade + router. */
class ObjectTreeTest {

    private static final Gson GSON = new Gson();
    private static final SchemaRegistry SCHEMAS = SchemaRegistry.fromClasspath();
    private static final String R = ObjectTreeApi.RECEIVER;

    @TempDir
    Path dir;
    private BufferStore buffer;
    private ProducerFixture.StubPoller poller;
    private ObjectTree tree;
    private X12ProducerFacade facade;

    @BeforeEach
    void seed() throws Exception {
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false, new TestRows.MutableClock(TestRows.BASE.plusSeconds(3600)));
        ProducerFixture.seed(buffer, dir);
        poller = new ProducerFixture.StubPoller(dir);
        tree = new ObjectTree(buffer, SCHEMAS, () -> poller, ".done");
        facade = new X12ProducerFacade(buffer, tree, SCHEMAS,
            new X12Operations(buffer, X12ProducerFacade::toElement, () -> poller, SCHEMAS, RecastHook.NONE));
    }

    @AfterEach
    void close() throws Exception {
        buffer.close();
    }

    // --- static skeleton ---------------------------------------------------

    @Test
    void rootAndReceiverChildren() throws Exception {
        assertEquals("{\"id\":\"/\",\"name\":\"/\",\"objectClass\":[\"container\"]}", facade.getRootObject());

        JsonObject rootKids = page(facade.getChildren("/", 100, 1));
        assertEquals(1, rootKids.get("count").getAsInt());
        assertEquals(R, item(rootKids, 0).get("id").getAsString());

        JsonObject kids = page(facade.getChildren(R, 100, 1));
        assertEquals(12, kids.get("count").getAsInt());
        assertEquals(List.of("files", "inbox", "transactions", "by-type", "by-version", "by-sender", "by-source",
                "remittances", "claims", "service-lines", "stats", "ops"),
            names(kids), "/inbox is the live volume, /files the consumed projection, then the "
                + "business collections projected out of the graph (DESIGN §8.5)");
        assertEquals(1, kids.get("pageNumber").getAsInt(), "1-based on the wire");

        JsonObject all = item(kids, 2);
        assertEquals(List.of("collection"), classes(all));
        assertEquals(ObjectTree.ENVELOPE_SCHEMA, all.get("collectionSchema").getAsString());
        assertEquals(5, all.get("collectionSize").getAsLong(), "collectionSize = countWhere(all)");

        JsonObject stats = item(kids, 10);
        assertEquals(List.of("document"), classes(stats));
        assertEquals("schema:shared:x12.receiver-stats", stats.get("documentSchema").getAsString());

        assertEquals(404, assertThrows(ProducerException.class, () -> facade.getObject(R + "/nope")).httpStatus());
        assertEquals(404, assertThrows(ProducerException.class, () -> facade.getChildren(R + "/nope", 10, 1)).httpStatus());
    }

    @Test
    void opsFunctionsCarrySchemasAndThrows() throws Exception {
        JsonObject ops = page(facade.getChildren(R + "/ops", 100, 1));
        assertEquals(List.of("take", "ack", "release", "replay", "recast", "purge", "raw", "validate", "rescan",
            "packs"), names(ops));
        JsonObject take = item(ops, 0);
        assertEquals(List.of("function"), classes(take));
        assertEquals("schema:function:x12.ops.take:input", take.get("inputSchema").getAsString());
        assertEquals("schema:function:x12.ops.take:output", take.get("outputSchema").getAsString());
        assertTrue(take.getAsJsonObject("throws").has("backpressure"));
        assertTrue(SCHEMAS.has(take.get("inputSchema").getAsString()), "declared function schemas resolve");
        assertTrue(SCHEMAS.has(take.getAsJsonObject("throws").get("backpressure").getAsString()));
        assertEquals(404, assertThrows(ProducerException.class, () -> facade.getObject(R + "/ops/nope")).httpStatus());
        assertEquals(0, page(facade.getChildren(R + "/ops/take", 100, 1)).get("count").getAsInt(), "leaf has no children");
    }

    // --- /files ----------------------------------------------------------------

    @Test
    void filesAreContainerAndBinaryWithRoundTrippingIds() throws Exception {
        JsonObject files = page(facade.getChildren(R + "/files", 100, 1));
        assertEquals(2, files.get("count").getAsInt());
        JsonObject a = null;
        for (JsonElement e : files.getAsJsonArray("items")) {
            JsonObject f = e.getAsJsonObject();
            assertEquals(List.of("container", "binary"), classes(f));
            for (String k : List.of("fileName", "size", "mimeType", "checksum", "modified", "created", "tags")) {
                assertTrue(f.has(k), "binary field " + k);
            }
            assertEquals("application/EDI-X12", f.get("mimeType").getAsString());
            assertFalse(f.get("id").getAsString().substring((R + "/files/").length()).contains("/"),
                "fileId is percent-encoded into one path segment: " + f.get("id"));
            if (FILE_A.equals(f.get("fileId").getAsString())) {
                a = f;
            }
        }
        assertNotNull(a);
        assertEquals("remit-a.835", a.get("name").getAsString());
        assertEquals("sha-a", a.get("checksum").getAsString());
        assertEquals(ProducerFixture.FILE_A_BYTES.length, a.get("size").getAsLong());
        assertEquals(TestRows.BASE.toString(), a.get("created").getAsString());
        assertEquals(TestRows.BASE.minusSeconds(60).toString(), a.get("modified").getAsString());
        assertEquals(List.of("source:inbox", "status:consumed"), strings(a.getAsJsonArray("tags")));

        // the emitted id round-trips through getObject / getChildren / getCollectionElements
        String id = a.get("id").getAsString();
        assertEquals(R + "/files/" + ObjectTree.encodeSegment(FILE_A), id);
        assertEquals(a, GSON.fromJson(facade.getObject(id), JsonObject.class));
        JsonObject kids = page(facade.getChildren(id, 100, 1));
        assertEquals(1, kids.get("count").getAsInt());
        JsonObject txs = item(kids, 0);
        assertEquals(id + "/transactions", txs.get("id").getAsString());
        assertEquals(List.of("collection"), classes(txs));
        assertEquals(ObjectTree.ENVELOPE_SCHEMA, txs.get("collectionSchema").getAsString());
        assertEquals(3, txs.get("collectionSize").getAsLong());
        JsonObject elements = page(facade.getCollectionElements(id + "/transactions", null, null, null, 10, 1, null));
        assertEquals(3, elements.get("count").getAsLong(), "scoped file_id = ?");
        for (JsonElement e : elements.getAsJsonArray("items")) {
            assertEquals(FILE_A, e.getAsJsonObject().get("fileId").getAsString());
        }
        // a raw (un-encoded) path works too
        assertEquals(3, page(facade.getCollectionElements(R + "/files/" + FILE_A + "/transactions", null, null, null, 10, 1, null))
            .get("count").getAsLong());

        assertEquals(400, assertThrows(ProducerException.class, () -> tree.resolveCollection(id)).httpStatus(),
            "the file node itself is not a collection");
        assertEquals(404, assertThrows(ProducerException.class,
            () -> facade.getObject(R + "/files/" + ObjectTree.encodeSegment("/var/lib/x12/inbox/nope.835"))).httpStatus());
    }

    @Test
    void downloadBinaryReadsCurrentPathAnd404sWhenGone() throws Exception {
        BinaryContent a = facade.downloadBinary(R + "/files/" + ObjectTree.encodeSegment(FILE_A));
        assertArrayEquals(ProducerFixture.FILE_A_BYTES, a.bytes());
        assertEquals("application/EDI-X12", a.mimeType());
        assertEquals("remit-a.835", a.fileName());

        ProducerException gone = assertThrows(ProducerException.class,
            () -> facade.downloadBinary(R + "/files/" + ObjectTree.encodeSegment(FILE_B)));
        assertEquals(404, gone.httpStatus());
        assertEquals("gone", gone.toBody().get("reason"));
        assertEquals(FILE_B, gone.toBody().get("id"));
        assertEquals(3, page(facade.getCollectionElements(R + "/transactions", "(fileId=" + FILE_A + ")", null, null, 10, 1, null))
            .get("count").getAsLong(), "transactions remain after the bytes are gone");

        assertEquals(400, assertThrows(ProducerException.class, () -> facade.downloadBinary(R + "/files")).httpStatus());
        assertEquals(400, assertThrows(ProducerException.class,
            () -> facade.downloadBinary(R + "/files/" + ObjectTree.encodeSegment(FILE_A) + "/transactions")).httpStatus());
        assertEquals(404, assertThrows(ProducerException.class,
            () -> facade.downloadBinary(R + "/files/" + ObjectTree.encodeSegment("/x/y.835"))).httpStatus());
    }

    @Test
    void segmentEncodingRoundTrips() {
        for (String v : List.of("/var/lib/x12/inbox/a.835", "plain", "50%/off", "%2F", "a%b/c%25")) {
            assertEquals(v, ObjectTree.decodeSegment(ObjectTree.encodeSegment(v)), v);
            assertFalse(ObjectTree.encodeSegment(v).contains("/"));
        }
        assertEquals("/a/b", ObjectTree.decodeSegment("/a/b"), "un-encoded input decodes to itself");
        assertEquals("%zz", ObjectTree.decodeSegment("%zz"), "foreign escapes are left alone");
    }

    // --- discriminators ---------------------------------------------------------

    @Test
    void byTypeInterposesTheVersionOnlyWhenATypeSpansGuides() throws Exception {
        JsonObject byType = page(facade.getChildren(R + "/by-type", 100, 1));
        assertEquals(List.of("835", "837P"), names(byType));

        JsonObject t835 = item(byType, 0);
        assertEquals(List.of("collection"), classes(t835), "single guide → the type IS the collection");
        assertEquals("schema:table:x12.005010X221A1.835", t835.get("collectionSchema").getAsString());
        assertEquals(3, t835.get("collectionSize").getAsLong());
        assertEquals(R + "/by-type/835", t835.get("id").getAsString());
        assertEquals(404, assertThrows(ProducerException.class,
            () -> facade.getObject(R + "/by-type/835/005010X221A1")).httpStatus(), "version node exists only when required");

        JsonObject t837 = item(byType, 1);
        assertEquals(List.of("container"), classes(t837), "two guides → container");
        assertFalse(t837.has("collectionSchema"));
        assertEquals(400, assertThrows(ProducerException.class,
            () -> facade.getCollectionElements(R + "/by-type/837P", null, null, null, 10, 1, null)).httpStatus(),
            "a container is not a collection");

        JsonObject leaves = page(facade.getChildren(R + "/by-type/837P", 100, 1));
        assertEquals(List.of("005010X222", "005010X222A1"), names(leaves));
        JsonObject alt = item(leaves, 0);
        JsonObject a1 = item(leaves, 1);
        assertEquals(R + "/by-type/837P/005010X222", alt.get("id").getAsString());
        assertEquals(List.of("collection"), classes(alt));
        assertEquals(ObjectTree.ENVELOPE_SCHEMA, alt.get("collectionSchema").getAsString(), "unbundled guide → envelope");
        assertEquals(1, alt.get("collectionSize").getAsLong());
        assertEquals("schema:table:x12.005010X222A1.837P", a1.get("collectionSchema").getAsString());
        assertEquals(1, a1.get("collectionSize").getAsLong());

        JsonObject page = page(facade.getCollectionElements(R + "/by-type/837P/005010X222A1", null, null, null, 10, 1, null));
        assertEquals(1, page.get("count").getAsLong());
        assertEquals(ProducerFixture.KEY_B1, item(page, 0).get("elementKey").getAsString());

        assertEquals(404, assertThrows(ProducerException.class, () -> facade.getObject(R + "/by-type/999")).httpStatus());
        assertEquals(404, assertThrows(ProducerException.class, () -> facade.getObject(R + "/by-type/837P/005010X999")).httpStatus());
    }

    @Test
    void coarseFacetsAreEnvelopeCollectionsScopedOnTheRealColumn() throws Exception {
        JsonObject bySource = page(facade.getChildren(R + "/by-source", 100, 1));
        assertEquals(List.of("inbox", "sftp"), names(bySource));
        assertEquals(3, item(bySource, 0).get("collectionSize").getAsLong());
        assertEquals(2, item(bySource, 1).get("collectionSize").getAsLong());
        assertEquals(ObjectTree.ENVELOPE_SCHEMA, item(bySource, 0).get("collectionSchema").getAsString());
        assertEquals("source_name = 'sftp'", tree.resolveCollection(R + "/by-source/sftp").scopeWhere());
        assertEquals(2, page(facade.getCollectionElements(R + "/by-source/sftp", null, null, null, 10, 1, null)).get("count").getAsLong());

        JsonObject bySender = page(facade.getChildren(R + "/by-sender", 100, 1));
        assertEquals(List.of("CLINIC", "PAYERA"), names(bySender));
        assertEquals(3, item(bySender, 1).get("collectionSize").getAsLong());

        JsonObject byVersion = page(facade.getChildren(R + "/by-version", 100, 1));
        assertEquals(List.of("005010X221A1", "005010X222", "005010X222A1"), names(byVersion));
        assertEquals("gs08 = '005010X222'", tree.resolveCollection(R + "/by-version/005010X222").scopeWhere());

        assertEquals(404, assertThrows(ProducerException.class, () -> facade.getObject(R + "/by-source/nope")).httpStatus());
        assertEquals(404, assertThrows(ProducerException.class, () -> facade.getObject(R + "/by-sender/nope")).httpStatus());
        assertEquals(404, assertThrows(ProducerException.class,
            () -> facade.getCollectionElements(R + "/by-version/nope", null, null, null, 10, 1, null)).httpStatus());
    }

    @Test
    void collectionElementsArePagedFilteredAndScoped() throws Exception {
        JsonObject all = page(facade.getCollectionElements(R + "/transactions", null, null, null, 2, 1, null));
        assertEquals(5, all.get("count").getAsLong(), "count = total, not the page");
        assertEquals(2, all.getAsJsonArray("items").size());
        assertEquals(1, all.get("pageNumber").getAsInt());
        assertEquals(ProducerFixture.KEY_B2, item(all, 0).get("elementKey").getAsString(), "newest first");

        JsonObject filtered = page(facade.getCollectionElements(R + "/transactions",
            "(&(transactionType=835)(status=new))", null, null, 10, 1, null));
        assertEquals(3, filtered.get("count").getAsLong());
        for (JsonElement e : filtered.getAsJsonArray("items")) {
            assertEquals("835", e.getAsJsonObject().get("transactionType").getAsString());
            assertTrue(e.getAsJsonObject().has("header"), "typed body spread into the element");
        }

        // the user filter composes with the collection scope
        assertEquals(0, page(facade.getCollectionElements(R + "/by-source/sftp",
            "(transactionType=835)", null, null, 10, 1, null)).get("count").getAsLong());

        // a lease moves rows out of status=new
        facade.invokeFunction(R + "/ops/take", "{\"max\":1,\"filter\":\"(transactionType=835)\"}");
        assertEquals(2, page(facade.getCollectionElements(R + "/transactions",
            "(&(transactionType=835)(status=new))", null, null, 10, 1, null)).get("count").getAsLong());
        assertEquals(1, page(facade.getCollectionElements(R + "/transactions",
            "(status=in_flight)", null, null, 10, 1, null)).get("count").getAsLong());

        assertEquals(400, assertThrows(ProducerException.class,
            () -> facade.getCollectionElements(R + "/transactions", "(bad", null, null, 10, 1, null)).httpStatus());
    }

    // --- /stats ------------------------------------------------------------------

    @Test
    void statsDocumentCombinesPollerAndBuffer() throws Exception {
        Files.write(dir.resolve("old.835.done"), new byte[] {1});
        Files.write(dir.resolve("pending.835"), new byte[] {1});
        facade.invokeFunction(R + "/ops/take", "{\"max\":1}");

        JsonObject stats = GSON.fromJson(facade.getDocumentData(R + "/stats"), JsonObject.class);
        assertTrue(stats.get("up").getAsBoolean());
        assertEquals(TestRows.BASE.plusSeconds(100).toString(), stats.get("lastScan").getAsString());
        assertEquals(TestRows.BASE.plusSeconds(30).toString(), stats.get("lastConsumed").getAsString(),
            "falls back to files.consumed_at when the poller reports none");
        assertEquals(5, stats.get("bufferDepth").getAsLong(), "un-acked = new + in_flight");
        assertEquals(4, stats.get("newCount").getAsLong());
        assertEquals(1, stats.get("inFlightCount").getAsLong());
        assertEquals(0, stats.get("ackedCount").getAsLong());
        assertEquals(2, stats.get("fileCount").getAsLong());
        assertFalse(stats.get("backpressure").getAsBoolean());
        assertTrue(stats.get("oldestUnackedSec").getAsLong() >= 3600 - 40, "clock is BASE+1h, oldest row at BASE");
        assertEquals(2, stats.get("doneFileCount").getAsLong(), "remit-a.835.done + old.835.done (FILE_B's .done is gone)");
        assertTrue(stats.has("oldestDoneFileAgeSec"));
        assertTrue(stats.has("walBytes"));
        JsonArray sources = stats.getAsJsonArray("sources");
        assertEquals(1, sources.size());
        assertEquals("inbox", sources.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(dir.toString(), sources.get(0).getAsJsonObject().get("path").getAsString());

        assertEquals(400, assertThrows(ProducerException.class, () -> facade.getDocumentData(R + "/transactions")).httpStatus());
        assertEquals(404, assertThrows(ProducerException.class, () -> facade.getDocumentData(R + "/nope")).httpStatus());

        ObjectTree noPoller = new ObjectTree(buffer, () -> null);
        assertFalse((Boolean) noPoller.documentData(R + "/stats").get("up"));
    }

    // --- write surface -------------------------------------------------------------

    @Test
    void everyWriteOpIsRejectedThroughTheRouter() throws Exception {
        String coll = R + "/transactions";
        // This facade was built without allowFileManagement, so createChildObject and
        // deleteObject are refused by the gate exactly as the data writes are refused
        // outright. InboxFilesTest covers what they do once the flag is on.
        List<String[]> writes = List.of(
            new String[] {"ObjectsApi.createChildObject", R},
            new String[] {"ObjectsApi.updateObject", R},
            new String[] {"ObjectsApi.deleteObject", R},
            new String[] {"CollectionsApi.addCollectionElement", coll},
            new String[] {"CollectionsApi.updateCollectionElement", coll},
            new String[] {"CollectionsApi.deleteCollectionElement", coll},
            new String[] {"DocumentsApi.updateDocumentData", R + "/stats"});
        for (String[] w : writes) {
            Map<String, Object> args = Map.of("objectId", w[1], "elementKey", "k", "element", Map.of());
            ProducerException e = assertThrows(ProducerException.class,
                () -> OperationRouter.executeOperation(facade, w[0], args), w[0]);
            assertEquals(400, e.httpStatus(), w[0]);
            assertEquals("err.unsupported.operation", e.key(), w[0]);
        }

        // Upload is not a JSON op at all: its body is bytes, so the router refuses it as a
        // malformed call and the HTTP layer serves it (symmetric with downloadBinary).
        ProducerException upload = assertThrows(ProducerException.class,
            () -> OperationRouter.executeOperation(facade, "BinaryApi.uploadBinaryContent",
                Map.of("objectId", R + "/files/" + ObjectTree.encodeSegment(FILE_A))));
        assertEquals(400, upload.httpStatus());
        assertEquals("err.illegal.argument", upload.key());
        assertTrue(OperationRouter.isBinaryUpload("BinaryApi.uploadBinaryContent"));
        assertEquals(400, assertThrows(ProducerException.class,
            () -> facade.invokeFunction(R + "/transactions", "{}")).httpStatus(), "invoking a non-function");

        // and the read path through the router still works
        JsonObject viaRouter = page(OperationRouter.executeOperation(facade, "CollectionsApi.getCollectionElements",
            Map.of("objectId", R + "/by-type/835", "pageSize", 10, "pageNumber", 1)));
        assertEquals(3, viaRouter.get("count").getAsLong());
        JsonObject schema = GSON.fromJson(OperationRouter.executeOperation(facade, "SchemasApi.getSchema",
            Map.of("objectId", "schema:type:x12.005010X221A1.CLP")), JsonObject.class);
        assertEquals("schema:type:x12.005010X221A1.CLP", schema.get("id").getAsString());
        JsonObject empty = GSON.fromJson(OperationRouter.executeOperation(facade, "FunctionsApi.invokeFunction",
            Map.of("objectId", R + "/ops/take", "requestBody", Map.of("filter", "(transactionType=999)"))), JsonObject.class);
        assertTrue(empty.has("leaseId") && empty.get("leaseId").isJsonNull(), "serializeNulls keeps leaseId");
    }

    // --- helpers ------------------------------------------------------------------

    private static JsonObject page(String json) {
        JsonObject o = GSON.fromJson(json, JsonObject.class);
        assertTrue(o.has("items") && o.has("count") && o.has("pageSize") && o.has("pageNumber"), "PagedResults envelope");
        return o;
    }

    private static JsonObject item(JsonObject page, int i) {
        return page.getAsJsonArray("items").get(i).getAsJsonObject();
    }

    private static List<String> names(JsonObject page) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : page.getAsJsonArray("items")) {
            out.add(e.getAsJsonObject().get("name").getAsString());
        }
        return out;
    }

    private static List<String> classes(JsonObject o) {
        return strings(o.getAsJsonArray("objectClass"));
    }

    private static List<String> strings(JsonArray a) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : a) {
            out.add(e.getAsString());
        }
        return out;
    }
}

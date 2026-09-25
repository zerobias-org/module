package com.zerobias.module.x12.producer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.TestRows;
import com.zerobias.module.x12.buffer.TransactionRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.zerobias.module.x12.buffer.TestRows.FILE_A;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The facade skeleton: the mandatory PagedResults envelope, the DESIGN §5 envelope
 * overlay, the receive-only write rejections, the default 404s, and delegation to a
 * plugged-in {@link ObjectTreeApi}/{@link OperationsApi}.
 */
class X12ProducerFacadeTest {

    private static final Gson GSON = new Gson();

    /** A minimal tree: root → /x12-receiver → /transactions (all rows) + /ops/take. */
    private static ObjectTreeApi tinyTree() {
        return new ObjectTreeApi() {
            private Map<String, Object> obj(String id, String name, String cls) {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("id", id);
                o.put("name", name);
                o.put("objectClass", List.of(cls));
                return o;
            }

            public Map<String, Object> object(String id) {
                switch (id) {
                    case "/": return obj("/", "/", "container");
                    case "/x12-receiver": return obj("/x12-receiver", "x12-receiver", "container");
                    case "/x12-receiver/transactions": return obj(id, "transactions", "collection");
                    case "/x12-receiver/ops/take": return obj(id, "take", "function");
                    case "/x12-receiver/stats": return obj(id, "stats", "document");
                    default: throw ProducerException.noSuchObject(id);
                }
            }

            public List<Map<String, Object>> children(String id) {
                if ("/".equals(id)) {
                    return List.of(object("/x12-receiver"));
                }
                if ("/x12-receiver".equals(id)) {
                    return List.of(object("/x12-receiver/transactions"), object("/x12-receiver/stats"));
                }
                object(id);
                return List.of();
            }

            public Collection resolveCollection(String id) {
                if ("/x12-receiver/transactions".equals(id)) {
                    return new Collection(id, null, "schema:shared:x12.transaction-envelope");
                }
                object(id);
                throw ProducerException.unsupported("not a collection: " + id);
            }

            public Map<String, Object> documentData(String id) {
                object(id);
                return Map.of("bufferDepth", 2);
            }

            public BinaryContent downloadBinary(String id) {
                object(id);
                return new BinaryContent(id, java.nio.file.Path.of("a.835"), 7, BinaryContent.MIME_X12, "a.835");
            }
        };
    }

    @Test
    void skeletonServesRootAnd404sEverythingElse(@TempDir Path dir) throws Exception {
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false)) {
            X12ProducerFacade f = X12ProducerFacade.skeleton(b);
            JsonObject root = GSON.fromJson(f.getRootObject(), JsonObject.class);
            assertEquals("/", root.get("id").getAsString());
            assertEquals("/", root.get("name").getAsString(), "root id == name == '/'");
            JsonObject kids = GSON.fromJson(f.getChildren("/", 100, 1), JsonObject.class);
            assertEquals(0, kids.getAsJsonArray("items").size());
            assertEquals(0, kids.get("count").getAsInt());

            assertEquals(404, assertThrows(ProducerException.class, () -> f.getObject("/x12-receiver")).httpStatus());
            assertEquals(404, assertThrows(ProducerException.class,
                () -> f.getCollectionElements("/x12-receiver/transactions", null, null, null, 10, 1, null)).httpStatus());
            assertEquals(404, assertThrows(ProducerException.class, () -> f.getSchema("schema:shared:x12.file")).httpStatus());
            assertEquals(404, assertThrows(ProducerException.class,
                () -> f.invokeFunction("/x12-receiver/ops/take", "{}")).httpStatus());
            assertEquals(400, assertThrows(ProducerException.class, () -> f.downloadBinary("/")).httpStatus());
            assertEquals(400, assertThrows(ProducerException.class, () -> f.getObject("")).httpStatus());
            assertEquals(400, assertThrows(ProducerException.class, () -> f.getSchema(null)).httpStatus());
        }
    }

    @Test
    void paginatedOpsReturnThePagedResultsEnvelope(@TempDir Path dir) throws Exception {
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false)) {
            b.insertTransaction(TestRows.tx("1", "0001", 0));
            b.insertTransaction(TestRows.tx("1", "0002", 1));
            b.insertTransaction(TestRows.tx("1", "0003", 2));
            X12ProducerFacade f = new X12ProducerFacade(b, tinyTree(), null, null);

            JsonObject page = GSON.fromJson(
                f.getCollectionElements("/x12-receiver/transactions", null, null, null, 2, 2, null), JsonObject.class);
            assertTrue(page.has("items"), "PagedResults MUST carry items");
            assertEquals(3, page.get("count").getAsLong(), "count = total matches, not the page");
            assertEquals(2, page.get("pageSize").getAsInt());
            assertEquals(2, page.get("pageNumber").getAsInt(), "1-based on the wire");
            assertEquals(1, page.getAsJsonArray("items").size());
            assertEquals("0001", page.getAsJsonArray("items").get(0).getAsJsonObject().get("stControlNumber").getAsString(),
                "newest first → page 2 of size 2 holds the oldest");

            JsonObject filtered = GSON.fromJson(f.getCollectionElements("/x12-receiver/transactions",
                "(stControlNumber=0002)", null, null, 10, 1, null), JsonObject.class);
            assertEquals(1, filtered.get("count").getAsLong());

            JsonObject kids = GSON.fromJson(f.getChildren("/x12-receiver", 1, 2), JsonObject.class);
            assertEquals(2, kids.get("count").getAsInt());
            assertEquals("stats", kids.getAsJsonArray("items").get(0).getAsJsonObject().get("name").getAsString());

            assertEquals(400, assertThrows(ProducerException.class,
                () -> f.getCollectionElements("/x12-receiver/transactions", "(bad", null, null, 10, 1, null)).httpStatus());
            assertEquals(400, assertThrows(ProducerException.class,
                () -> f.getCollectionElements("/x12-receiver/transactions", null, null, null, 5000, 1, null)).httpStatus());

            String raw = X12ProducerFacade.pagedResults(List.of(), 0, 100, 1);
            assertEquals("{\"items\":[],\"count\":0,\"pageSize\":100,\"pageNumber\":1}", raw);
        }
    }

    @Test
    void toElementOverlaysTheEnvelopeOverTheBody(@TempDir Path dir) throws Exception {
        TransactionRow r = TestRows.tx("1", "0001", 0);
        // The body now comes from the object graph; toElement overlays the envelope on top.
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

        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false)) {
            b.insertTransaction(r);
            X12ProducerFacade f = new X12ProducerFacade(b, tinyTree(), null, null);
            JsonObject got = GSON.fromJson(f.getCollectionElement("/x12-receiver/transactions", r.elementKey()), JsonObject.class);
            assertEquals(r.elementKey(), got.get("elementKey").getAsString());
            assertEquals(404, assertThrows(ProducerException.class,
                () -> f.getCollectionElement("/x12-receiver/transactions", "nope")).httpStatus());
        }
    }

    @Test
    void writeSurfaceIsRejectedAndDelegatesDocumentsBinaryFunctions(@TempDir Path dir) throws Exception {
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false)) {
            OperationsApi ops = (fn, input) -> Map.of("fn", fn, "echo", input, "leaseId", java.util.Optional.empty());
            X12ProducerFacade f = new X12ProducerFacade(b, tinyTree(), null, (fn, input) -> {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("fn", fn);
                out.put("max", input.get("max"));
                out.put("leaseId", null);
                return out;
            });
            for (ProducerException e : List.of(
                    assertThrows(ProducerException.class, () -> f.createCollectionElement("/x", "{}")),
                    assertThrows(ProducerException.class, () -> f.updateCollectionElement("/x", "k", "{}")),
                    assertThrows(ProducerException.class, () -> f.deleteCollectionElement("/x", "k")))) {
                assertEquals(400, e.httpStatus());
                assertEquals("err.unsupported.operation", e.key());
            }

            String out = f.invokeFunction("/x12-receiver/ops/take", "{\"max\":5}");
            assertEquals("{\"fn\":\"take\",\"max\":5.0,\"leaseId\":null}", out, "serializeNulls on function output");
            assertEquals(400, assertThrows(ProducerException.class,
                () -> f.invokeFunction("/x12-receiver/transactions", "{}")).httpStatus(), "not a function");

            assertEquals("{\"bufferDepth\":2}", f.getDocumentData("/x12-receiver/stats"));
            BinaryContent bin = f.downloadBinary("/x12-receiver/stats");
            assertEquals("application/EDI-X12", bin.mimeType());
            assertEquals("a.835", bin.fileName());
            assertFalse(ops == null);
        }
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
    }
}

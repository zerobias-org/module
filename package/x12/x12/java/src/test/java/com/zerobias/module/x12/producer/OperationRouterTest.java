package com.zerobias.module.x12.producer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.Status;
import com.zerobias.module.x12.buffer.TestRows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RPC dispatch ({@code ApiClass.methodName} → facade), parameter handling and its error mapping. */
class OperationRouterTest {

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
    void dispatchesTheInterfaceOperationIds() throws Exception {
        JsonObject root = json(route("ObjectsApi.getRootObject", Map.of()));
        assertEquals("/", root.get("id").getAsString());

        JsonObject kids = json(route("ObjectsApi.getChildren", Map.of("objectId", R, "pageSize", "5", "pageNumber", 2)));
        assertEquals(5, kids.get("pageSize").getAsInt());
        assertEquals(2, kids.get("pageNumber").getAsInt());
        assertEquals(4, kids.getAsJsonArray("items").size());
        assertEquals(9, kids.get("count").getAsInt());

        JsonObject search = json(route("CollectionsApi.searchCollectionElements",
            Map.of("objectId", R + "/transactions", "filter", "(transactionType=837P)")));
        assertEquals(2, search.get("count").getAsLong());
        assertEquals(100, search.get("pageSize").getAsInt(), "default page size");
        assertEquals(1, search.get("pageNumber").getAsInt(), "default page number");

        assertEquals(ProducerFixture.KEY_A1, json(route("CollectionsApi.getCollectionElement",
            Map.of("objectId", R + "/transactions", "elementKey", ProducerFixture.KEY_A1))).get("elementKey").getAsString());
        assertTrue(json(route("DocumentsApi.getDocumentData", Map.of("objectId", R + "/stats"))).has("bufferDepth"));
        assertEquals("schema:shared:x12.file",
            json(route("SchemasApi.getSchema", Map.of("schemaId", "schema:shared:x12.file"))).get("id").getAsString());

        JsonObject verdict = json(route("FunctionsApi.validateFunctionInput", Map.of("objectId", R + "/ops/take",
            "validateFunctionInputRequest", Map.of("input", Map.of("filtr", "(x=1)")))));
        assertFalse(verdict.get("valid").getAsBoolean());

        assertEquals(400, assertThrows(ProducerException.class, () -> route("NopeApi.x", Map.of())).httpStatus());
        assertEquals(400, assertThrows(ProducerException.class, () -> route("noDot", Map.of())).httpStatus());
    }

    @Test
    void onlyExactWireNamesAreRouted() throws Exception {
        for (String alias : List.of("BinaryApi.downloadBinaryContent", "BinaryApi.uploadBinary", "DocumentsApi.getDocument",
                "DocumentsApi.updateDocument", "ObjectsApi.downloadBinary", "ObjectsApi.objectSearch",
                "CollectionsApi.searchChildObjects")) {
            ProducerException e = assertThrows(ProducerException.class,
                () -> route(alias, Map.of("objectId", R + "/stats")), alias);
            assertEquals(400, e.httpStatus(), alias);
            assertEquals("err.unsupported.operation", e.key(), alias);
            assertFalse(OperationRouter.isSupported(alias, true), alias);
        }
        assertTrue(OperationRouter.isBinaryDownload("BinaryApi.downloadBinary"));
        for (String notIt : List.of("BinaryApi.downloadBinaryContent", "ObjectsApi.downloadBinary", "X.downloadBinary",
                "downloadBinary", "BinaryApi.uploadBinaryContent")) {
            assertFalse(OperationRouter.isBinaryDownload(notIt), notIt);
        }
        assertFalse(OperationRouter.isBinaryDownload(null));
        assertEquals(400, assertThrows(ProducerException.class,
            () -> route("BinaryApi.downloadBinary", Map.of("objectId", "/"))).httpStatus(), "bytes are the HTTP layer's job");
        assertEquals(400, assertThrows(ProducerException.class,
            () -> route("SchemasApi.getSchema", Map.of("objectId", "schema:shared:x12.file"))).httpStatus(),
            "getSchema takes schemaId");
    }

    @Test
    void declaredParametersAreHonouredOrRejectedNeverIgnored() throws Exception {
        String coll = R + "/transactions";
        List<Object[]> rejected = List.of(
            new Object[] {"ObjectsApi.getChildren", R, "sortBy", List.of("name")},
            new Object[] {"ObjectsApi.getChildren", R, "sortDir", List.of("desc")},
            new Object[] {"ObjectsApi.getChildren", R, "type", List.of("collection")},
            new Object[] {"ObjectsApi.getChildren", R, "tags", List.of("source:inbox")},
            new Object[] {"ObjectsApi.getChildren", R, "pageToken", "t1"},
            new Object[] {"ObjectsApi.searchChildObjects", R, "filter", "(objectClass=collection)"},
            new Object[] {"ObjectsApi.searchChildObjects", R, "properties", List.of("name")},
            new Object[] {"ObjectsApi.searchChildObjects", R, "scope", "subtree"},
            new Object[] {"ObjectsApi.searchChildObjects", R, "sortBy", List.of("name")},
            new Object[] {"CollectionsApi.getCollectionElements", coll, "sortBy", List.of("receivedAt")},
            new Object[] {"CollectionsApi.getCollectionElements", coll, "properties", List.of("elementKey")},
            new Object[] {"CollectionsApi.searchCollectionElements", coll, "sortDir", List.of("asc")},
            new Object[] {"CollectionsApi.searchCollectionElements", coll, "pageToken", "t1"});
        for (Object[] r : rejected) {
            Map<String, Object> args = new HashMap<>();
            args.put("objectId", r[1]);
            args.put((String) r[2], r[3]);
            ProducerException e = assertThrows(ProducerException.class, () -> route((String) r[0], args),
                r[0] + " " + r[2]);
            assertEquals(400, e.httpStatus());
            assertEquals("err.unsupported.operation", e.key(), r[0] + " " + r[2]);
            assertTrue(e.getMessage().contains((String) r[2]), e.getMessage());
        }

        // unset (null / empty) is not "set"; the one supported scope and a boolean includeCount are accepted
        Map<String, Object> quiet = new HashMap<>();
        quiet.put("objectId", R);
        quiet.put("sortBy", List.of());
        quiet.put("pageToken", "");
        quiet.put("type", null);
        assertEquals(9, json(route("ObjectsApi.getChildren", quiet)).get("count").getAsInt());
        JsonObject searched = json(route("ObjectsApi.searchChildObjects",
            Map.of("objectId", R, "scope", "one_level", "includeCount", true, "pageSize", 2)));
        assertEquals(9, searched.get("count").getAsInt(), "count is always in the body");
        assertEquals(2, searched.getAsJsonArray("items").size());
        assertEquals(400, assertThrows(ProducerException.class,
            () -> route("ObjectsApi.searchChildObjects", Map.of("objectId", R, "scope", "everywhere"))).httpStatus());
        assertEquals(400, assertThrows(ProducerException.class,
            () -> route("ObjectsApi.searchChildObjects", Map.of("objectId", R, "includeCount", "maybe"))).httpStatus());
    }

    @Test
    void pagingArgumentsAreValidated() throws Exception {
        String coll = R + "/transactions";
        for (Object[] bad : new Object[][] {{"pageSize", "abc"}, {"pageSize", 1.5}, {"pageSize", 0}, {"pageSize", "1001"},
                {"pageNumber", "0"}, {"pageNumber", -2}, {"pageNumber", List.of(1)}}) {
            ProducerException e = assertThrows(ProducerException.class,
                () -> route("CollectionsApi.getCollectionElements", Map.of("objectId", coll, (String) bad[0], bad[1])),
                bad[0] + "=" + bad[1]);
            assertEquals(400, e.httpStatus(), bad[0] + "=" + bad[1]);
            assertEquals("err.illegal.argument", e.key(), bad[0] + "=" + bad[1]);
        }
        JsonObject page = json(route("CollectionsApi.getCollectionElements",
            Map.of("objectId", coll, "pageSize", 2.0, "pageNumber", " 3 ")));
        assertEquals(2, page.get("pageSize").getAsInt(), "a whole JSON number is an integer");
        assertEquals(3, page.get("pageNumber").getAsInt(), "so is a numeric string");
        assertEquals(1, page.getAsJsonArray("items").size());
    }

    @Test
    void functionBodiesAreParsedStrictly() throws Exception {
        for (Object body : List.of("{bad json", List.of(1, 2), 42)) {
            Map<String, Object> args = new HashMap<>();
            args.put("objectId", R + "/ops/take");
            args.put("requestBody", body);
            ProducerException e = assertThrows(ProducerException.class, () -> route("FunctionsApi.invokeFunction", args),
                String.valueOf(body));
            assertEquals(400, e.httpStatus(), String.valueOf(body));
        }
        assertEquals(400, assertThrows(ProducerException.class, () -> route("FunctionsApi.invokeFunction",
            Map.of("objectId", R + "/ops/purge", "requestBody", Map.of("olderthan", "P30D")))).httpStatus());
        assertEquals(0, buffer.count(Status.IN_FLIGHT));
        assertEquals("{\"replayed\":0}", route("FunctionsApi.invokeFunction", Map.of("objectId", R + "/ops/replay")),
            "no requestBody = no input");
    }

    @Test
    void isSupportedIsTrueOnlyForTheRoutedReadOperations() {
        for (String op : List.of("getRootObject", "getObject", "getChildren", "searchChildObjects", "getCollectionElements",
                "searchCollectionElements", "getCollectionElement", "getSchema", "getDocumentData", "invokeFunction",
                "validateFunctionInput", "downloadBinary", "ObjectsApi.getChildren", "BinaryApi.downloadBinary",
                "FunctionsApi.validateFunctionInput")) {
            assertTrue(OperationRouter.isSupported(op, false), op);
        }
        for (String op : List.of("objectSearch", "createChildObject", "updateObject", "deleteObject", "addCollectionElement",
                "updateCollectionElement", "deleteCollectionElement", "executeBulkOperations", "updateDocumentData",
                "uploadBinaryContent", "downloadBinaryContent", "ObjectsApi.downloadBinary", "nope", "")) {
            assertFalse(OperationRouter.isSupported(op, false), op);
        }
        assertFalse(OperationRouter.isSupported(null, false));
        // config.allowFileManagement opens exactly the three file-management ops, by exact name
        for (String op : List.of("createChildObject", "deleteObject", "uploadBinaryContent",
                "ObjectsApi.createChildObject", "ObjectsApi.deleteObject", "BinaryApi.uploadBinaryContent")) {
            assertTrue(OperationRouter.isSupported(op, true), op);
        }
        for (String op : List.of("uploadBinary", "BinaryApi.uploadBinary", "updateObject", "addCollectionElement",
                "updateDocumentData", "ObjectsApi.uploadBinaryContent")) {
            assertFalse(OperationRouter.isSupported(op, true), op);
        }
        assertTrue(OperationRouter.isBinaryUpload("BinaryApi.uploadBinaryContent"));
        for (String notIt : List.of("BinaryApi.uploadBinary", "uploadBinaryContent", "X.uploadBinaryContent")) {
            assertFalse(OperationRouter.isBinaryUpload(notIt), notIt);
        }
        assertDoesNotThrow(() -> route("ObjectsApi.getObject", Map.of("objectId", R)));
    }

    private String route(String method, Map<String, Object> args) throws Exception {
        return OperationRouter.executeOperation(facade, method, args);
    }

    private static JsonObject json(String s) {
        return GSON.fromJson(s, JsonObject.class);
    }
}

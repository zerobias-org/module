package com.zerobias.module.x12.producer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zerobias.module.x12.buffer.BufferStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RPC dispatch ({@code ApiClass.methodName} → facade) and its error mapping. */
class OperationRouterTest {

    private static final Gson GSON = new Gson();

    @Test
    void dispatchesObjectsSchemasAndRejectsWrites(@TempDir Path dir) throws Exception {
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false)) {
            X12ProducerFacade f = X12ProducerFacade.skeleton(b);
            JsonObject root = GSON.fromJson(OperationRouter.executeOperation(f, "ObjectsApi.getRootObject", Map.of()),
                JsonObject.class);
            assertEquals("/", root.get("id").getAsString());

            JsonObject kids = GSON.fromJson(OperationRouter.executeOperation(f, "ObjectsApi.getChildren",
                Map.of("objectId", "/", "pageSize", "50", "pageNumber", 1)), JsonObject.class);
            assertEquals(50, kids.get("pageSize").getAsInt());
            assertTrue(kids.has("items"));

            assertEquals(404, assertThrows(ProducerException.class, () -> OperationRouter.executeOperation(f,
                "SchemasApi.getSchema", Map.of("schemaId", "schema:shared:x12.file"))).httpStatus());
            assertEquals(400, assertThrows(ProducerException.class, () -> OperationRouter.executeOperation(f,
                "ObjectsApi.createChildObject", Map.of())).httpStatus());
            assertEquals(400, assertThrows(ProducerException.class, () -> OperationRouter.executeOperation(f,
                "ObjectsApi.objectSearch", Map.of("objectId", "/"))).httpStatus());
            assertEquals(400, assertThrows(ProducerException.class, () -> OperationRouter.executeOperation(f,
                "CollectionsApi.addCollectionElement", Map.of("objectId", "/x", "element", Map.of()))).httpStatus());
            assertEquals(400, assertThrows(ProducerException.class, () -> OperationRouter.executeOperation(f,
                "BinaryApi.uploadBinaryContent", Map.of())).httpStatus());
            assertEquals(400, assertThrows(ProducerException.class, () -> OperationRouter.executeOperation(f,
                "DocumentsApi.updateDocumentData", Map.of())).httpStatus());
            assertEquals(400, assertThrows(ProducerException.class, () -> OperationRouter.executeOperation(f,
                "NopeApi.x", Map.of())).httpStatus());
            assertEquals(400, assertThrows(ProducerException.class, () -> OperationRouter.executeOperation(f,
                "noDot", Map.of())).httpStatus());
        }
    }

    @Test
    void binaryDownloadIsRecognizedByItsInterfaceNameOnly(@TempDir Path dir) throws Exception {
        assertTrue(OperationRouter.isBinaryDownload("BinaryApi.downloadBinary"));
        assertFalse(OperationRouter.isBinaryDownload("BinaryApi.downloadBinaryContent"), "no aliases");
        assertFalse(OperationRouter.isBinaryDownload("ObjectsApi.downloadBinary"), "no aliases");
        assertFalse(OperationRouter.isBinaryDownload("BinaryApi.uploadBinaryContent"));
        assertFalse(OperationRouter.isBinaryDownload(null));
        assertTrue(OperationRouter.isBinaryUpload("BinaryApi.uploadBinaryContent"));
        assertFalse(OperationRouter.isBinaryUpload("BinaryApi.uploadBinary"), "no aliases");
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false)) {
            X12ProducerFacade f = X12ProducerFacade.skeleton(b);
            assertEquals(400, assertThrows(ProducerException.class, () -> OperationRouter.executeOperation(f,
                "BinaryApi.downloadBinary", Map.of("objectId", "/"))).httpStatus());
        }
    }

    @Test
    void onlyExactInterfaceNamesAndParameterNamesAreRouted(@TempDir Path dir) throws Exception {
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false)) {
            X12ProducerFacade f = new X12ProducerFacade(b, ObjectTreeApi.ROOT_ONLY, SchemaRegistry.functionsOnly(),
                OperationsApi.NONE, true);
            // DocumentsApi.getDocument was an alias of getDocumentData
            ProducerException alias = assertThrows(ProducerException.class, () -> OperationRouter.executeOperation(f,
                "DocumentsApi.getDocument", Map.of("objectId", "/")));
            assertEquals("err.unsupported.operation", alias.key());
            // getSchema takes schemaId, not objectId
            String fnSchema = SchemaRegistry.functionInputId("take");
            assertTrue(OperationRouter.executeOperation(f, "SchemasApi.getSchema", Map.of("schemaId", fnSchema))
                .contains(fnSchema));
            assertEquals(400, assertThrows(ProducerException.class, () -> OperationRouter.executeOperation(f,
                "SchemasApi.getSchema", Map.of("objectId", fnSchema))).httpStatus());
            // createChildObject reads createObjectRequest, never a flattened or differently-named body
            for (Map<String, Object> args : List.<Map<String, Object>>of(
                    Map.of("objectId", "/", "name", "x"),
                    Map.of("objectId", "/", "object", Map.of("name", "x")),
                    Map.of("objectId", "/", "body", Map.of("name", "x")))) {
                ProducerException e = assertThrows(ProducerException.class,
                    () -> OperationRouter.executeOperation(f, "ObjectsApi.createChildObject", args));
                assertEquals("err.illegal.argument", e.key(), args.toString());
                assertTrue(e.getMessage().contains("createObjectRequest"), e.getMessage());
            }
            // the real body name reaches the tree (which refuses: ROOT_ONLY is not a live directory)
            assertEquals("err.unsupported.operation", assertThrows(ProducerException.class,
                () -> OperationRouter.executeOperation(f, "ObjectsApi.createChildObject",
                    Map.of("objectId", "/", "createObjectRequest", Map.of("name", "x")))).key());
        }
    }

    @Test
    void isSupportedIsAnExplicitWhitelist() {
        for (String op : List.of("getRootObject", "ObjectsApi.getObject", "getChildren", "searchChildObjects",
                "getCollectionElements", "searchCollectionElements", "getCollectionElement", "getSchema",
                "getDocumentData", "invokeFunction", "FunctionsApi.validateFunctionInput", "downloadBinary")) {
            assertTrue(OperationRouter.isSupported(op, false), op);
        }
        for (String op : List.of("objectSearch", "ObjectsApi.objectSearch", "updateObject", "addCollectionElement",
                "updateCollectionElement", "deleteCollectionElement", "executeBulkOperations", "updateDocumentData",
                "somethingNew", "downloadBinaryContent", "getDocument", "")) {
            assertFalse(OperationRouter.isSupported(op, false), op);
            assertFalse(OperationRouter.isSupported(op, true), op);
        }
        assertFalse(OperationRouter.isSupported(null, true));
        for (String op : List.of("createChildObject", "ObjectsApi.deleteObject", "uploadBinaryContent")) {
            assertFalse(OperationRouter.isSupported(op, false), op + " is gated");
            assertTrue(OperationRouter.isSupported(op, true), op + " with allowFileManagement");
        }
    }

    @Test
    void unhonouredParametersAreRejectedNeverDropped(@TempDir Path dir) throws Exception {
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false)) {
            X12ProducerFacade f = X12ProducerFacade.skeleton(b);
            // getChildren: fixed order, no facets, no cursor
            for (Map.Entry<String, Object> p : List.<Map.Entry<String, Object>>of(
                    Map.entry("sortBy", List.of("name")), Map.entry("sortDir", List.of("desc")),
                    Map.entry("type", List.of("container")), Map.entry("tags", List.of("a")),
                    Map.entry("pageToken", "t"))) {
                ProducerException e = assertThrows(ProducerException.class, () -> OperationRouter.executeOperation(f,
                    "ObjectsApi.getChildren", Map.of("objectId", "/", p.getKey(), p.getValue())), p.getKey());
                assertEquals("err.unsupported.operation", e.key(), p.getKey());
            }
            // searchChildObjects: no filter/projection/sort/cursor; subtree scope unsupported
            for (Map.Entry<String, Object> p : List.<Map.Entry<String, Object>>of(
                    Map.entry("filter", "(name=x)"), Map.entry("properties", List.of("id")),
                    Map.entry("sortBy", "name"), Map.entry("pageToken", "t"), Map.entry("scope", "subtree"))) {
                ProducerException e = assertThrows(ProducerException.class, () -> OperationRouter.executeOperation(f,
                    "ObjectsApi.searchChildObjects", Map.of("objectId", "/", p.getKey(), p.getValue())), p.getKey());
                assertEquals("err.unsupported.operation", e.key(), p.getKey());
            }
            assertEquals("err.illegal.argument", assertThrows(ProducerException.class, () -> OperationRouter
                .executeOperation(f, "ObjectsApi.searchChildObjects", Map.of("objectId", "/", "scope", "everywhere")))
                .key());
            assertEquals("err.illegal.argument", assertThrows(ProducerException.class, () -> OperationRouter
                .executeOperation(f, "ObjectsApi.searchChildObjects", Map.of("objectId", "/", "includeCount", "yes")))
                .key());
            assertTrue(OperationRouter.executeOperation(f, "ObjectsApi.searchChildObjects",
                Map.of("objectId", "/", "scope", "one_level", "includeCount", true)).contains("\"items\""));
            // collections: pageToken and properties are not implemented
            for (String op : List.of("CollectionsApi.getCollectionElements", "CollectionsApi.searchCollectionElements")) {
                for (Map.Entry<String, Object> p : List.<Map.Entry<String, Object>>of(
                        Map.entry("pageToken", "t"), Map.entry("properties", List.of("elementKey")))) {
                    assertEquals("err.unsupported.operation", assertThrows(ProducerException.class,
                        () -> OperationRouter.executeOperation(f, op,
                            Map.of("objectId", "/x12-receiver/transactions", p.getKey(), p.getValue()))).key(),
                        op + " " + p.getKey());
                }
            }
        }
    }

    @Test
    void pagingBoundsAreEnforced(@TempDir Path dir) throws Exception {
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false)) {
            X12ProducerFacade f = X12ProducerFacade.skeleton(b);
            for (Map<String, Object> bad : List.<Map<String, Object>>of(
                    Map.of("pageNumber", 0), Map.of("pageNumber", -1), Map.of("pageSize", 0),
                    Map.of("pageSize", 1001), Map.of("pageSize", "ten"), Map.of("pageNumber", 1.5),
                    Map.of("pageSize", true))) {
                Map<String, Object> args = new java.util.HashMap<>(bad);
                args.put("objectId", "/");
                ProducerException e = assertThrows(ProducerException.class,
                    () -> OperationRouter.executeOperation(f, "ObjectsApi.getChildren", args), bad.toString());
                assertEquals("err.illegal.argument", e.key(), bad.toString());
            }
            JsonObject ok = GSON.fromJson(OperationRouter.executeOperation(f, "ObjectsApi.getChildren",
                Map.of("objectId", "/", "pageSize", 1000.0, "pageNumber", "2")), JsonObject.class);
            assertEquals(1000, ok.get("pageSize").getAsInt());
            assertEquals(2, ok.get("pageNumber").getAsInt());
            JsonObject dflt = GSON.fromJson(OperationRouter.executeOperation(f, "ObjectsApi.getChildren",
                Map.of("objectId", "/")), JsonObject.class);
            assertEquals(100, dflt.get("pageSize").getAsInt());
            assertEquals(1, dflt.get("pageNumber").getAsInt());
        }
    }
}

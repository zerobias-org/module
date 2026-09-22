package com.zerobias.module.x12.producer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zerobias.module.x12.buffer.BufferStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
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
    void binaryDownloadIsRecognizedAndNotRoutedAsJson(@TempDir Path dir) throws Exception {
        assertTrue(OperationRouter.isBinaryDownload("BinaryApi.downloadBinaryContent"));
        assertTrue(OperationRouter.isBinaryDownload("ObjectsApi.downloadBinary"));
        assertFalse(OperationRouter.isBinaryDownload("BinaryApi.uploadBinaryContent"));
        assertFalse(OperationRouter.isBinaryDownload(null));
        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false)) {
            X12ProducerFacade f = X12ProducerFacade.skeleton(b);
            assertEquals(400, assertThrows(ProducerException.class, () -> OperationRouter.executeOperation(f,
                "BinaryApi.downloadBinaryContent", Map.of("objectId", "/"))).httpStatus());
        }
    }
}

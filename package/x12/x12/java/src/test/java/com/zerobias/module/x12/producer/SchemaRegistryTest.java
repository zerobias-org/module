package com.zerobias.module.x12.producer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The classpath registry (codegen output) + the in-code function schemas (DESIGN §2.2). */
class SchemaRegistryTest {

    private static final Gson GSON = new Gson();

    @Test
    void classpathRegistryServesEveryDesignIdFamily() {
        SchemaRegistry r = SchemaRegistry.fromClasspath();
        assertTrue(r.size() > 600, "codegen output + builtins indexed, got " + r.size());

        for (String id : List.of(
                "schema:table:x12.005010X221A1.835",       // transaction set (collection schema)
                "schema:type:x12.005010X221A1.CLP",        // segment
                "schema:type:x12.005010X221A1.2100",       // loop
                "schema:type:x12.005010X221A1.C001",       // composite
                "schema:enum:x12.codes.1029",              // code set
                "schema:enum:x12.ops.FileStatus",          // receiver-owned enum
                "schema:shared:x12.transaction-envelope",
                "schema:shared:x12.file",
                "schema:shared:x12.receiver-stats",
                "schema:function:x12.ops.take:input",      // in-code
                "schema:function:x12.ops.take:output",
                "schema:function:x12.ops.rescan:output",
                SchemaRegistry.OPS_ERROR_SCHEMA,
                SchemaRegistry.OPS_VERDICT_SCHEMA)) {
            assertTrue(r.has(id), id);
            JsonObject schema = GSON.fromJson(r.getSchema(id), JsonObject.class);
            assertEquals(id, schema.get("id").getAsString(), "served JSON declares the requested id");
            assertTrue(schema.has("properties") || schema.has("dataTypes"), id + " is a schema document");
        }

        JsonObject table = GSON.fromJson(r.getSchema("schema:table:x12.005010X221A1.835"), JsonObject.class);
        assertTrue(table.getAsJsonArray("properties").size() > 0);

        JsonObject takeOut = GSON.fromJson(r.getSchema("schema:function:x12.ops.take:output"), JsonObject.class);
        assertEquals("leaseId", takeOut.getAsJsonArray("properties").get(0).getAsJsonObject().get("name").getAsString());
        JsonObject tx = takeOut.getAsJsonArray("properties").get(1).getAsJsonObject();
        assertTrue(tx.get("multi").getAsBoolean());
        assertEquals("schema:shared:x12.transaction-envelope",
            tx.getAsJsonObject("references").get("schemaId").getAsString());

        ProducerException e = assertThrows(ProducerException.class, () -> r.getSchema("schema:table:x12.nope.999"));
        assertEquals(404, e.httpStatus());
        assertEquals("err.no.such.object", e.key());
        assertEquals("schema", e.toBody().get("type"));
        assertFalse(r.has("schema:table:hl7v2.v27.ADT_A01"), "not the hl7 catalog");
    }

    @Test
    void everyFunctionHasInputAndOutput() {
        SchemaRegistry r = SchemaRegistry.functionsOnly();
        for (String fn : SchemaRegistry.OPS_FUNCTIONS) {
            assertTrue(r.has(SchemaRegistry.functionInputId(fn)), fn + ":input");
            assertTrue(r.has(SchemaRegistry.functionOutputId(fn)), fn + ":output");
        }
        assertEquals(SchemaRegistry.OPS_FUNCTIONS.size() * 2 + 2, r.size());
    }

    @Test
    void idsReconstructFromPaths() {
        assertEquals("schema:table:x12.005010X221A1.835",
            SchemaRegistry.idFromPath("schemas/005010X221A1/transactions/835.json".split("/"), () -> null));
        assertEquals("schema:type:x12.005010X222A1.2000A",
            SchemaRegistry.idFromPath("schemas/005010X222A1/loops/2000A.json".split("/"), () -> null));
        assertEquals("schema:type:x12.005010X221A1.C022",
            SchemaRegistry.idFromPath("schemas/005010X221A1/composites/C022.json".split("/"), () -> null));
        assertEquals("schema:enum:x12.codes.1032",
            SchemaRegistry.idFromPath("schemas/codes/1032.json".split("/"), () -> null));
        assertEquals("schema:enum:x12.ops.TransactionStatus",
            SchemaRegistry.idFromPath("schemas/ops/TransactionStatus.json".split("/"), () -> null));
        assertEquals("schema:shared:x12.file",
            SchemaRegistry.idFromPath("schemas/shared/file.json".split("/"), () -> "schema:shared:x12.file"));
        assertEquals(null, SchemaRegistry.idFromPath("schemas/index.json".split("/"), () -> null));
        assertEquals(null, SchemaRegistry.idFromPath("structure-index/005010X221A1.json".split("/"), () -> null));
    }

    @Test
    void directoryRegistryHonoursIndexJsonWhenPresent(@TempDir Path root) throws Exception {
        Path schemas = root.resolve("schemas");
        Files.createDirectories(schemas.resolve("custom"));
        Files.writeString(schemas.resolve("custom/thing.json"),
            "{\"id\":\"schema:type:x12.custom.thing\",\"dataTypes\":[],\"properties\":[]}");
        Files.writeString(schemas.resolve("index.json"), "{\"schema:type:x12.custom.thing\":\"custom/thing.json\"}");
        SchemaRegistry r = SchemaRegistry.fromDirectory(root);
        assertTrue(r.has("schema:type:x12.custom.thing"));
        assertTrue(r.getSchema("schema:type:x12.custom.thing").contains("custom.thing"));
        assertTrue(r.has("schema:function:x12.ops.take:input"), "builtins always present");

        // array form, and a scan fallback when the index is absent
        Files.writeString(schemas.resolve("index.json"),
            "[{\"id\":\"schema:type:x12.custom.thing\",\"path\":\"custom/thing.json\"}]");
        assertTrue(SchemaRegistry.fromDirectory(root).has("schema:type:x12.custom.thing"));

        Files.delete(schemas.resolve("index.json"));
        Files.createDirectories(schemas.resolve("005010X999/transactions"));
        Files.writeString(schemas.resolve("005010X999/transactions/999.json"),
            "{\"id\":\"schema:table:x12.005010X999.999\",\"dataTypes\":[],\"properties\":[]}");
        SchemaRegistry scanned = SchemaRegistry.fromDirectory(root);
        assertTrue(scanned.has("schema:table:x12.005010X999.999"));
        assertFalse(scanned.has("schema:type:x12.custom.thing"), "unknown subdir is not indexed by the scan");

        assertEquals(SchemaRegistry.OPS_FUNCTIONS.size() * 2 + 2, SchemaRegistry.fromDirectory(root.resolve("missing")).size());
    }
}

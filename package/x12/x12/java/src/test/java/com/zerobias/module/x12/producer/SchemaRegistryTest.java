package com.zerobias.module.x12.producer;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The classpath registry (codegen output) + the in-code function schemas (DESIGN §2.2). */
class SchemaRegistryTest {

    private static final Gson GSON = new Gson();
    private static final SchemaRegistry REGISTRY = SchemaRegistry.fromClasspath();

    @Test
    void classpathRegistryServesEveryDesignIdFamily() {
        for (String id : List.of(
                "schema:table:x12.005010X221A1.835",       // transaction set (collection schema), one per bundled guide
                "schema:table:x12.005010X222A1.837P",
                "schema:table:x12.005010X223A2.837I",
                "schema:table:x12.005010X214.277CA",
                "schema:table:x12.005010X231A1.999",
                "schema:type:x12.005010X221A1.CLP",        // segment
                "schema:type:x12.005010X221A1.2100",       // loop
                "schema:type:x12.005010X221A1.C001",       // composite
                "schema:enum:x12.codes.1029",              // code set
                "schema:enum:x12.ops.FileStatus",          // receiver-owned enum
                "schema:shared:x12.transaction-envelope",
                "schema:shared:x12.file",
                "schema:shared:x12.receiver-stats",
                "schema:shared:x12.receiver-stats-source",
                "schema:function:x12.ops.take:input",      // in-code
                "schema:function:x12.ops.take:output",
                "schema:function:x12.ops.rescan:output",
                SchemaRegistry.NOT_FOUND_ERROR_SCHEMA,
                SchemaRegistry.OPS_VERDICT_SCHEMA)) {
            assertTrue(REGISTRY.has(id), id);
            JsonObject schema = GSON.fromJson(REGISTRY.getSchema(id), JsonObject.class);
            assertEquals(id, schema.get("id").getAsString(), "served JSON declares the requested id");
            assertTrue(schema.has("properties") && schema.has("dataTypes"), id + " is a schema document");
        }

        JsonObject table = GSON.fromJson(REGISTRY.getSchema("schema:table:x12.005010X221A1.835"), JsonObject.class);
        assertTrue(table.getAsJsonArray("properties").size() > 0);

        JsonObject takeOut = GSON.fromJson(REGISTRY.getSchema("schema:function:x12.ops.take:output"), JsonObject.class);
        assertEquals("leaseId", takeOut.getAsJsonArray("properties").get(0).getAsJsonObject().get("name").getAsString());
        JsonObject tx = takeOut.getAsJsonArray("properties").get(1).getAsJsonObject();
        assertTrue(tx.get("multi").getAsBoolean());
        assertEquals("schema:shared:x12.transaction-envelope",
            tx.getAsJsonObject("references").get("schemaId").getAsString());

        // validate's rematerialized verdict always carries schemaId (null when the re-parse failed)
        JsonObject verdict = GSON.fromJson(REGISTRY.getSchema(SchemaRegistry.OPS_VERDICT_SCHEMA), JsonObject.class);
        List<String> verdictProps = new ArrayList<>();
        verdict.getAsJsonArray("properties").forEach(p -> verdictProps.add(p.getAsJsonObject().get("name").getAsString()));
        assertEquals(List.of("valid", "errors", "schemaId"), verdictProps);

        ProducerException e = assertThrows(ProducerException.class, () -> REGISTRY.getSchema("schema:table:x12.nope.999"));
        assertEquals(404, e.httpStatus());
        assertEquals("err.no.such.object", e.key());
        assertEquals("schema", e.toBody().get("type"));
        assertFalse(REGISTRY.has("schema:table:hl7v2.v27.ADT_A01"), "not the hl7 catalog");
    }

    @Test
    void everyFunctionInputSchemaIsTheValidatorsDefinition() {
        for (String fn : SchemaRegistry.OPS_FUNCTIONS) {
            assertTrue(REGISTRY.has(SchemaRegistry.functionOutputId(fn)), fn + ":output");
            JsonObject input = GSON.fromJson(REGISTRY.getSchema(SchemaRegistry.functionInputId(fn)), JsonObject.class);
            List<String> declared = new ArrayList<>();
            for (JsonElement p : input.getAsJsonArray("properties")) {
                JsonObject prop = p.getAsJsonObject();
                declared.add(prop.get("name").getAsString() + ":" + prop.get("dataType").getAsString()
                    + (prop.get("required").getAsBoolean() ? "!" : "") + (prop.has("multi") ? "[]" : ""));
            }
            List<String> enforced = new ArrayList<>();
            for (SchemaRegistry.Param p : SchemaRegistry.functionInputs(fn)) {
                enforced.add(p.name() + ":" + p.dataType() + (p.required() ? "!" : "") + (p.multi() ? "[]" : ""));
            }
            assertEquals(enforced, declared, fn);
        }
        assertEquals(List.of("leaseId:string!", "elementKeys:string[]"),
            SchemaRegistry.functionInputs("ack").stream()
                .map(p -> p.name() + ":" + p.dataType() + (p.required() ? "!" : "") + (p.multi() ? "[]" : "")).toList());
    }

    @Test
    void notFoundErrorSchemaDescribesTheBodyActuallyRaised() {
        JsonObject schema = GSON.fromJson(REGISTRY.getSchema(SchemaRegistry.NOT_FOUND_ERROR_SCHEMA), JsonObject.class);
        List<String> names = new ArrayList<>();
        for (JsonElement p : schema.getAsJsonArray("properties")) {
            names.add(p.getAsJsonObject().get("name").getAsString());
        }
        assertEquals(new ArrayList<>(ProducerException.noSuchLease("L").toBody().keySet()), names);
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
}

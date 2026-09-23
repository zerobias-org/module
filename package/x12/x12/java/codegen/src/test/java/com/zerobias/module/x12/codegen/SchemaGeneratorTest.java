package com.zerobias.module.x12.codegen;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end: generate two guides (one with an alias) into a temp dir and check the layout (DESIGN §6). */
class SchemaGeneratorTest {

    private static final Gson GSON = new Gson();

    @Test
    void generatesLayoutForGuideAndAlias(@TempDir Path out) throws IOException {
        new SchemaGenerator(out).run(SchemaGenerator.resolveGuides("005010X221A1,005010X223A1"), false);

        // 835.
        final JsonObject table = read(out.resolve("schemas/005010X221A1/transactions/835.json"));
        assertEquals("schema:table:x12.005010X221A1.835", table.get("id").getAsString());
        final JsonArray props = table.getAsJsonArray("properties");
        assertTrue(has(props, "detail"), "top-level loop composition");
        assertTrue(has(props, "elementKey"), "envelope overlay appended");
        assertTrue(has(props, "parserErrorCount"));
        final JsonObject elementKey = find(props, "elementKey");
        assertTrue(elementKey.get("primaryKey").getAsBoolean());
        assertTrue(elementKey.get("required").getAsBoolean());
        assertTrue(Files.exists(out.resolve("schemas/005010X221A1/loops/2100.json")));
        assertTrue(Files.exists(out.resolve("schemas/005010X221A1/segments/CLP.json")));
        assertTrue(Files.exists(out.resolve("schemas/005010X221A1/composites/C003.json")));
        assertTrue(Files.exists(out.resolve("structure-index/005010X221A1.json")));

        // dataTypes[] lists exactly the core types the properties use.
        final JsonObject clp = read(out.resolve("schemas/005010X221A1/segments/CLP.json"));
        final JsonArray dts = clp.getAsJsonArray("dataTypes");
        assertEquals(2, dts.size(), "string + decimal");

        // 837I emitted under the A2 id the wire carries, plus the A1 alias copy.
        assertEquals("schema:table:x12.005010X223A2.837I",
            read(out.resolve("schemas/005010X223A2/transactions/837I.json")).get("id").getAsString());
        assertEquals("schema:table:x12.005010X223A1.837I",
            read(out.resolve("schemas/005010X223A1/transactions/837I.json")).get("id").getAsString());
        final JsonObject aliasIndex = read(out.resolve("structure-index/005010X223A1.json"));
        assertEquals("005010X223A2", aliasIndex.get("aliasOf").getAsString());
        assertEquals("005010X223A1", aliasIndex.get("gs08").getAsString());
        assertTrue(read(out.resolve("structure-index/005010X223A2.json")).get("aliasOf") == null);

        // Code sets, ops enums, shared.
        final JsonObject e1029 = read(out.resolve("schemas/codes/1029.json"));
        assertEquals("schema:enum:x12.codes.1029", e1029.get("id").getAsString());
        final JsonObject enumType = e1029.getAsJsonArray("dataTypes").get(0).getAsJsonObject();
        assertTrue(enumType.get("isEnum").getAsBoolean());
        assertTrue(enumType.getAsJsonArray("values").size() >= 10);
        assertEquals("code", e1029.getAsJsonArray("properties").get(0).getAsJsonObject().get("name").getAsString());
        assertTrue(Files.exists(out.resolve("schemas/codes/156.json")), "states codeset bound via external=");
        assertTrue(Files.exists(out.resolve("schemas/ops/TransactionStatus.json")));
        assertTrue(Files.exists(out.resolve("schemas/ops/EnvelopeOrigin.json")));
        assertTrue(Files.exists(out.resolve("schemas/ops/FileStatus.json")));
        for (String f : List.of("transaction-envelope", "file", "receiver-stats", "receiver-stats-source")) {
            final JsonObject s = read(out.resolve("schemas/shared/" + f + ".json"));
            assertEquals("schema:shared:x12." + f, s.get("id").getAsString());
        }

        // Every emitted file's id is canonical and its path matches its id.
        try (Stream<Path> files = Files.walk(out.resolve("schemas"))) {
            for (Path p : files.filter(Files::isRegularFile).toList()) {
                final String id = read(p).get("id").getAsString();
                assertTrue(SchemaIds.isValid(id), id);
                final String name = p.getFileName().toString().replace(".json", "");
                assertTrue(id.endsWith("." + name) || id.endsWith(":x12." + name), p + " vs " + id);
            }
        }
    }

    private static JsonObject read(Path p) throws IOException {
        return GSON.fromJson(Files.readString(p), JsonObject.class);
    }

    private static boolean has(JsonArray props, String name) {
        return find(props, name) != null;
    }

    private static JsonObject find(JsonArray props, String name) {
        for (var e : props) {
            if (name.equals(e.getAsJsonObject().get("name").getAsString())) {
                return e.getAsJsonObject();
            }
        }
        return null;
    }
}

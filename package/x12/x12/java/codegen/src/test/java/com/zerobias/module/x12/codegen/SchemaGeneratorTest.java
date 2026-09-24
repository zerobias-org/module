package com.zerobias.module.x12.codegen;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end: generate guides into a temp dir and check the layout (DESIGN §6). */
class SchemaGeneratorTest {

    private static final Gson GSON = new Gson();

    @Test
    void generatesLayoutForCanonicalGuidesOnly(@TempDir Path out) throws IOException {
        // An alias on the command line resolves to its canonical guide; nothing is emitted under the alias.
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

        // 837I emitted once, under the A2 id the wire carries.
        assertEquals("schema:table:x12.005010X223A2.837I",
            read(out.resolve("schemas/005010X223A2/transactions/837I.json")).get("id").getAsString());
        assertFalse(Files.exists(out.resolve("schemas/005010X223A1")), "no alias schema copies");
        assertFalse(Files.exists(out.resolve("structure-index/005010X223A1.json")), "no alias index");
        assertEquals("005010X223A2", read(out.resolve("structure-index/005010X223A2.json")).get("gs08").getAsString());
        try (Stream<Path> dirs = Files.list(out.resolve("structure-index"))) {
            assertEquals(2, dirs.count(), "one index per canonical guide");
        }

        // Code sets, ops enums, shared.
        final JsonObject e1029 = read(out.resolve("schemas/codes/1029.json"));
        assertEquals("schema:enum:x12.codes.1029", e1029.get("id").getAsString());
        final JsonObject enumType = e1029.getAsJsonArray("dataTypes").get(0).getAsJsonObject();
        assertTrue(enumType.get("isEnum").getAsBoolean());
        assertTrue(enumType.getAsJsonArray("values").size() >= 10);
        assertEquals("code", e1029.getAsJsonArray("properties").get(0).getAsJsonObject().get("name").getAsString());
        final JsonObject states = read(out.resolve("schemas/codes/states.json"));
        assertEquals("schema:enum:x12.codes.states", states.get("id").getAsString(), "codeset bound via external= keeps its id");
        assertEquals("x12CodeStates", states.getAsJsonArray("dataTypes").get(0).getAsJsonObject().get("name").getAsString());
        assertTrue(Files.exists(out.resolve("schemas/ops/TransactionStatus.json")));
        assertTrue(Files.exists(out.resolve("schemas/ops/EnvelopeOrigin.json")));
        assertTrue(Files.exists(out.resolve("schemas/ops/FileStatus.json")));
        for (String f : List.of("transaction-envelope", "file", "receiver-stats", "receiver-stats-source")) {
            final JsonObject s = read(out.resolve("schemas/shared/" + f + ".json"));
            assertEquals("schema:shared:x12." + f, s.get("id").getAsString());
        }

        // Every emitted schema file's id is canonical and its path matches its id.
        // index.json is the enumeration, not a schema — skip it.
        final List<Path> schemaFiles = new ArrayList<>();
        try (Stream<Path> files = Files.walk(out.resolve("schemas"))) {
            for (Path p : files.filter(Files::isRegularFile).toList()) {
                if (p.getFileName().toString().equals("index.json")) {
                    continue;
                }
                schemaFiles.add(p);
                final String id = read(p).get("id").getAsString();
                assertTrue(SchemaIds.isValid(id), id);
                final String name = p.getFileName().toString().replace(".json", "");
                assertTrue(id.endsWith("." + name) || id.endsWith(":x12." + name), p + " vs " + id);
            }
        }

        // schemas/index.json enumerates every schema file, by id, with a resolvable path.
        final JsonObject index = read(out.resolve("schemas/index.json"));
        assertEquals(schemaFiles.size(), index.size(), "index.json covers every emitted schema");
        for (Path p : schemaFiles) {
            final String id = read(p).get("id").getAsString();
            assertTrue(index.has(id), "index.json is missing " + id);
            assertEquals(out.resolve("schemas").relativize(p).toString(), index.get(id).getAsString(), id);
        }

        // packs.json: one pack per guide label, plus codes and core (DESIGN §7).
        final JsonArray packs = GSON.fromJson(Files.readString(out.resolve("packs.json")), JsonArray.class);
        final Map<String, JsonObject> byName = new LinkedHashMap<>();
        int declared = 0;
        for (var el : packs) {
            final JsonObject pack = el.getAsJsonObject();
            byName.put(pack.get("name").getAsString(), pack);
            assertEquals("x12", pack.get("namespace").getAsString());
            assertEquals("bundled", pack.get("source").getAsString());
            assertFalse(pack.has("version"), "a bundled pack's version IS the module's");
            declared += pack.get("schemaCount").getAsInt();
            assertEquals(pack.get("schemaCount").getAsInt(), pack.getAsJsonArray("schemaIds").size());
            for (var id : pack.getAsJsonArray("schemaIds")) {
                assertTrue(index.has(id.getAsString()), "pack declares an unemitted id: " + id);
            }
        }
        assertEquals(index.size(), declared, "every emitted schema belongs to exactly one pack");

        final JsonObject guide = byName.get("x12-guide-005010X223A2");
        assertEquals("005010X223A2", guide.get("gs08").getAsString());
        assertEquals("837I", guide.get("transactionType").getAsString());
        assertEquals("structure-index/005010X223A2.json", guide.get("structureIndex").getAsString());
        assertEquals("x12.005010X223A2.*", guide.get("idScope").getAsString());
        assertTrue(guide.getAsJsonArray("schemaIds").contains(
            GSON.toJsonTree("schema:table:x12.005010X223A2.837I")), "the table id is the guide pack's");

        assertEquals("005010X223A2", byName.get("x12-guide-005010X223A1").get("aliasOf").getAsString(),
            "the alias label X223A1 gets its own pack, pointing back at canonical X223A2");
        assertFalse(byName.get("x12-guide-005010X223A2").has("aliasOf"), "the canonical label has no aliasOf");
        assertFalse(byName.get("x12-codes").has("gs08"), "code sets span guides");
        assertFalse(byName.get("x12-core").has("structureIndex"), "the core pack has no guide structure");
    }

    @Test
    void fullRunClearsGuidesNoLongerInTheTable(@TempDir Path out) throws IOException {
        // Left over from a build that still emitted alias copies and the 820.
        Files.createDirectories(out.resolve("schemas/005010X218/transactions"));
        Files.writeString(out.resolve("schemas/005010X218/transactions/820.json"), "{}");
        Files.createDirectories(out.resolve("structure-index"));
        Files.writeString(out.resolve("structure-index/005010X223A1.json"), "{}");

        new SchemaGenerator(out).run(SchemaGenerator.resolveGuides("005010X231A1"), true);

        assertFalse(Files.exists(out.resolve("schemas/005010X218")));
        assertFalse(Files.exists(out.resolve("structure-index/005010X223A1.json")));
        assertTrue(Files.exists(out.resolve("structure-index/005010X231A1.json")));
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

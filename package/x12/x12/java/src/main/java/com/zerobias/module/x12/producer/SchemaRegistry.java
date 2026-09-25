package com.zerobias.module.x12.producer;

import com.google.gson.Gson;
import com.zerobias.module.x12.producer.mapping.EntityMapping;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Serves the generated X12 schemas (DESIGN §2.2 / §6) by canonical id for the
 * {@code getSchema} operation and for the object tree's collection-schema lookups.
 *
 * <p><b>Lazy by id → resource.</b> The codegen emits ~700 small files under
 * {@code classpath:schemas/}; the registry indexes only the <em>ids</em> at boot and
 * reads a file's JSON on demand. Ids are reconstructed from paths where the layout
 * makes that unambiguous —
 * <pre>
 * schemas/&lt;GS08&gt;/transactions/&lt;TS&gt;.json   → schema:table:x12.&lt;GS08&gt;.&lt;TS&gt;
 * schemas/&lt;GS08&gt;/{loops,segments,composites}/&lt;xid&gt;.json → schema:type:x12.&lt;GS08&gt;.&lt;xid&gt;
 * schemas/codes/&lt;dataEle&gt;.json                 → schema:enum:x12.codes.&lt;dataEle&gt;
 * schemas/ops/&lt;Name&gt;.json                      → schema:enum:x12.ops.&lt;Name&gt;
 * schemas/shared/&lt;name&gt;.json                   → the file's declared id (read once)
 * </pre>
 * and, when the codegen ships a {@code schemas/index.json} ({@code {"<id>": "<path
 * relative to schemas/>"}} or {@code [{"id","path"}]}), that index wins over the scan.
 *
 * <p>The {@code schema:function:x12.ops.<fn>:input|output} schemas (DESIGN §2.5) and the
 * two small shared shapes they reference ({@code schema:shared:x12.ops-error},
 * {@code schema:shared:x12.ops-verdict}) are not emitted by the codegen; they are
 * generated here in code (static, in-memory).
 *
 * <p>{@code structure-index/*.json} (no schema id, different tree) is never scanned.
 */
public final class SchemaRegistry implements SchemaRegistryApi {

    private static final Gson GSON = new Gson();
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

    static final String CATALOG = "x12";
    static final String ENVELOPE_SCHEMA = "schema:shared:" + CATALOG + ".transaction-envelope";
    static final String OPS_ERROR_SCHEMA = "schema:shared:" + CATALOG + ".ops-error";
    static final String OPS_VERDICT_SCHEMA = "schema:shared:" + CATALOG + ".ops-verdict";

    /** schemaId → a loader that yields the raw JSON (classpath resource, file, or in-memory). */
    private final Map<String, Supplier<String>> loaders = new LinkedHashMap<>();

    private SchemaRegistry() {
    }

    /** An empty registry plus the in-code function/ops schemas (tests, or a build with no codegen output). */
    public static SchemaRegistry functionsOnly() {
        SchemaRegistry r = new SchemaRegistry();
        r.addBuiltins();
        return r;
    }

    /**
     * Build a registry by scanning {@code schemaRoot} (a dir containing {@code schemas/}).
     * Ids are reconstructed from paths; content is read lazily. Only the built-in
     * function schemas are present if the dir is absent.
     */
    public static SchemaRegistry fromDirectory(Path schemaRoot) {
        SchemaRegistry r = new SchemaRegistry();
        Path schemas = schemaRoot.resolve("schemas");
        if (Files.isDirectory(schemas)) {
            Path index = schemas.resolve("index.json");
            if (Files.isRegularFile(index)) {
                r.indexFromIndexJson(readFile(index), rel -> () -> readFile(schemas.resolve(rel)));
            } else {
                try (Stream<Path> walk = Files.walk(schemas)) {
                    walk.filter(p -> p.toString().endsWith(".json"))
                        .forEach(p -> r.indexFile(schemaRoot, p));
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to scan schema tree at " + schemas, e);
                }
            }
        }
        r.addBuiltins();
        return r;
    }

    /**
     * Build a registry from the {@code /schemas} resources on the classpath — how the
     * packaged module serves them. Handles both the shaded jar ({@code jar:} URL) and an
     * exploded {@code target/classes} ({@code file:} URL); either way a schema is read
     * from the classpath on demand. Only the built-in function schemas if absent.
     */
    public static SchemaRegistry fromClasspath() {
        SchemaRegistry r = new SchemaRegistry();
        URL root = SchemaRegistry.class.getResource("/schemas");
        if (root != null) {
            String index = readClasspathOrNull("schemas/index.json");
            if (index != null) {
                r.indexFromIndexJson(index, rel -> () -> readClasspath("schemas/" + rel));
            } else {
                r.scanClasspath(root);
            }
        }
        r.addBuiltins();
        return r;
    }

    private void scanClasspath(URL root) {
        try {
            if ("jar".equals(root.getProtocol())) {
                JarURLConnection conn = (JarURLConnection) root.openConnection();
                conn.setUseCaches(false);   // we own + close this JarFile, not the shared one
                try (JarFile jar = conn.getJarFile()) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry e = entries.nextElement();
                        if (!e.isDirectory()
                                && e.getName().startsWith("schemas/") && e.getName().endsWith(".json")) {
                            indexClasspath(e.getName());
                        }
                    }
                }
            } else if ("file".equals(root.getProtocol())) {
                Path schemasDir = Path.of(root.toURI());
                Path classpathRoot = schemasDir.getParent();   // parent of /schemas
                try (Stream<Path> walk = Files.walk(schemasDir)) {
                    walk.filter(p -> p.toString().endsWith(".json"))
                        .forEach(p -> indexClasspath(classpathRoot.relativize(p).toString().replace('\\', '/')));
                }
            }
        } catch (IOException | URISyntaxException e) {
            throw new UncheckedIOException("Failed to scan classpath schema tree", new IOException(e));
        }
    }

    // --- indexing (id from path; content deferred) -------------------------

    /** Index one classpath resource ({@code schemas/...json}) by its reconstructed/read id. */
    private void indexClasspath(String resource) {
        String[] parts = resource.split("/");
        String id = idFromPath(parts, () -> readIdFromString(readClasspathOrNull(resource)));
        if (id != null) {
            loaders.put(id, () -> readClasspath(resource));
        }
    }

    /** Index one filesystem schema file by its reconstructed/read id. */
    private void indexFile(Path schemaRoot, Path file) {
        String[] parts = schemaRoot.relativize(file).toString().replace('\\', '/').split("/");
        String id = idFromPath(parts, () -> readIdFromString(readFile(file)));
        if (id != null) {
            loaders.put(id, () -> readFile(file));
        }
    }

    /**
     * Index from a codegen-written {@code index.json}: either {@code {"<id>": "<path>"}}
     * or {@code [{"id": "...", "path": "..."}]}, paths relative to {@code schemas/}.
     */
    private void indexFromIndexJson(String json, java.util.function.Function<String, Supplier<String>> loaderFor) {
        JsonElement root = GSON.fromJson(json, JsonElement.class);
        if (root == null) {
            return;
        }
        if (root.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
                if (e.getKey().startsWith("schema:") && e.getValue().isJsonPrimitive()) {
                    loaders.put(e.getKey(), loaderFor.apply(e.getValue().getAsString()));
                }
            }
        } else if (root.isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray()) {
                if (el.isJsonObject() && el.getAsJsonObject().has("id") && el.getAsJsonObject().has("path")) {
                    String id = el.getAsJsonObject().get("id").getAsString();
                    if (id.startsWith("schema:")) {
                        loaders.put(id, loaderFor.apply(el.getAsJsonObject().get("path").getAsString()));
                    }
                }
            }
        }
    }

    /**
     * The schema id for a {@code schemas/...} path (DESIGN §2.2). Guide-scoped files
     * reconstruct without a read; {@code shared/} files fall back to {@code declaredId}
     * which reads the file's own id. Returns null for anything else (e.g. {@code index.json}).
     */
    static String idFromPath(String[] parts, Supplier<String> declaredId) {
        if (parts.length < 3 || !"schemas".equals(parts[0])) {
            return null;
        }
        if (parts.length == 3) {
            String name = stripJson(parts[2]);
            switch (parts[1]) {
                case "codes":  return "schema:enum:" + CATALOG + ".codes." + name;
                case "ops":    return "schema:enum:" + CATALOG + ".ops." + name;
                case "shared": return declaredId.get();   // e.g. schema:shared:x12.transaction-envelope
                default:       return null;
            }
        }
        if (parts.length == 4) {
            String gs08 = parts[1];
            String name = stripJson(parts[3]);
            switch (parts[2]) {
                case "transactions": return "schema:table:" + CATALOG + "." + gs08 + "." + name;
                case "loops":
                case "segments":
                case "composites":   return "schema:type:" + CATALOG + "." + gs08 + "." + name;
                default:             return null;
            }
        }
        return null;
    }

    private static String stripJson(String fileName) {
        return fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - ".json".length()) : fileName;
    }

    private static String readIdFromString(String json) {
        if (json == null) {
            return null;
        }
        JsonObject obj = GSON.fromJson(json, JsonObject.class);
        if (obj != null && obj.has("id")) {
            String id = obj.get("id").getAsString();
            if (id.startsWith("schema:")) {
                return id;
            }
        }
        return null;
    }

    private static String readClasspathOrNull(String resource) {
        InputStream in = SchemaRegistry.class.getResourceAsStream("/" + resource);
        if (in == null) {
            return null;
        }
        try (InputStream s = in) {
            return new String(s.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static String readClasspath(String resource) {
        String s = readClasspathOrNull(resource);
        if (s == null) {
            throw new UncheckedIOException(new IOException("schema resource vanished: " + resource));
        }
        return s;
    }

    private static String readFile(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read schema " + file, e);
        }
    }

    // --- SchemaRegistryApi ---------------------------------------------------

    @Override
    public String getSchema(String schemaId) {
        Supplier<String> loader = loaders.get(schemaId);
        if (loader == null) {
            throw ProducerException.noSuchSchema(schemaId);
        }
        return loader.get();
    }

    @Override
    public boolean has(String schemaId) {
        return loaders.containsKey(schemaId);
    }

    @Override
    public int size() {
        return loaders.size();
    }

    /** All registered ids (sorted), for diagnostics/tests. */
    public List<String> ids() {
        List<String> out = new ArrayList<>(loaders.keySet());
        out.sort(String::compareTo);
        return out;
    }

    // --- built-in function schemas (DESIGN §2.5) ----------------------------

    /** The {@code /ops/<fn>} names, in DESIGN §2.1 order. */
    public static final List<String> OPS_FUNCTIONS =
        List.of("take", "ack", "release", "replay", "recast", "purge", "raw", "validate", "rescan", "packs");

    public static String functionInputId(String fn) {
        return "schema:function:" + CATALOG + ".ops." + fn + ":input";
    }

    public static String functionOutputId(String fn) {
        return "schema:function:" + CATALOG + ".ops." + fn + ":output";
    }

    /**
     * One declared input property of an {@code /ops/<fn>} function. This table is the single
     * source for both the served {@code schema:function:x12.ops.<fn>:input} schema and the
     * check {@code X12Operations} applies before running the function, so what a caller reads
     * from {@code getSchema} is exactly what {@code invokeFunction} accepts.
     */
    public record Param(String name, String dataType, boolean required, boolean multi, String description) {
    }

    private static final String FILTER_DESC = "RFC4515 filter over envelope + schema property names (DESIGN §2.6)";
    private static final String ELEMENT_KEY_DESC = "<fileId>:<ISA13>:<GS06>:<ST02>";

    private static final Map<String, List<Param>> INPUTS = Map.of(
        "take", List.of(
            new Param("filter", "string", false, false, FILTER_DESC),
            new Param("max", "integer", false, false, "Batch size, at least 1; default 100, capped at 1000"),
            new Param("leaseTtl", "string", false, false, "ISO-8601 duration, positive; default PT5M")),
        "ack", leaseInput(),
        "release", leaseInput(),
        "replay", List.of(new Param("filter", "string", false, false, "RFC4515; omitted = every in_flight row")),
        "recast", List.of(
            new Param("filter", "string", false, false, "RFC4515"),
            new Param("max", "integer", false, false, "Rows examined per call, at least 1; default and cap 1000")),
        "purge", List.of(new Param("olderThan", "string", false, false,
            "ISO-8601 duration, not negative; omitted = every acked row")),
        "raw", List.of(new Param("elementKey", "string", true, false, ELEMENT_KEY_DESC)),
        "validate", List.of(new Param("elementKey", "string", true, false, ELEMENT_KEY_DESC)),
        "rescan", List.of(new Param("source", "string", false, false, "config.sources[].name; omitted = every source")),
        "packs", List.of(
            new Param("name", "string", false, false, "Report only this pack; omitted = all"),
            new Param("gs08", "string", false, false, "Report only the pack covering this guide")));

    private static List<Param> leaseInput() {
        return List.of(
            new Param("leaseId", "string", true, false, null),
            new Param("elementKeys", "string", false, true, "Subset of the lease, non-empty; omitted = whole lease"));
    }

    /** The declared input properties of {@code /ops/<fn>}; empty for an unknown function. */
    public static List<Param> functionInputs(String fn) {
        return INPUTS.getOrDefault(fn, List.of());
    }

    /** Register the in-code schemas; a codegen-emitted file with the same id wins (never overwritten). */
    private void addBuiltins() {
        for (Map.Entry<String, String> e : builtinSchemas().entrySet()) {
            loaders.putIfAbsent(e.getKey(), () -> e.getValue());
        }
    }

    /** Every in-code schema, id → JSON. Package-private for tests. */
    static Map<String, String> builtinSchemas() {
        Map<String, String> out = new LinkedHashMap<>();
        put(out, schema(OPS_ERROR_SCHEMA, List.of(
            prop("code", "string", true, "Declared error code (see the function's throws)"),
            prop("message", "string", false, null))));
        put(out, schema(OPS_VERDICT_SCHEMA, List.of(
            prop("valid", "boolean", true, null),
            multi(prop("errors", "string", true, "Validation errors; empty when valid")))));

        // take
        for (String fn : OPS_FUNCTIONS) {
            List<JsonObject> props = new ArrayList<>();
            for (Param p : functionInputs(fn)) {
                JsonObject jp = prop(p.name(), p.dataType(), p.required(), p.description());
                props.add(p.multi() ? multi(jp) : jp);
            }
            put(out, schema(functionInputId(fn), props));
        }
        put(out, schema(functionOutputId("take"), List.of(
            prop("leaseId", "string", true, "null when nothing was drainable"),
            ref(multi(prop("transactions", "string", true, "Leased transaction sets (envelope + typed body)")), ENVELOPE_SCHEMA),
            prop("remaining", "integer", true, "Approximate drainable backlog after this lease"))));
        // ack / release
        put(out, schema(functionOutputId("ack"), List.of(prop("acked", "integer", true, "Rows finalized"))));
        put(out, schema(functionOutputId("release"), List.of(prop("released", "integer", true, "Rows returned to new"))));
        // replay
        put(out, schema(functionOutputId("replay"), List.of(prop("replayed", "integer", true, null))));
        // recast
        put(out, schema(functionOutputId("recast"), List.of(
            prop("examined", "integer", true, null),
            prop("recast", "integer", true, null),
            prop("unchanged", "integer", true, null),
            prop("failed", "integer", true, null),
            prop("note", "string", false, "Present when no materializer is configured"))));
        // purge
        put(out, schema(functionOutputId("purge"), List.of(prop("purged", "integer", true, "Acked rows deleted"))));
        // raw
        put(out, schema(functionOutputId("raw"), List.of(
            prop("elementKey", "string", true, null),
            prop("fileId", "string", true, null),
            prop("gs08", "string", true, null),
            prop("transactionType", "string", true, null),
            prop("raw", "string", true, "ST..SE segments verbatim plus the ISA/GS context lines"))));
        // validate
        put(out, schema(functionOutputId("validate"), List.of(
            prop("elementKey", "string", true, null),
            prop("schemaId", "string", true, null),
            ref(prop("stored", "string", true, "Verdict on the stored typed JSON"), OPS_VERDICT_SCHEMA),
            ref(prop("rematerialized", "string", false, "Verdict on the re-materialized form; null without a materializer"), OPS_VERDICT_SCHEMA),
            prop("repsAgree", "boolean", false, "null without a materializer"),
            multi(prop("parserErrors", "string", true, "imsweb getErrors()")),
            prop("parserErrorCount", "integer", false, null))));
        // rescan
        put(out, schema(functionOutputId("rescan"), List.of(
            prop("scanned", "integer", true, null),
            prop("discovered", "integer", true, null),
            prop("consumed", "integer", true, null),
            prop("errored", "integer", true, null))));
        // packs (DESIGN §7): what content this deployment has, and where it came from
        put(out, schema(functionOutputId("packs"), List.of(
            prop("packCount", "integer", true, "Packs loaded"),
            prop("schemaCount", "integer", true, "Schemas declared across those packs"),
            prop("registrySize", "integer", true, "Schemas the registry can actually serve"),
            multi(prop("guides", "string", true, "GS08 ids covered, aliases included")),
            multi(prop("packs", "string", true, "name, namespace, source, version, gs08, aliasOf, "
                + "transactionType, idScope, schemaCount, structureIndex, core, status, missingSchemas")))));
        return out;
    }

    private static void put(Map<String, String> out, JsonObject schema) {
        out.put(schema.get("id").getAsString(), PRETTY.toJson(schema));
    }

    /** A schema in the codegen's shape: {@code {id, dataTypes[], properties[]}} with only the data types used. */
    /**
     * Register the business element schemas the mappings declare (DESIGN §8.5). A collection
     * may not advertise a {@code collectionSchema} the registry cannot serve, so every mapping
     * turns into a real schema here: its columns become properties, typed by the column's core
     * dataType, with {@code primaryKey} carried through and a code-list {@code references} where
     * the mapping names one. Generated rather than authored so the schema and the projection can
     * never disagree about what a Claim has.
     */
    public void addMappingSchemas(java.util.Collection<EntityMapping> mappings) {
        for (EntityMapping m : mappings) {
            if (m.schemaId() == null) {
                continue;
            }
            final List<JsonObject> props = new ArrayList<>();
            for (EntityMapping.Column c : m.columns()) {
                JsonObject p = prop(c.name(), c.dataType(), false, c.description());
                if (c.primaryKey()) {
                    p.addProperty("primaryKey", true);
                }
                if (c.enumSchemaId() != null) {
                    p = ref(p, c.enumSchemaId());
                }
                props.add(p);
            }
            // Provenance: every business row can be traced back to the interchange it came from.
            props.add(prop("elementKey", "string", true, "The transaction set this row was projected from"));
            props.add(prop("fileId", "string", true, "The interchange file that delivered it"));
            for (EntityMapping.Dimension d : m.dimensions()) {
                props.add(prop(d.name(), d.dataType(), false, d.description()));
            }
            final String json = PRETTY.toJson(schema(m.schemaId(), props));
            loaders.put(m.schemaId(), () -> json);
        }
    }

    private static JsonObject schema(String id, List<JsonObject> properties) {
        JsonObject s = new JsonObject();
        s.addProperty("id", id);
        JsonArray types = new JsonArray();
        List<String> seen = new ArrayList<>();
        for (JsonObject p : properties) {
            String dt = p.get("dataType").getAsString();
            if (!seen.contains(dt)) {
                seen.add(dt);
                types.add(dataType(dt));
            }
        }
        s.add("dataTypes", types);
        JsonArray props = new JsonArray();
        properties.forEach(props::add);
        s.add("properties", props);
        return s;
    }

    private static JsonObject prop(String name, String dataType, boolean required, String description) {
        JsonObject p = new JsonObject();
        p.addProperty("name", name);
        if (description != null) {
            p.addProperty("description", description);
        }
        p.addProperty("dataType", dataType);
        p.addProperty("required", required);
        return p;
    }

    private static JsonObject multi(JsonObject p) {
        p.addProperty("multi", true);
        return p;
    }

    private static JsonObject ref(JsonObject p, String schemaId) {
        JsonObject r = new JsonObject();
        r.addProperty("schemaId", schemaId);
        p.add("references", r);
        return p;
    }

    /** The codegen's core data-type descriptors (mirrors {@code CoreTypes}). */
    private static JsonObject dataType(String name) {
        JsonObject t = new JsonObject();
        t.addProperty("name", name);
        JsonArray examples = new JsonArray();
        switch (name) {
            case "boolean":
                t.addProperty("jsonType", "boolean");
                t.addProperty("description", "A boolean value");
                examples.add(true);
                examples.add(false);
                break;
            case "integer":
                t.addProperty("jsonType", "number");
                t.addProperty("description", "Integer numbers");
                examples.add(42);
                examples.add(-100);
                examples.add(999);
                break;
            case "date-time":
                t.addProperty("jsonType", "string");
                t.addProperty("description", "ISO 8601 timestamps");
                examples.add("2025-10-29T10:30:00.000Z");
                examples.add("2024-01-15T14:22:15Z");
                break;
            default:
                t.addProperty("jsonType", "string");
                t.addProperty("description", "Text values");
                examples.add("example text");
                examples.add("hello world");
                break;
        }
        t.add("examples", examples);
        t.addProperty("htmlInput", "date-time".equals(name) ? "datetime-local" : "integer".equals(name) ? "number" : "text");
        t.addProperty("isEnum", false);
        return t;
    }
}

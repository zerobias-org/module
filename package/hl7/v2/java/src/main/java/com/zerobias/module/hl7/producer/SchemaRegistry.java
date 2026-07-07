package com.zerobias.module.hl7.producer;

import com.google.gson.Gson;
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
 * Serves generated HL7 schemas (DESIGN §2.2) by their canonical id, for the
 * {@code getSchema} operation and to enumerate the {@code /by-type/<X>}
 * collections in the object tree.
 *
 * <p><b>Lazy by id → resource.</b> The general-purpose catalog is large (all
 * message families across every supported version → thousands of small files).
 * Rather than parse every file into memory at boot, the registry indexes only the
 * schema <em>ids</em> — reconstructed from each file's <em>path</em>
 * ({@code schemas/<version>/{messages,groups,segments,datatypes,tables}/<name>.json}
 * → {@code schema:{table|type|enum}:hl7v2.<version>.<name>}) with no content read —
 * and reads a schema's JSON on demand in {@link #getSchema}. Shared schemas
 * ({@code schemas/shared/*.json}, a handful) carry an id the path can't
 * reconstruct, so those are read once at index time to capture it.
 *
 * <p>{@code structure-index/*.json} (no schema id, different tree) is never scanned.
 */
public final class SchemaRegistry {

    private static final Gson GSON = new Gson();

    /** schemaId → a loader that yields the raw JSON (classpath resource, file, or in-memory extension). */
    private final Map<String, Supplier<String>> loaders = new LinkedHashMap<>();

    private SchemaRegistry() {
    }

    /**
     * Build a registry by scanning {@code schemaRoot} (a dir containing
     * {@code schemas/}). Ids are reconstructed from paths; content is read lazily.
     * Returns an empty registry if the dir is absent.
     */
    public static SchemaRegistry fromDirectory(Path schemaRoot) {
        SchemaRegistry r = new SchemaRegistry();
        Path schemas = schemaRoot.resolve("schemas");
        if (!Files.isDirectory(schemas)) {
            return r;
        }
        try (Stream<Path> walk = Files.walk(schemas)) {
            walk.filter(p -> p.toString().endsWith(".json"))
                .forEach(p -> r.indexFile(schemaRoot, p));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan schema tree at " + schemas, e);
        }
        return r;
    }

    /**
     * Build a registry from the {@code /schemas} resources on the classpath — how
     * the packaged module serves them. Handles both the shaded jar ({@code jar:}
     * URL) and an exploded {@code target/classes} ({@code file:} URL); either way a
     * schema is read from the classpath on demand. Empty if absent.
     */
    public static SchemaRegistry fromClasspath() {
        SchemaRegistry r = new SchemaRegistry();
        URL root = SchemaRegistry.class.getResource("/schemas");
        if (root == null) {
            return r;
        }
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
                            r.indexClasspath(e.getName());
                        }
                    }
                }
            } else if ("file".equals(root.getProtocol())) {
                Path classpathRoot = Path.of(root.toURI()).getParent();   // parent of /schemas
                try (Stream<Path> walk = Files.walk(Path.of(root.toURI()))) {
                    walk.filter(p -> p.toString().endsWith(".json"))
                        .forEach(p -> r.indexClasspath(classpathRoot.relativize(p).toString().replace('\\', '/')));
                }
            }
        } catch (IOException | URISyntaxException e) {
            throw new UncheckedIOException("Failed to scan classpath schema tree", new IOException(e));
        }
        return r;
    }

    // --- indexing (id from path; content deferred) -------------------------

    /** Index one classpath resource ({@code schemas/...json}) by its reconstructed/read id. */
    private void indexClasspath(String resource) {
        String[] parts = resource.split("/");
        String id = idFromPath(parts, () -> readIdFromStream(SchemaRegistry.class.getResourceAsStream("/" + resource)));
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
     * The schema id for a {@code schemas/...} path. Version-scoped files
     * ({@code schemas/<v>/<subdir>/<name>.json}) reconstruct without a read; shared
     * files ({@code schemas/shared/<name>.json}) fall back to {@code sharedId} which
     * reads the file's declared id. Returns null for anything else.
     */
    private static String idFromPath(String[] parts, Supplier<String> sharedId) {
        if (parts.length == 4 && "schemas".equals(parts[0])) {
            String version = parts[1];
            String name = stripJson(parts[3]);
            switch (parts[2]) {
                case "messages":  return "schema:table:hl7v2." + version + "." + name;
                case "segments":
                case "groups":
                case "datatypes": return "schema:type:hl7v2." + version + "." + name;
                case "tables":    return "schema:enum:hl7v2." + version + "." + name;
                default:          return null;
            }
        }
        if (parts.length == 3 && "schemas".equals(parts[0]) && "shared".equals(parts[1])) {
            return sharedId.get();   // e.g. schema:shared:hl7v2.message-envelope
        }
        return null;
    }

    private static String stripJson(String fileName) {
        return fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - ".json".length()) : fileName;
    }

    private static String readIdFromStream(InputStream in) {
        if (in == null) {
            return null;
        }
        try (InputStream s = in) {
            return readIdFromString(new String(s.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }

    private static String readIdFromString(String json) {
        JsonObject obj = GSON.fromJson(json, JsonObject.class);
        if (obj != null && obj.has("id")) {
            String id = obj.get("id").getAsString();
            if (id.startsWith("schema:")) {
                return id;
            }
        }
        return null;
    }

    private static String readClasspath(String resource) {
        InputStream in = SchemaRegistry.class.getResourceAsStream("/" + resource);
        if (in == null) {
            throw new UncheckedIOException(new IOException("schema resource vanished: " + resource));
        }
        try (InputStream s = in) {
            return new String(s.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read schema " + resource, e);
        }
    }

    private static String readFile(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read schema " + file, e);
        }
    }

    /**
     * Merge an extension pack's schemas (DESIGN §7) by walking {@code extDir} for
     * {@code *.json} files carrying a {@code schema:} id. Throws {@link IllegalStateException}
     * on a duplicate id (the no-dup-across-packs rule, §7.3 boot validation) — fail
     * fast rather than silently shadow base or peer content. Extension content is
     * small and read here, so it is kept in memory rather than re-read on demand.
     */
    public void mergeExtension(Path extDir) {
        if (!Files.isDirectory(extDir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(extDir)) {
            walk.filter(p -> p.toString().endsWith(".json")).forEach(file -> {
                String json = readFile(file);
                String id = readIdFromString(json);   // manifest.json / structure-index carry no schema id
                if (id == null) {
                    return;
                }
                if (loaders.containsKey(id)) {
                    throw new IllegalStateException("duplicate schema id across extensions: " + id);
                }
                loaders.put(id, () -> json);
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan extension dir " + extDir, e);
        }
    }

    /** Raw schema JSON for {@code schemaId}, or throw {@code noSuchObjectError}. */
    public String getSchema(String schemaId) {
        Supplier<String> loader = loaders.get(schemaId);
        if (loader == null) {
            throw ProducerException.noSuchSchema(schemaId);
        }
        return loader.get();
    }

    public boolean has(String schemaId) {
        return loaders.containsKey(schemaId);
    }

    public int size() {
        return loaders.size();
    }

    /**
     * Message-structure names (e.g. {@code ADT_A01}) for the given version slot,
     * derived from indexed {@code schema:table:hl7v2.<version>.<NAME>} ids — the
     * configured {@code /by-type/<X>} collections.
     */
    public List<String> messageStructures(String version) {
        String prefix = "schema:table:hl7v2." + version + ".";
        List<String> out = new ArrayList<>();
        for (String id : loaders.keySet()) {
            if (id.startsWith(prefix)) {
                out.add(id.substring(prefix.length()));
            }
        }
        out.sort(String::compareTo);
        return out;
    }

    /**
     * All message-structure names → their collection schema id, across every
     * namespace (base version slots + extension namespaces like {@code epic}). Drives
     * the namespace-agnostic {@code /by-type/<X>} listing once extensions are merged.
     */
    public Map<String, String> messageStructureIds() {
        Map<String, String> out = new LinkedHashMap<>();
        String pre = "schema:table:hl7v2.";
        for (String id : loaders.keySet()) {
            if (id.startsWith(pre)) {
                String tail = id.substring(pre.length());      // <namespace>.<name>
                int dot = tail.indexOf('.');
                if (dot > 0) {
                    out.put(tail.substring(dot + 1), id);       // name → full id (last wins)
                }
            }
        }
        return out;
    }
}

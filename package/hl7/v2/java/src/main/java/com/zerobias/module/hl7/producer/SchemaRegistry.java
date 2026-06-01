package com.zerobias.module.hl7.producer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Serves generated HL7 schemas (DESIGN §2.2) by their canonical id, for the
 * {@code getSchema} operation and to enumerate the {@code /by-type/<X>}
 * collections in the object tree.
 *
 * <p>The schema tree is produced at build time (Phase 1 codegen, a build
 * artifact) under {@code schemas/<version>/{messages,groups,segments,datatypes,
 * tables}/*.json} + {@code schemas/shared/message-envelope.json}. Rather than
 * decode ids back into file paths, the registry walks the tree once at startup,
 * parses each file's {@code id}, and indexes id → raw JSON. The
 * {@code structure-index/*.json} files (no schema {@code id}) are skipped.
 */
public final class SchemaRegistry {

    private static final Gson GSON = new Gson();

    /** schemaId → raw JSON text (served verbatim). */
    private final Map<String, String> byId = new LinkedHashMap<>();

    private SchemaRegistry() {
    }

    /**
     * Build a registry by scanning {@code schemaRoot} (the codegen output dir
     * containing {@code schemas/}). Returns an empty registry if the dir is absent.
     */
    public static SchemaRegistry fromDirectory(Path schemaRoot) {
        SchemaRegistry r = new SchemaRegistry();
        Path schemas = schemaRoot.resolve("schemas");
        if (!Files.isDirectory(schemas)) {
            return r;
        }
        try (Stream<Path> walk = Files.walk(schemas)) {
            walk.filter(p -> p.toString().endsWith(".json"))
                .forEach(r::index);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan schema tree at " + schemas, e);
        }
        return r;
    }

    private void index(Path file) {
        try {
            String json = Files.readString(file);
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj != null && obj.has("id")) {
                String id = obj.get("id").getAsString();
                if (id.startsWith("schema:")) {
                    byId.put(id, json);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read schema " + file, e);
        }
    }

    /**
     * Merge an extension pack's schemas (DESIGN §7) by walking {@code extDir} for
     * {@code *.json} files carrying a {@code schema:} id. Throws {@link IllegalStateException}
     * on a duplicate id (the no-dup-across-packs rule, §7.3 boot validation) —
     * fail fast rather than silently shadow base or peer content.
     */
    public void mergeExtension(Path extDir) {
        if (!Files.isDirectory(extDir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(extDir)) {
            walk.filter(p -> p.toString().endsWith(".json")).forEach(file -> {
                try {
                    String json = Files.readString(file);
                    JsonObject obj = GSON.fromJson(json, JsonObject.class);
                    if (obj == null || !obj.has("id")) {
                        return;   // manifest.json / structure-index.json carry no schema id
                    }
                    String id = obj.get("id").getAsString();
                    if (!id.startsWith("schema:")) {
                        return;
                    }
                    if (byId.containsKey(id)) {
                        throw new IllegalStateException("duplicate schema id across extensions: " + id);
                    }
                    byId.put(id, json);
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to read extension schema " + file, e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan extension dir " + extDir, e);
        }
    }

    /** Raw schema JSON for {@code schemaId}, or throw {@code noSuchObjectError}. */
    public String getSchema(String schemaId) {
        String json = byId.get(schemaId);
        if (json == null) {
            throw ProducerException.noSuchSchema(schemaId);
        }
        return json;
    }

    public boolean has(String schemaId) {
        return byId.containsKey(schemaId);
    }

    public int size() {
        return byId.size();
    }

    /**
     * Message-structure names (e.g. {@code ADT_A01}) for the given version slot,
     * derived from indexed {@code schema:table:hl7v2.<version>.<NAME>} ids — the
     * configured {@code /by-type/<X>} collections.
     */
    public List<String> messageStructures(String version) {
        String prefix = "schema:table:hl7v2." + version + ".";
        List<String> out = new ArrayList<>();
        for (String id : byId.keySet()) {
            if (id.startsWith(prefix)) {
                out.add(id.substring(prefix.length()));
            }
        }
        out.sort(String::compareTo);
        return out;
    }

    /**
     * All message-structure names → their collection schema id, across every
     * namespace (base {@code v251} + extension namespaces like {@code epic}). Drives
     * the namespace-agnostic {@code /by-type/<X>} listing once extensions are merged.
     * Insertion order: base then extensions.
     */
    public Map<String, String> messageStructureIds() {
        Map<String, String> out = new LinkedHashMap<>();
        String pre = "schema:table:hl7v2.";
        for (String id : byId.keySet()) {
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

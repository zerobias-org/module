package com.zerobias.module.hl7.materializer;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime view of the build-generated structure index (DESIGN §5) — the
 * data-driven map the {@link Materializer} walks to turn a generic-parsed message
 * into typed JSON. Mirrors the JSON the codegen emits to
 * {@code structure-index/<version>.json}; deserialized with Gson.
 *
 * <p>This is a deliberate twin of the codegen's {@code ...codegen.model.StructureIndex}:
 * the codegen module (which depends on the typed HAPI structure jars) produces the
 * JSON at build time; this runtime copy (no structure-jar dependency) consumes it.
 */
public final class StructureIndex {

    private static final Gson GSON = new Gson();

    public String version;
    public Map<String, MessageEntry> messages = new LinkedHashMap<>();
    public Map<String, MessageEntry> groups = new LinkedHashMap<>();
    public Map<String, SegmentEntry> segments = new LinkedHashMap<>();
    public Map<String, DatatypeEntry> datatypes = new LinkedHashMap<>();

    public static final class MessageEntry {
        public List<StructureRef> structures = List.of();
    }

    public static final class StructureRef {
        public String name;        // property name (lowercased structure code, e.g. "pid")
        public String structure;   // structure simple name (e.g. "PID")
        public boolean group;      // nested group vs segment
        public boolean required;
        public boolean multi;
    }

    public static final class SegmentEntry {
        public List<FieldEntry> fields = List.of();
    }

    public static final class FieldEntry {
        public int field;          // 1-based HL7 field number
        public String name;        // bean property name
        public String type;        // HL7 datatype simple name
        public boolean multi;
        public boolean required;
        public Integer table;      // bound HL7 table for ID/IS, else null
    }

    public static final class DatatypeEntry {
        public List<ComponentEntry> components = List.of();
    }

    public static final class ComponentEntry {
        public int component;      // 1-based component number
        public String name;
        public String type;
        public Integer table;
    }

    /**
     * Fold an extension pack's structure index into this one (DESIGN §7.3 step 5):
     * the extension's augmented messages (e.g. {@code ADT_A01_with_ZPV}) and its new
     * segments/datatypes (e.g. {@code ZPV}). Base entries win on collision — extensions
     * contribute NEW structures, they don't redefine base ones.
     */
    public void merge(StructureIndex other) {
        if (other == null) {
            return;
        }
        other.messages.forEach(messages::putIfAbsent);
        other.groups.forEach(groups::putIfAbsent);
        other.segments.forEach(segments::putIfAbsent);
        other.datatypes.forEach(datatypes::putIfAbsent);
    }

    public MessageEntry message(String structure) {
        return messages.get(structure);
    }

    public SegmentEntry segment(String code) {
        return segments.get(code);
    }

    public DatatypeEntry datatype(String type) {
        return datatypes.get(type);
    }

    // --- loaders -----------------------------------------------------------

    public static StructureIndex fromJson(String json) {
        StructureIndex idx = GSON.fromJson(json, StructureIndex.class);
        if (idx == null) {
            throw new IllegalArgumentException("empty structure index");
        }
        return idx;
    }

    public static StructureIndex fromStream(InputStream in) {
        try (InputStream s = in) {
            return fromJson(new String(s.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read structure index", e);
        }
    }

    public static StructureIndex fromFile(Path file) {
        try {
            return fromJson(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read structure index " + file, e);
        }
    }

    /** Load {@code /structure-index/<version>.json} from the classpath, or null if absent. */
    public static StructureIndex fromClasspath(String version) {
        InputStream in = StructureIndex.class.getResourceAsStream("/structure-index/" + version + ".json");
        return in == null ? null : fromStream(in);
    }
}

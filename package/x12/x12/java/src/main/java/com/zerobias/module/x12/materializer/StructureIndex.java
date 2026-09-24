package com.zerobias.module.x12.materializer;

import com.google.gson.Gson;
import com.zerobias.module.x12.parser.TransactionTypes;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runtime twin of the codegen's {@code codegen.model.StructureIndex} (DESIGN §5/§6): the
 * materializer driver for one implementation guide, loaded from the classpath resource
 * {@code structure-index/<GS08>.json} the codegen emits at {@code generate-resources}.
 * Plain data; field names are the JSON keys (Gson). This copy exists so the runtime never
 * depends on the build-time module; StructureIndexTest fails when the two shapes drift.
 *
 * <p>Loop entries are keyed by loop xid (envelope loops included; {@link #transactionLoop}
 * names the atom root), segment entries by segment xid, composite entries by composite
 * data element ({@code C003}). Property names ({@code loop2100}, {@code clp}, {@code clp02},
 * {@code c00301}) are the schema's — the materialized JSON uses exactly these keys.
 */
public final class StructureIndex {

    private static final Gson GSON = new Gson();
    public static final String RESOURCE_DIR = "structure-index/";

    public String gs08;
    public String transactionType;
    public String transactionXid;
    public String mapFile;
    public String transactionLoop;
    public String tableSchemaId;
    public Map<String, LoopEntry> loops = new LinkedHashMap<>();
    public Map<String, SegmentEntry> segments = new LinkedHashMap<>();
    public Map<String, CompositeEntry> composites = new LinkedHashMap<>();

    /** Loop xid → property name, from the loop references; built on first use (Gson skips transient fields). */
    private transient volatile Map<String, String> loopNames;

    public StructureIndex() {
    }

    /** Ordered child layout of a loop. */
    public static final class LoopEntry {
        public String xid;
        public String name;
        public String schemaId;
        public List<StructureRef> structures = new ArrayList<>();
    }

    /** One child of a loop: a segment or a nested loop. */
    public static final class StructureRef {
        public String name;
        public String xid;
        /** {@code "segment"} or {@code "loop"}. */
        public String kind;
        public boolean required;
        public boolean multi;
        public String pos;
        public String repeat;

        public boolean isLoop() {
            return "loop".equals(kind);
        }
    }

    /** Ordered positional field layout of a segment. */
    public static final class SegmentEntry {
        public String xid;
        public String name;
        public String schemaId;
        public List<FieldEntry> fields = new ArrayList<>();
    }

    /** Ordered positional sub-element layout of a composite data element. */
    public static final class CompositeEntry {
        public String dataEle;
        public String name;
        public String schemaId;
        public List<FieldEntry> fields = new ArrayList<>();
    }

    /** One element (or composite slot) of a segment / composite. */
    public static final class FieldEntry {
        public int seq;
        public String name;
        public String xid;
        public String dataEle;
        public String x12Type;
        public String coreType;
        public Integer impliedDecimals;
        public Integer minLen;
        public Integer maxLen;
        public boolean required;
        public boolean multi;
        public Integer repeat;
        public String composite;
        public String codes;
        public String usage;
    }

    public LoopEntry loop(String xid) {
        return loops.get(xid);
    }

    public SegmentEntry segment(String xid) {
        return segments.get(xid);
    }

    public CompositeEntry composite(String dataEle) {
        return composites.get(dataEle);
    }

    /** The atom root ({@code ST_LOOP}) entry. */
    public LoopEntry transactionEntry() {
        return loops.get(transactionLoop == null ? "ST_LOOP" : transactionLoop);
    }

    /**
     * The JSON property of a loop, as its parent's reference names it ({@code 2100} →
     * {@code loop2100}), so the runtime never re-derives the codegen's naming. imsweb parses with
     * the map this index was generated from, so every loop it builds is referenced; an xid the
     * index does not know comes back verbatim.
     */
    public String loopProperty(String xid) {
        Map<String, String> names = loopNames;
        if (names == null) {
            names = new HashMap<>();
            for (LoopEntry l : loops.values()) {
                for (StructureRef ref : l.structures) {
                    if (ref.isLoop()) {
                        names.putIfAbsent(ref.xid, ref.name);
                    }
                }
            }
            loopNames = names;
        }
        return names.getOrDefault(xid, xid);
    }

    /**
     * Load the index for a GS08 from the classpath; any accepted spelling resolves through
     * {@link TransactionTypes#canonical} (the codegen emits one index per canonical guide).
     * Empty when no resource exists — e.g. a build with {@code -Dcodegen.skip=true} or an
     * unsupported guide.
     */
    public static Optional<StructureIndex> fromClasspath(String gs08) {
        if (gs08 == null || gs08.isBlank()) {
            return Optional.empty();
        }
        return load(TransactionTypes.canonical(gs08).orElse(gs08.trim()));
    }

    private static Optional<StructureIndex> load(String id) {
        String resource = RESOURCE_DIR + id + ".json";
        ClassLoader cl = StructureIndex.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resource)) {
            if (in == null) {
                return Optional.empty();
            }
            StructureIndex idx = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), StructureIndex.class);
            return Optional.ofNullable(idx);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + resource, e);
        }
    }
}

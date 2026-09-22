package com.zerobias.module.x12.codegen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zerobias.module.x12.codegen.mapping.Mapping;
import com.zerobias.module.x12.codegen.mapping.MappingLoader;
import com.zerobias.module.x12.codegen.model.DataType;
import com.zerobias.module.x12.codegen.model.Property;
import com.zerobias.module.x12.codegen.model.Schema;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Build-time schema generator (DESIGN §6).
 *
 * <p>Reads the pyx12 maps from the {@code com.imsweb:x12-parser} jar and emits,
 * under {@code <outputDir>}:
 * <ul>
 *   <li>{@code schemas/<GS08>/transactions/<TS>.json} — {@code schema:table:x12.<GS08>.<TS>}</li>
 *   <li>{@code schemas/<GS08>/loops/<xid>.json}       — {@code schema:type:x12.<GS08>.<xid>}</li>
 *   <li>{@code schemas/<GS08>/segments/<xid>.json}    — {@code schema:type:x12.<GS08>.<xid>}</li>
 *   <li>{@code schemas/<GS08>/composites/<C0nn>.json} — {@code schema:type:x12.<GS08>.<C0nn>}</li>
 *   <li>{@code schemas/codes/<dataEle>.json}          — {@code schema:enum:x12.codes.<dataEle>}</li>
 *   <li>{@code schemas/ops/<Name>.json}               — {@code schema:enum:x12.ops.<Name>}</li>
 *   <li>{@code schemas/shared/*.json}                 — envelope, file, receiver-stats(-source)</li>
 *   <li>{@code structure-index/<GS08>.json}           — runtime materializer driver</li>
 * </ul>
 *
 * <p>Every schema id is validated against the interface's canonical pattern and
 * every {@code references.schemaId} must resolve to an emitted schema; either
 * failure aborts the build. Output is a git-ignored build artifact regenerated
 * on every build (the parent pom's {@code generate-resources} phase).
 *
 * <p>Usage: {@code SchemaGenerator <guides|ALL> <outputResourcesDir>} where
 * {@code guides} is a comma-separated list of GS08 ids (canonical or alias),
 * e.g. {@code 005010X221A1,005010X231A1}.
 */
public final class SchemaGenerator {

    private final Path outputDir;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Set<String> emittedIds = new LinkedHashSet<>();
    private final List<Schema> emittedSchemas = new ArrayList<>();

    public SchemaGenerator(Path outputDir) {
        this.outputDir = outputDir;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: SchemaGenerator <guides|ALL> <outputResourcesDir>"
                + "  (e.g. ALL ../src/main/resources, or 005010X221A1,005010X231A1 ../src/main/resources)");
            System.exit(2);
            return;
        }
        final List<GuideCatalog.Guide> guides = resolveGuides(args[0]);
        new SchemaGenerator(Path.of(args[1])).run(guides, "ALL".equalsIgnoreCase(args[0]) || "*".equals(args[0]));
    }

    /** {@code ALL} / {@code *} → the whole catalog; else each comma-separated GS08 (canonical or alias). */
    static List<GuideCatalog.Guide> resolveGuides(String arg) {
        if ("ALL".equalsIgnoreCase(arg) || "*".equals(arg)) {
            return GuideCatalog.all();
        }
        final Map<String, GuideCatalog.Guide> out = new LinkedHashMap<>();
        for (String token : arg.split(",")) {
            final String id = token.trim();
            if (id.isEmpty()) {
                continue;
            }
            final GuideCatalog.Guide g = GuideCatalog.find(id);
            if (g == null) {
                throw new IllegalArgumentException("unknown guide " + id + "; known: " + GuideCatalog.ids());
            }
            out.putIfAbsent(g.gs08(), g);
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("no guides requested");
        }
        return new ArrayList<>(out.values());
    }

    /** Generate the requested guides plus the codes/ops/shared content. */
    public void run(List<GuideCatalog.Guide> guides, boolean all) throws IOException {
        final MappingLoader loader = new MappingLoader();
        final Map<String, Mapping.DataElement> dataElements = loader.loadDataElements();
        final CodeRegistry codes = new CodeRegistry(loader.loadCodeSets());

        // Pass 1: walk everything (the code registry is global across guides).
        final List<StructureWalker> walkers = new ArrayList<>();
        for (GuideCatalog.Guide g : guides) {
            final Mapping.Transaction tx = loader.loadTransaction(g.mapFile());
            for (String label : GuideCatalog.labels(g)) {
                final String aliasOf = label.equals(g.gs08()) ? null : g.gs08();
                final StructureWalker w = new StructureWalker(label, g.transactionType(), aliasOf, g.mapFile(),
                    dataElements, codes);
                w.walk(tx);
                walkers.add(w);
            }
        }

        // Pass 2: emit guides.
        for (StructureWalker w : walkers) {
            final StructureWalker.Generated gen = w.emit();
            gen.table.properties.addAll(SharedSchemas.envelopeProperties());

            final Path guideRoot = outputDir.resolve("schemas").resolve(w.gs08());
            deleteTree(guideRoot);
            write(guideRoot.resolve("transactions").resolve(w.transactionType() + ".json"), gen.table);
            writeAll(guideRoot.resolve("loops"), gen.loops);
            writeAll(guideRoot.resolve("segments"), gen.segments);
            writeAll(guideRoot.resolve("composites"), gen.composites);

            final Path indexFile = outputDir.resolve("structure-index").resolve(w.gs08() + ".json");
            Files.createDirectories(indexFile.getParent());
            Files.writeString(indexFile, gson.toJson(gen.index) + "\n");

            System.out.printf("Generated %s (%s%s): 1 transaction, %d loop(s), %d segment(s), %d composite(s).%n",
                w.gs08(), w.transactionType(), gen.index.aliasOf == null ? "" : ", alias of " + gen.index.aliasOf,
                gen.loops.size(), gen.segments.size(), gen.composites.size());
        }

        // Code-set enums (union across the walked guides + codes.xml).
        final Path codesRoot = outputDir.resolve("schemas").resolve(SchemaIds.CODES);
        if (all) {
            deleteTree(codesRoot);
        }
        int enums = 0;
        for (String dataEle : codes.dataElements()) {
            write(codesRoot.resolve(dataEle + ".json"), codeEnum(dataEle, dataElements, codes));
            enums++;
        }
        System.out.printf("Generated %d code-set enum(s) under schemas/%s.%n", enums, SchemaIds.CODES);

        // Receiver-owned enums + shared schemas.
        final Path opsRoot = outputDir.resolve("schemas").resolve(SchemaIds.OPS);
        deleteTree(opsRoot);
        writeAll(opsRoot, SharedSchemas.opsEnums());
        final Path sharedRoot = outputDir.resolve("schemas").resolve("shared");
        deleteTree(sharedRoot);
        write(sharedRoot.resolve("transaction-envelope.json"), SharedSchemas.transactionEnvelope());
        write(sharedRoot.resolve("file.json"), SharedSchemas.file());
        write(sharedRoot.resolve("receiver-stats.json"), SharedSchemas.receiverStats());
        write(sharedRoot.resolve("receiver-stats-source.json"), SharedSchemas.receiverStatsSource());

        verifyReferences();
        System.out.printf("Wrote %d schema(s) to %s.%n", emittedSchemas.size(), outputDir);
    }

    /** {@code schema:enum:x12.codes.<dataEle>} with the merged value set. */
    static Schema codeEnum(String dataEle, Map<String, Mapping.DataElement> dataElements, CodeRegistry codes) {
        final Mapping.DataElement de = dataElements.get(dataEle);
        final StringBuilder desc = new StringBuilder();
        desc.append(de == null || de.name() == null || de.name().isBlank() ? "X12 data element " + dataEle
            : de.name() + " (X12 data element " + dataEle + ")");
        final Set<String> sources = new TreeSet<>();
        for (String g : codes.guidesOf(dataEle)) {
            sources.add("valid_codes in " + g);
        }
        for (String cs : codes.codesetsOf(dataEle)) {
            sources.add("codes.xml codeset " + cs);
        }
        if (!sources.isEmpty()) {
            desc.append("; value set from ").append(String.join(", ", sources));
        }
        return SharedSchemas.enumSchema(SchemaIds.codes(dataEle), "x12Code" + dataEle, desc.toString(), codes.codesOf(dataEle));
    }

    /**
     * Populate {@code schema.dataTypes} with definitions for every core type its
     * properties use, keeping any enum type the schema already declares.
     */
    public static void fillDataTypes(Schema schema) {
        final Map<String, DataType> declared = new LinkedHashMap<>();
        for (DataType dt : schema.dataTypes) {
            if (dt.isEnum) {
                declared.put(dt.name, dt);
            }
        }
        final Set<String> used = new LinkedHashSet<>();
        for (Property p : schema.properties) {
            used.add(p.dataType);
        }
        schema.dataTypes.clear();
        for (String type : used) {
            final DataType dt = declared.get(type);
            schema.dataTypes.add(dt != null ? dt : CoreTypes.definition(type));
        }
    }

    /** Every reference must point at an emitted schema — dangling composition is a build failure. */
    private void verifyReferences() {
        final List<String> dangling = new ArrayList<>();
        for (Schema s : emittedSchemas) {
            for (Property p : s.properties) {
                if (p.references != null) {
                    SchemaIds.requireValid(p.references.schemaId);
                    if (!emittedIds.contains(p.references.schemaId)) {
                        dangling.add(s.id + "." + p.name + " -> " + p.references.schemaId);
                    }
                }
            }
        }
        if (!dangling.isEmpty()) {
            throw new IllegalStateException("dangling schema references:\n  " + String.join("\n  ", dangling));
        }
    }

    private void writeAll(Path dir, Map<String, Schema> schemas) throws IOException {
        for (Map.Entry<String, Schema> e : schemas.entrySet()) {
            write(dir.resolve(e.getKey() + ".json"), e.getValue());
        }
    }

    private void write(Path file, Schema schema) throws IOException {
        SchemaIds.requireValid(schema.id);
        if (!emittedIds.add(schema.id)) {
            throw new IllegalStateException("schema id emitted twice: " + schema.id + " (" + file + ")");
        }
        fillDataTypes(schema);
        emittedSchemas.add(schema);
        Files.createDirectories(file.getParent());
        Files.writeString(file, gson.toJson(schema) + "\n");
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }
}

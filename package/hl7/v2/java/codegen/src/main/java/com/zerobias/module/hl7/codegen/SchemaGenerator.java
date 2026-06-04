package com.zerobias.module.hl7.codegen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zerobias.module.hl7.codegen.model.DataType;
import com.zerobias.module.hl7.codegen.model.Property;
import com.zerobias.module.hl7.codegen.model.Reference;
import com.zerobias.module.hl7.codegen.model.Schema;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Build-time schema generator (DESIGN §6).
 *
 * <p>Walks the HAPI typed structure classes for an HL7 version and emits, under
 * {@code <outputDir>}:
 * <ul>
 *   <li>{@code schemas/<version>/messages/*.json}   — {@code schema:table:hl7v2.<v>.<MSG>}</li>
 *   <li>{@code schemas/<version>/groups/*.json}     — {@code schema:type:hl7v2.<v>.<GROUP>}</li>
 *   <li>{@code schemas/<version>/segments/*.json}   — {@code schema:type:hl7v2.<v>.<SEG>}</li>
 *   <li>{@code schemas/<version>/datatypes/*.json}  — {@code schema:type:hl7v2.<v>.<DT>}</li>
 *   <li>{@code schemas/<version>/tables/*.json}     — {@code schema:enum:hl7v2.<v>.HL7nnnn}</li>
 *   <li>{@code schemas/shared/message-envelope.json}— {@code schema:shared:hl7v2.message-envelope}</li>
 *   <li>{@code structure-index/<version>.json}      — runtime materializer driver</li>
 * </ul>
 *
 * <p>Generated output is committed to git (reviewable diffs, no request-time
 * reflection); this tool runs only on demand (the listener pom's
 * {@code regen-schemas} profile). PLAN.md Phase 1.
 *
 * <p>Usage: {@code SchemaGenerator <version> <outputDir> [msg...]}
 * (e.g. {@code SchemaGenerator v27 ../src/main/resources ADT_A01 ORU_R01}).
 * With no message list, a small default set is generated; expand it to widen
 * coverage (the walk discovers all transitively-referenced structures).
 */
public final class SchemaGenerator {

    private static final String[] DEFAULT_MESSAGES = {"ADT_A01", "ORU_R01"};

    /** Shared envelope id (DESIGN §2.2). Status references the ops enum from Phase 7. */
    static final String ENVELOPE_ID = SchemaIds.shared("hl7v2.message-envelope");
    static final String STATUS_ENUM_ID = "schema:enum:hl7v2.ops.MessageStatus";

    private final String version;
    private final Path outputDir;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private SchemaGenerator(String version, Path outputDir) {
        this.version = version;
        this.outputDir = outputDir;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: SchemaGenerator <version> <outputDir> [msg...]"
                + "  (e.g. v27 ../src/main/resources ADT_A01 ORU_R01)");
            System.exit(2);
            return;
        }
        final String version = args[0];
        final Path outputDir = Path.of(args[1]);
        final String[] msgs = args.length > 2
            ? java.util.Arrays.copyOfRange(args, 2, args.length)
            : DEFAULT_MESSAGES;

        new SchemaGenerator(version, outputDir).run(msgs);
    }

    private void run(String[] messageSimpleNames) throws Exception {
        final StructureWalker walker = new StructureWalker(version);
        for (String msg : messageSimpleNames) {
            walker.walkMessage(msg);
        }

        // Message table schemas carry the HL7 structure + the buffer envelope (DESIGN §2.3).
        for (Schema s : walker.messages.values()) {
            s.properties.addAll(envelopeProperties());
        }

        // Every schema declares the core dataTypes its properties use.
        forEach(walker.messages, this::fillDataTypes);
        forEach(walker.groups, this::fillDataTypes);
        forEach(walker.segments, this::fillDataTypes);
        forEach(walker.datatypes, this::fillDataTypes);

        // Write schema files.
        final Path schemaRoot = outputDir.resolve("schemas").resolve(version);
        writeAll(schemaRoot.resolve("messages"), walker.messages);
        writeAll(schemaRoot.resolve("groups"), walker.groups);
        writeAll(schemaRoot.resolve("segments"), walker.segments);
        writeAll(schemaRoot.resolve("datatypes"), walker.datatypes);

        for (Integer table : walker.tables) {
            final Schema stub = buildEnumStub(table);
            write(schemaRoot.resolve("tables").resolve(String.format("HL7%04d.json", table)), stub);
        }

        // Shared envelope schema (used by the heterogeneous /messages view).
        write(outputDir.resolve("schemas").resolve("shared").resolve("message-envelope.json"),
            buildEnvelopeSchema());

        // Structure index for the runtime materializer.
        final Path indexFile = outputDir.resolve("structure-index").resolve(version + ".json");
        Files.createDirectories(indexFile.getParent());
        Files.writeString(indexFile, gson.toJson(walker.index) + "\n");

        System.out.printf(
            "Generated %s: %d message(s), %d group(s), %d segment(s), %d datatype(s), %d table(s).%n",
            version, walker.messages.size(), walker.groups.size(),
            walker.segments.size(), walker.datatypes.size(), walker.tables.size());
        System.out.println("Tables referenced (value-set population pending — DESIGN §6 follow-up): " + walker.tables);
    }

    /** controlId (pk), receivedAt, status (enum ref), leaseId — DESIGN §2.3 / §8. */
    private static List<Property> envelopeProperties() {
        final List<Property> props = new ArrayList<>();
        props.add(new Property("controlId", CoreTypes.STRING).required(true).primaryKey(true));
        props.add(new Property("receivedAt", CoreTypes.DATE_TIME).required(true));
        props.add(new Property("status", CoreTypes.STRING).required(true)
            .references(new Reference(STATUS_ENUM_ID)));
        props.add(new Property("leaseId", CoreTypes.STRING));
        return props;
    }

    private Schema buildEnvelopeSchema() {
        final Schema s = new Schema(SchemaIds.requireValid(ENVELOPE_ID));
        s.properties.addAll(envelopeProperties());
        fillDataTypes(s);
        return s;
    }

    private Schema buildEnumStub(int table) {
        final Schema s = new Schema(SchemaIds.requireValid(SchemaIds.enumTable(version, table)));
        final String name = String.format("HL7%04d", table);
        final DataType dt = new DataType(name, "string",
            "HL7 table " + table + " (value-set population pending — DESIGN §6 follow-up)",
            null, "text");
        dt.isEnum = true;
        s.dataTypes.add(dt);
        s.properties.add(new Property("code", CoreTypes.STRING).required(true).primaryKey(true));
        s.properties.add(new Property("display", CoreTypes.STRING));
        return s;
    }

    /** Populate {@code schema.dataTypes} with definitions for the core types its properties use. */
    private void fillDataTypes(Schema schema) {
        final Set<String> used = new LinkedHashSet<>();
        for (Property p : schema.properties) {
            used.add(p.dataType);
        }
        schema.dataTypes.clear();
        for (String coreType : used) {
            schema.dataTypes.add(CoreTypes.definition(coreType));
        }
    }

    private void writeAll(Path dir, Map<String, Schema> schemas) throws Exception {
        for (Map.Entry<String, Schema> e : schemas.entrySet()) {
            write(dir.resolve(e.getKey() + ".json"), e.getValue());
        }
    }

    private void write(Path file, Schema schema) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, gson.toJson(schema) + "\n");
    }

    private interface SchemaConsumer {
        void accept(Schema s);
    }

    private static void forEach(Map<String, Schema> schemas, SchemaConsumer fn) {
        for (Schema s : schemas.values()) {
            fn.accept(s);
        }
    }
}

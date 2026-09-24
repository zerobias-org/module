package com.zerobias.module.x12.codegen;

import com.zerobias.module.x12.codegen.model.DataType;
import com.zerobias.module.x12.codegen.model.EnumValue;
import com.zerobias.module.x12.codegen.model.Property;
import com.zerobias.module.x12.codegen.model.Reference;
import com.zerobias.module.x12.codegen.model.Schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The guide-independent schemas (DESIGN §2.1, §2.2, §5, §8, §9): the transaction
 * envelope overlay, the file (binary node) metadata, the receiver stats document
 * and the receiver's own enums under {@code schema:enum:x12.ops.*}. Also the
 * generic enum-schema builder used for the code sets.
 */
public final class SharedSchemas {

    public static final String ENVELOPE_ID = SchemaIds.shared("transaction-envelope");
    public static final String FILE_ID = SchemaIds.shared("file");
    public static final String STATS_ID = SchemaIds.shared("receiver-stats");
    public static final String STATS_SOURCE_ID = SchemaIds.shared("receiver-stats-source");

    public static final String TRANSACTION_STATUS = "TransactionStatus";
    public static final String ENVELOPE_ORIGIN = "EnvelopeOrigin";
    public static final String FILE_STATUS = "FileStatus";

    private SharedSchemas() {
    }

    /**
     * The envelope overlay every transaction row carries at top level (DESIGN §5),
     * appended to each {@code schema:table:} and the whole of the shared envelope
     * schema. {@code elementKey} is the primary key ({@code <fileId>:<ISA13>:<GS06>:<ST02>}).
     */
    public static List<Property> envelopeProperties() {
        final List<Property> p = new ArrayList<>();
        p.add(new Property("elementKey", CoreTypes.STRING).required(true).primaryKey(true)
            .description("Atom key: <fileId>:<ISA13>:<GS06>:<ST02>"));
        p.add(new Property("fileId", CoreTypes.STRING).required(true)
            .description("Interchange file id: <absolute path at discovery>@<first 12 hex of sha256>")
            .references(new Reference(FILE_ID, "fileId")));
        p.add(new Property("fileName", CoreTypes.STRING).description("Interchange file name"));
        p.add(new Property("sourceName", CoreTypes.STRING).required(true).description("Watched source (config.sources[].name)"));
        p.add(new Property("isaControlNumber", CoreTypes.STRING).description("ISA13 Interchange Control Number"));
        p.add(new Property("gsControlNumber", CoreTypes.STRING).description("GS06 Group Control Number"));
        p.add(new Property("stControlNumber", CoreTypes.STRING).description("ST02 Transaction Set Control Number"));
        p.add(new Property("gs08", CoreTypes.STRING).required(true).description("GS08 implementation guide id, e.g. 005010X221A1"));
        p.add(new Property("transactionType", CoreTypes.STRING).required(true).description("Display type: 835, 837P, 837I, 277CA, 999, ..."));
        p.add(new Property("senderId", CoreTypes.STRING).description("ISA06 Interchange Sender ID (GS02 per discriminator)"));
        p.add(new Property("receiverId", CoreTypes.STRING).description("ISA08 Interchange Receiver ID"));
        p.add(new Property("interchangeDate", CoreTypes.DATE_TIME).description("ISA09 + ISA10 interchange date/time"));
        p.add(new Property("receivedAt", CoreTypes.DATE_TIME).required(true).description("Time the transaction was buffered"));
        p.add(new Property("status", CoreTypes.STRING).required(true).description("Buffer status")
            .references(new Reference(SchemaIds.opsEnum(TRANSACTION_STATUS))));
        p.add(new Property("leaseId", CoreTypes.STRING).description("Active lease, when in_flight"));
        p.add(new Property("envelope", CoreTypes.STRING).required(true).description("file: parsed from ISA..IEA; synthetic: bare ST wrapped by the poller")
            .references(new Reference(SchemaIds.opsEnum(ENVELOPE_ORIGIN))));
        p.add(new Property("parserErrorCount", CoreTypes.INTEGER).description("imsweb parser errors recorded for this transaction"));
        return p;
    }

    public static Schema transactionEnvelope() {
        final Schema s = new Schema(SchemaIds.requireValid(ENVELOPE_ID));
        s.properties.addAll(envelopeProperties());
        return s;
    }

    /** {@code /files/<fileId>} binary-node metadata (DESIGN §2.1 binary fields + §8 files table). */
    public static Schema file() {
        final Schema s = new Schema(SchemaIds.requireValid(FILE_ID));
        s.properties.add(new Property("fileId", CoreTypes.STRING).required(true).primaryKey(true)
            .description("<absolute path inside the container at discovery time>@<first 12 hex of sha256>"));
        s.properties.add(new Property("filePath", CoreTypes.STRING).required(true)
            .description("Absolute path inside the container at discovery time (before the .done/.error rename)"));
        s.properties.add(new Property("fileName", CoreTypes.STRING).required(true));
        s.properties.add(new Property("sourceName", CoreTypes.STRING).required(true).description("Watched source (config.sources[].name)"));
        s.properties.add(new Property("currentPath", CoreTypes.STRING).description("Path after the .done/.error rename"));
        s.properties.add(new Property("size", CoreTypes.INTEGER).required(true).description("Bytes"));
        s.properties.add(new Property("mimeType", CoreTypes.MIME_TYPE).required(true).description("application/EDI-X12"));
        s.properties.add(new Property("checksum", CoreTypes.STRING).required(true).description("sha256 of the bytes, hex"));
        s.properties.add(new Property("modified", CoreTypes.DATE_TIME).description("File mtime"));
        s.properties.add(new Property("created", CoreTypes.DATE_TIME).description("Discovery time"));
        s.properties.add(new Property("consumedAt", CoreTypes.DATE_TIME));
        s.properties.add(new Property("status", CoreTypes.STRING).required(true)
            .references(new Reference(SchemaIds.opsEnum(FILE_STATUS))));
        s.properties.add(new Property("tags", CoreTypes.STRING).multi(true).description("source:<name>, status:<consumed|error|duplicate>"));
        s.properties.add(new Property("isaCount", CoreTypes.INTEGER).description("ISA_LOOPs in the file"));
        s.properties.add(new Property("transactionCount", CoreTypes.INTEGER).description("ST..SE atoms buffered from the file"));
        s.properties.add(new Property("errorMessage", CoreTypes.STRING));
        s.properties.add(new Property("renameFailed", CoreTypes.BOOLEAN).description("Committed but the .done rename failed"));
        s.properties.add(new Property("redeliveryCount", CoreTypes.INTEGER)
            .description("Times the same bytes re-landed at the same path after this row was recorded"));
        return s;
    }

    /** {@code /stats} document (DESIGN §2.1 "poller + buffer metrics", §9, §11.2). */
    public static Schema receiverStats() {
        final Schema s = new Schema(SchemaIds.requireValid(STATS_ID));
        s.properties.add(new Property("up", CoreTypes.BOOLEAN).required(true).description("Every poller thread alive"));
        s.properties.add(new Property("lastScan", CoreTypes.DATE_TIME));
        s.properties.add(new Property("lastConsumed", CoreTypes.DATE_TIME));
        s.properties.add(new Property("bufferDepth", CoreTypes.INTEGER).required(true)
            .description("Un-acked transactions (new + in_flight)"));
        s.properties.add(new Property("oldestUnackedSec", CoreTypes.INTEGER));
        s.properties.add(new Property("backpressure", CoreTypes.BOOLEAN).required(true));
        s.properties.add(new Property("newCount", CoreTypes.INTEGER).required(true).description("Transactions with status new"));
        s.properties.add(new Property("inFlightCount", CoreTypes.INTEGER).required(true).description("Transactions with status in_flight"));
        s.properties.add(new Property("ackedCount", CoreTypes.INTEGER).required(true).description("Transactions with status acked"));
        s.properties.add(new Property("fileCount", CoreTypes.INTEGER).required(true).description("Rows in the files table"));
        s.properties.add(new Property("doneFileCount", CoreTypes.INTEGER).required(true).description(".done files still in the inboxes"));
        s.properties.add(new Property("oldestDoneFileAgeSec", CoreTypes.INTEGER));
        s.properties.add(new Property("walBytes", CoreTypes.INTEGER).required(true).description("Size of the WAL file"));
        s.properties.add(new Property("dbSizeBytes", CoreTypes.INTEGER).required(true)
            .description("Size of the database file, free pages included"));
        s.properties.add(new Property("sources", CoreTypes.STRING).multi(true).required(true)
            .references(new Reference(STATS_SOURCE_ID)));
        return s;
    }

    public static Schema receiverStatsSource() {
        final Schema s = new Schema(SchemaIds.requireValid(STATS_SOURCE_ID));
        s.properties.add(new Property("name", CoreTypes.STRING).required(true));
        s.properties.add(new Property("path", CoreTypes.STRING).required(true));
        s.properties.add(new Property("writable", CoreTypes.BOOLEAN).required(true));
        s.properties.add(new Property("pending", CoreTypes.INTEGER).required(true).description("Candidate files not yet consumed"));
        s.properties.add(new Property("errored", CoreTypes.INTEGER).required(true).description(
            "The source's files rows with status error: files sent to .error and files recorded after repeated failed reads"));
        s.properties.add(new Property("lastScanCompleted", CoreTypes.DATE_TIME).description("When this source's last scan finished"));
        return s;
    }

    /** Receiver-owned enums: name → schema. */
    public static Map<String, Schema> opsEnums() {
        final Map<String, Schema> out = new LinkedHashMap<>();
        out.put(TRANSACTION_STATUS, enumSchema(SchemaIds.opsEnum(TRANSACTION_STATUS), "transactionStatus",
            "Buffer lifecycle of a transaction set (DESIGN §8)", ordered("new", "in_flight", "acked")));
        out.put(ENVELOPE_ORIGIN, enumSchema(SchemaIds.opsEnum(ENVELOPE_ORIGIN), "envelopeOrigin",
            "Whether the ISA/GS envelope came from the file or was synthesized for a bare ST (DESIGN §4.3)",
            ordered("file", "synthetic")));
        out.put(FILE_STATUS, enumSchema(SchemaIds.opsEnum(FILE_STATUS), "fileStatus",
            "Outcome of consuming an interchange file (DESIGN §8)", ordered("consumed", "error", "duplicate")));
        return out;
    }

    /**
     * An enum schema: one {@code isEnum} DataType carrying the value set, a
     * {@code code} primary-key property of that type and an optional
     * {@code description}.
     */
    public static Schema enumSchema(String id, String typeName, String description, Collection<String> values) {
        final Schema s = new Schema(SchemaIds.requireValid(id));
        final List<Object> examples = new ArrayList<>();
        final List<EnumValue> members = new ArrayList<>();
        for (String v : values) {
            if (examples.size() < 3) {
                examples.add(v);
            }
            members.add(new EnumValue(v, null));
        }
        final DataType dt = new DataType(typeName, "string", description, examples, "text");
        dt.isEnum = true;
        dt.values = members;
        s.dataTypes.add(dt);
        s.dataTypes.add(CoreTypes.definition(CoreTypes.STRING));
        s.properties.add(new Property("code", typeName).required(true).primaryKey(true).description("The code value"));
        s.properties.add(new Property("description", CoreTypes.STRING).description("Code meaning, when known"));
        return s;
    }

    private static Set<String> ordered(String... v) {
        return new LinkedHashSet<>(List.of(v));
    }
}

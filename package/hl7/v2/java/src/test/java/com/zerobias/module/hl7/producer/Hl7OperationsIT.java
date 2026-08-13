package com.zerobias.module.hl7.producer;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zerobias.module.hl7.buffer.BufferRow;
import com.zerobias.module.hl7.buffer.BufferStore;
import com.zerobias.module.hl7.buffer.MessageStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 7 validation (DESIGN §2.5): the drain functions
 * ({@code take}/{@code ack}/{@code release}/{@code replay}/{@code purge}) driven
 * through {@code FunctionsApi.invokeFunction} on {@link OperationRouter} — the
 * exact HTTP code path — over a real seeded SQLite buffer. Exercises the full
 * consume loop: take leases rows, ack finalizes them, release/replay return them
 * to drainable, purge evicts acked.
 */
class Hl7OperationsIT {

    private static final Gson GSON = new Gson();

    private BufferStore buffer;

    private Hl7ProducerFacade facade(Path dir) throws Exception {
        writeSchema(dir, "schemas/v27/messages/ADT_A01.json", "schema:table:hl7v2.v27.ADT_A01");
        writeSchema(dir, "schemas/v27/messages/ORU_R01.json", "schema:table:hl7v2.v27.ORU_R01");
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false);
        insert("M1", "ADT_A01", "ADT");
        insert("M2", "ADT_A01", "ADT");
        insert("M3", "ORU_R01", "ORU");
        SchemaRegistry schemas = SchemaRegistry.fromDirectory(dir);
        return new Hl7ProducerFacade(buffer, new ObjectTree(buffer, schemas, "v27"), schemas);
    }

    private static void writeSchema(Path dir, String rel, String id) throws Exception {
        Path f = dir.resolve(rel);
        Files.createDirectories(f.getParent());
        Files.writeString(f, "{\"id\":\"" + id + "\",\"dataTypes\":[],\"properties\":[]}\n");
    }

    /** Write a schema with an explicit properties list (JSON property objects, comma-separated). */
    private static void writeSchemaBody(Path dir, String rel, String id, String propsJson) throws Exception {
        Path f = dir.resolve(rel);
        Files.createDirectories(f.getParent());
        Files.writeString(f, "{\"id\":\"" + id + "\",\"dataTypes\":[],\"properties\":[" + propsJson + "]}\n");
    }

    private void insert(String cid, String struct, String code) throws Exception {
        buffer.insert(new BufferRow(0, Instant.parse("2026-05-28T10:00:00Z"), cid, struct, code, "A01",
            "EPIC", "HOSP", "2.7", "schema:table:hl7v2.v27." + struct, ("raw-" + cid).getBytes(),
            "{\"msh\":{\"messageType\":{\"messageCode\":\"" + code + "\"}}}",
            MessageStatus.NEW, null, null, null));
    }

    /** invoke a function op via the router; requestBody carries the input map. */
    private JsonObject invoke(Hl7ProducerFacade f, String fn, Map<String, Object> input) throws Exception {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("objectId", "/hl7-v2-receiver/ops/" + fn);
        args.put("requestBody", input);
        String out = OperationRouter.executeOperation(f, "FunctionsApi.invokeFunction", args);
        return GSON.fromJson(out, JsonObject.class);
    }

    @Test
    void takeLeasesThenAck(@TempDir Path dir) throws Exception {
        Hl7ProducerFacade f = facade(dir);

        JsonObject lease = invoke(f, "take", Map.of("max", 2));
        assertNotNull(lease.get("leaseId").getAsString());
        JsonArray msgs = lease.getAsJsonArray("messages");
        assertEquals(2, msgs.size());
        assertEquals(1, lease.get("remaining").getAsLong(), "one row still drainable");
        // materialized element carries the envelope
        assertEquals("M1", msgs.get(0).getAsJsonObject().get("controlId").getAsString());
        assertEquals("in_flight", msgs.get(0).getAsJsonObject().get("status").getAsString());

        assertEquals(2, buffer.count(MessageStatus.IN_FLIGHT));
        JsonObject acked = invoke(f, "ack", Map.of("leaseId", lease.get("leaseId").getAsString()));
        assertEquals(2, acked.get("acked").getAsInt());
        assertEquals(2, buffer.count(MessageStatus.ACKED));
        assertEquals(0, buffer.count(MessageStatus.IN_FLIGHT));
    }

    @Test
    void takeWithFilterScopesCandidates(@TempDir Path dir) throws Exception {
        Hl7ProducerFacade f = facade(dir);
        // only ADT rows are drainable under this filter
        JsonObject lease = invoke(f, "take",
            Map.of("filter", "(msh.messageType.messageCode=ADT)", "max", 10));
        JsonArray msgs = lease.getAsJsonArray("messages");
        assertEquals(2, msgs.size());
        for (var m : msgs) {
            assertEquals("ADT", m.getAsJsonObject().getAsJsonObject("msh")
                .getAsJsonObject("messageType").get("messageCode").getAsString());
        }
    }

    @Test
    void partialAckLeavesRestInFlight(@TempDir Path dir) throws Exception {
        Hl7ProducerFacade f = facade(dir);
        JsonObject lease = invoke(f, "take", Map.of("max", 2));
        String leaseId = lease.get("leaseId").getAsString();

        JsonObject acked = invoke(f, "ack", Map.of("leaseId", leaseId, "controlIds", List.of("M1")));
        assertEquals(1, acked.get("acked").getAsInt());
        assertEquals(1, buffer.count(MessageStatus.ACKED));
        assertEquals(1, buffer.count(MessageStatus.IN_FLIGHT), "M2 still in flight");
    }

    @Test
    void releaseReturnsRowsToDrainable(@TempDir Path dir) throws Exception {
        Hl7ProducerFacade f = facade(dir);
        JsonObject lease = invoke(f, "take", Map.of("max", 3));
        String leaseId = lease.get("leaseId").getAsString();
        assertEquals(3, buffer.count(MessageStatus.IN_FLIGHT));

        JsonObject released = invoke(f, "release", Map.of("leaseId", leaseId));
        assertEquals(3, released.get("released").getAsInt());
        assertEquals(0, buffer.count(MessageStatus.IN_FLIGHT));

        // takeable again
        JsonObject again = invoke(f, "take", Map.of("max", 3));
        assertEquals(3, again.getAsJsonArray("messages").size());
    }

    @Test
    void replayForcesInFlightBackToNew(@TempDir Path dir) throws Exception {
        Hl7ProducerFacade f = facade(dir);
        invoke(f, "take", Map.of("max", 3));          // lease all 3 (don't keep the id)
        assertEquals(3, buffer.count(MessageStatus.IN_FLIGHT));

        JsonObject replayed = invoke(f, "replay", Map.of());
        assertEquals(3, replayed.get("replayed").getAsInt());
        assertEquals(3, buffer.count(MessageStatus.NEW));
        assertEquals(0, buffer.count(MessageStatus.IN_FLIGHT));
    }

    @Test
    void purgeDeletesAcked(@TempDir Path dir) throws Exception {
        Hl7ProducerFacade f = facade(dir);
        String leaseId = invoke(f, "take", Map.of("max", 3)).get("leaseId").getAsString();
        invoke(f, "ack", Map.of("leaseId", leaseId));
        assertEquals(3, buffer.count(MessageStatus.ACKED));

        JsonObject purged = invoke(f, "purge", Map.of());   // default: all acked
        assertEquals(3, purged.get("purged").getAsInt());
        assertEquals(0, buffer.count());
    }

    @Test
    void emptyTakeReturnsNullLease(@TempDir Path dir) throws Exception {
        Hl7ProducerFacade f = facade(dir);
        invoke(f, "ack", Map.of("leaseId", invoke(f, "take", Map.of("max", 99)).get("leaseId").getAsString()));
        // nothing drainable now
        JsonObject empty = invoke(f, "take", Map.of("max", 10));
        assertTrue(empty.get("leaseId").isJsonNull());
        assertEquals(0, empty.getAsJsonArray("messages").size());
        assertEquals(0, empty.get("remaining").getAsLong());
    }

    @Test
    void errorMapping(@TempDir Path dir) throws Exception {
        Hl7ProducerFacade f = facade(dir);

        // ack without leaseId -> illegalArgument
        ProducerException e1 = assertThrows(ProducerException.class,
            () -> invoke(f, "ack", Map.of()));
        assertEquals(400, e1.httpStatus());
        assertEquals("err.illegal.argument", e1.key());

        // invoking a non-function object -> unsupported
        ProducerException e2 = assertThrows(ProducerException.class, () -> {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("objectId", "/hl7-v2-receiver/messages");
            args.put("requestBody", Map.of());
            OperationRouter.executeOperation(f, "FunctionsApi.invokeFunction", args);
        });
        assertEquals(400, e2.httpStatus());
        assertEquals("err.unsupported.operation", e2.key());

        // malformed take filter -> illegalArgument
        ProducerException e3 = assertThrows(ProducerException.class,
            () -> invoke(f, "take", Map.of("filter", "not-a-filter")));
        assertEquals("err.illegal.argument", e3.key());
    }

    // --- er7 / validate (DESIGN §2.5; addressed by message id) -------------

    @Test
    void er7ReturnsCanonicalWireText(@TempDir Path dir) throws Exception {
        Hl7ProducerFacade f = facade(dir);
        JsonObject out = invoke(f, "er7", Map.of("elementKey", "M1"));
        assertEquals("M1", out.get("controlId").getAsString());
        assertEquals("ADT_A01", out.get("messageStructure").getAsString());
        // insert() stored ("raw-" + cid) as the raw ER7 bytes; returned verbatim as UTF-8
        assertEquals("raw-M1", out.get("er7").getAsString());
    }

    @Test
    void er7UnknownMessageIdIs404(@TempDir Path dir) throws Exception {
        Hl7ProducerFacade f = facade(dir);
        ProducerException e = assertThrows(ProducerException.class,
            () -> invoke(f, "er7", Map.of("elementKey", "NOPE")));
        assertEquals(404, e.httpStatus());
        assertEquals("err.no.such.object", e.key());
    }

    /** A facade whose ADT_A01 schema exactly declares what {@code toElement} emits for a seeded row. */
    private Hl7ProducerFacade validateFacade(Path dir, String adtProps) throws Exception {
        writeSchemaBody(dir, "schemas/v27/messages/ADT_A01.json", "schema:table:hl7v2.v27.ADT_A01", adtProps);
        writeSchema(dir, "schemas/v27/messages/ORU_R01.json", "schema:table:hl7v2.v27.ORU_R01");
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false);
        insert("M1", "ADT_A01", "ADT");   // mapped_json {"msh":{messageType:{messageCode:"ADT"}}}
        SchemaRegistry schemas = SchemaRegistry.fromDirectory(dir);
        return new Hl7ProducerFacade(buffer, new ObjectTree(buffer, schemas, "v27"), schemas);
    }

    @Test
    void validatePassesWhenElementConforms(@TempDir Path dir) throws Exception {
        // declare exactly the keys toElement produces (msh + envelope); all present → valid
        Hl7ProducerFacade f = validateFacade(dir,
            "{\"name\":\"msh\"},{\"name\":\"controlId\",\"required\":true},"
          + "{\"name\":\"receivedAt\",\"required\":true},{\"name\":\"status\",\"required\":true},"
          + "{\"name\":\"sourcePort\"},{\"name\":\"leaseId\"}");
        JsonObject out = invoke(f, "validate", Map.of("elementKey", "M1"));
        assertEquals("M1", out.get("controlId").getAsString());
        JsonObject stored = out.getAsJsonObject("stored");
        assertTrue(stored.get("valid").getAsBoolean(), "expected conformant: " + stored.get("errors"));
    }

    @Test
    void validateFlagsMissingRequiredAndUndeclared(@TempDir Path dir) throws Exception {
        // require a "pid" the (msh-only) element lacks, and don't declare "msh"
        Hl7ProducerFacade f = validateFacade(dir,
            "{\"name\":\"pid\",\"required\":true},{\"name\":\"controlId\",\"required\":true},"
          + "{\"name\":\"receivedAt\",\"required\":true},{\"name\":\"status\",\"required\":true},"
          + "{\"name\":\"sourcePort\"},{\"name\":\"leaseId\"}");
        JsonObject stored = invoke(f, "validate", Map.of("elementKey", "M1")).getAsJsonObject("stored");
        assertFalse(stored.get("valid").getAsBoolean());
        String errs = stored.getAsJsonArray("errors").toString();
        assertTrue(errs.contains("pid") && errs.contains("missing required"), errs);
        assertTrue(errs.contains("msh") && errs.contains("undeclared"), errs);
    }
}

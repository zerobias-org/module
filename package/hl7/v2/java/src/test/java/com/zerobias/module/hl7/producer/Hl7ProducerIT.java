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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 6 validation (DESIGN §2): the DataProducer read surface over a real
 * seeded SQLite buffer + a generated schema tree. Drives operations through
 * {@link OperationRouter#executeOperation} — the exact code path the HTTP route
 * (`POST /connections/{id}/{method}`) invokes — so routing, the object tree,
 * collection scoping + Phase-5 filtering, schema serving, and error mapping are
 * all covered without the Javalin/Jetty stack.
 */
class Hl7ProducerIT {

    private static final Gson GSON = new Gson();

    private Hl7ProducerFacade facade(Path dir) throws Exception {
        // --- a minimal generated schema tree (Phase 1 emits the full one) ---
        writeSchema(dir, "schemas/shared/message-envelope.json", "schema:shared:hl7v2.message-envelope");
        writeSchema(dir, "schemas/v27/messages/ADT_A01.json", "schema:table:hl7v2.v27.ADT_A01");
        writeSchema(dir, "schemas/v27/messages/ORU_R01.json", "schema:table:hl7v2.v27.ORU_R01");
        writeSchema(dir, "schemas/v27/segments/PID.json", "schema:type:hl7v2.v27.PID");

        BufferStore buffer = new BufferStore(dir.resolve("buffer.db").toString(), false);
        insert(buffer, "M1", "ADT_A01", "ADT", "EPIC", "new", "SMITH");
        insert(buffer, "M2", "ADT_A01", "ADT", "EPIC", "acked", "ASHWORTH");
        insert(buffer, "M3", "ORU_R01", "ORU", "CERNER", "new", "BOOTH");

        SchemaRegistry schemas = SchemaRegistry.fromDirectory(dir);
        ObjectTree tree = new ObjectTree(buffer, schemas, "v27");
        return new Hl7ProducerFacade(buffer, tree, schemas);
    }

    private static void writeSchema(Path dir, String rel, String id) throws Exception {
        Path f = dir.resolve(rel);
        Files.createDirectories(f.getParent());
        Files.writeString(f, "{\"id\":\"" + id + "\",\"dataTypes\":[],\"properties\":[]}\n");
    }

    private static void insert(BufferStore b, String cid, String struct, String code,
            String app, String status, String family) throws Exception {
        String json = "{\"pid\":{\"patientFamilyName\":\"" + family + "\"}}";
        b.insert(new BufferRow(0, Instant.parse("2026-05-28T10:00:00Z"), cid, struct, code, "A01",
            app, "HOSP", "2.7", "schema:table:hl7v2.v27." + struct, ("raw-" + cid).getBytes(),
            json, MessageStatus.fromWire(status), null, null, null));
    }

    private static String op(Hl7ProducerFacade f, String method, Object... kv) throws Exception {
        Map<String, Object> args = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            args.put((String) kv[i], kv[i + 1]);
        }
        return OperationRouter.executeOperation(f, method, args);
    }

    /**
     * Unwrap a paginated op's response, asserting the {@code PagedResults}
     * envelope the platform requires ({@code items}/{@code count}/{@code pageSize}/
     * {@code pageNumber}) — a bare array trips "Producers must return 'items' for
     * PagedResults queries" and breaks the data-explorer tree.
     */
    private static JsonArray pagedItems(String json) {
        JsonObject env = GSON.fromJson(json, JsonObject.class);
        assertTrue(env.has("items"), "PagedResults must have 'items': " + json);
        assertTrue(env.has("count"), "PagedResults must have 'count': " + json);
        assertTrue(env.has("pageSize"), "PagedResults must have 'pageSize': " + json);
        assertTrue(env.has("pageNumber"), "PagedResults must have 'pageNumber': " + json);
        return env.getAsJsonArray("items");
    }

    @Test
    void rootAndContainers(@TempDir Path dir) throws Exception {
        Hl7ProducerFacade f = facade(dir);

        JsonObject root = GSON.fromJson(op(f, "ObjectsApi.getRootObject"), JsonObject.class);
        assertEquals("/", root.get("id").getAsString());
        assertTrue(classes(root).contains("container"));

        JsonObject receiver = GSON.fromJson(
            op(f, "ObjectsApi.getObject", "objectId", "/hl7-v2-receiver"), JsonObject.class);
        assertTrue(classes(receiver).contains("container"));

        // root has exactly one child: the receiver
        JsonArray rootChildren = pagedItems(
            op(f, "ObjectsApi.getChildren", "objectId", "/"));
        assertEquals(1, rootChildren.size());
        assertEquals("/hl7-v2-receiver", rootChildren.get(0).getAsJsonObject().get("id").getAsString());

        // receiver has exactly: messages, by-type, by-sender, stats, ops
        JsonArray kids = pagedItems(
            op(f, "ObjectsApi.getChildren", "objectId", "/hl7-v2-receiver"));
        java.util.Set<String> kidIds = kids.asList().stream()
            .map(e -> e.getAsJsonObject().get("id").getAsString())
            .collect(java.util.stream.Collectors.toSet());
        assertEquals(java.util.Set.of(
            "/hl7-v2-receiver/messages",
            "/hl7-v2-receiver/by-type",
            "/hl7-v2-receiver/by-sender",
            "/hl7-v2-receiver/stats",
            "/hl7-v2-receiver/ops"), kidIds);
    }

    @Test
    void pagedResultsEnvelopeAndPaging(@TempDir Path dir) throws Exception {
        Hl7ProducerFacade f = facade(dir);

        // getChildren: the envelope's scalar fields are faithful (count = total
        // children, pageNumber echoes the 1-based request) — a bare array here was
        // the data-explorer "must return 'items'" blocker.
        JsonObject env = GSON.fromJson(
            op(f, "ObjectsApi.getChildren", "objectId", "/hl7-v2-receiver",
                "pageSize", 2, "pageNumber", 1), JsonObject.class);
        assertEquals(5, env.get("count").getAsInt());           // 5 children total
        assertEquals(2, env.get("pageSize").getAsInt());
        assertEquals(1, env.get("pageNumber").getAsInt());      // 1-based, echoed
        assertEquals(2, env.getAsJsonArray("items").size());    // first page: 2 of 5

        // page 3 (1-based) of size 2 -> the trailing 1 child, count unchanged
        JsonObject p3 = GSON.fromJson(
            op(f, "ObjectsApi.getChildren", "objectId", "/hl7-v2-receiver",
                "pageSize", 2, "pageNumber", 3), JsonObject.class);
        assertEquals(5, p3.get("count").getAsInt());
        assertEquals(1, p3.getAsJsonArray("items").size());

        // collection elements are paged the same way — total count, not page size
        JsonObject coll = GSON.fromJson(
            op(f, "CollectionsApi.searchCollectionElements",
                "objectId", "/hl7-v2-receiver/messages", "pageSize", 2, "pageNumber", 1),
            JsonObject.class);
        assertEquals(3, coll.get("count").getAsInt());          // 3 seeded messages
        assertEquals(2, coll.getAsJsonArray("items").size());   // page caps at 2
    }

    @Test
    void messagesCollectionAndSize(@TempDir Path dir) throws Exception {
        Hl7ProducerFacade f = facade(dir);
        JsonObject messages = GSON.fromJson(
            op(f, "ObjectsApi.getObject", "objectId", "/hl7-v2-receiver/messages"), JsonObject.class);
        assertTrue(classes(messages).contains("collection"));
        assertEquals("schema:shared:hl7v2.message-envelope", messages.get("collectionSchema").getAsString());
        assertEquals(3, messages.get("collectionSize").getAsLong());
    }

    @Test
    void byTypeChildrenFromSchemaRegistry(@TempDir Path dir) throws Exception {
        Hl7ProducerFacade f = facade(dir);
        JsonArray types = pagedItems(
            op(f, "ObjectsApi.getChildren", "objectId", "/hl7-v2-receiver/by-type"));
        List<String> ids = types.asList().stream()
            .map(e -> e.getAsJsonObject().get("id").getAsString()).toList();
        assertEquals(List.of("/hl7-v2-receiver/by-type/ADT_A01", "/hl7-v2-receiver/by-type/ORU_R01"), ids);
    }

    @Test
    void bySenderChildrenFromBuffer(@TempDir Path dir) throws Exception {
        Hl7ProducerFacade f = facade(dir);
        JsonArray senders = pagedItems(
            op(f, "ObjectsApi.getChildren", "objectId", "/hl7-v2-receiver/by-sender"));
        List<String> ids = senders.asList().stream()
            .map(e -> e.getAsJsonObject().get("id").getAsString()).toList();
        assertEquals(List.of("/hl7-v2-receiver/by-sender/CERNER", "/hl7-v2-receiver/by-sender/EPIC"), ids);
    }

    @Test
    void searchCollectionElementsScopeAndFilter(@TempDir Path dir) throws Exception {
        Hl7ProducerFacade f = facade(dir);

        // all messages
        JsonArray all = pagedItems(
            op(f, "CollectionsApi.searchCollectionElements", "objectId", "/hl7-v2-receiver/messages"));
        assertEquals(3, all.size());

        // scoped to ADT_A01 -> M1, M2
        JsonArray adt = pagedItems(
            op(f, "CollectionsApi.searchCollectionElements",
                "objectId", "/hl7-v2-receiver/by-type/ADT_A01"));
        assertEquals(2, adt.size());

        // scope + RFC4515 filter (status column) -> only the un-acked ADT
        JsonArray adtNew = pagedItems(
            op(f, "CollectionsApi.searchCollectionElements",
                "objectId", "/hl7-v2-receiver/by-type/ADT_A01", "filter", "(status=new)"));
        assertEquals(1, adtNew.size());
        assertEquals("M1", adtNew.get(0).getAsJsonObject().get("controlId").getAsString());

        // json-path filter into the typed body
        JsonArray booth = pagedItems(
            op(f, "CollectionsApi.searchCollectionElements",
                "objectId", "/hl7-v2-receiver/messages", "filter", "(pid.patientFamilyName=BOOTH)"));
        assertEquals(1, booth.size());
        assertEquals("M3", booth.get(0).getAsJsonObject().get("controlId").getAsString());
    }

    @Test
    void getCollectionElementByControlId(@TempDir Path dir) throws Exception {
        Hl7ProducerFacade f = facade(dir);
        JsonObject el = GSON.fromJson(
            op(f, "CollectionsApi.getCollectionElement",
                "objectId", "/hl7-v2-receiver/messages", "elementKey", "M2"), JsonObject.class);
        assertEquals("M2", el.get("controlId").getAsString());
        assertEquals("acked", el.get("status").getAsString());
        assertEquals("ASHWORTH", el.getAsJsonObject("pid").get("patientFamilyName").getAsString());
    }

    @Test
    void getSchemaServesById(@TempDir Path dir) throws Exception {
        Hl7ProducerFacade f = facade(dir);
        String env = op(f, "SchemasApi.getSchema", "objectId", "schema:shared:hl7v2.message-envelope");
        assertTrue(env.contains("\"schema:shared:hl7v2.message-envelope\""));
    }

    @Test
    void errorMapping(@TempDir Path dir) throws Exception {
        Hl7ProducerFacade f = facade(dir);

        // unknown object -> 404, noSuchObjectError body {key,template,timestamp,statusCode,type,id}
        ProducerException e1 = assertThrows(ProducerException.class,
            () -> op(f, "ObjectsApi.getObject", "objectId", "/hl7-v2-receiver/by-type/NOPE"));
        assertEquals(404, e1.httpStatus());
        assertEquals("err.no.such.object", e1.key());
        assertNoSuchObjectBody(e1.toBody(), "object", "/hl7-v2-receiver/by-type/NOPE");

        // unknown schema -> 404, type=schema
        ProducerException e2 = assertThrows(ProducerException.class,
            () -> op(f, "SchemasApi.getSchema", "objectId", "schema:type:hl7v2.v27.NOPE"));
        assertEquals(404, e2.httpStatus());
        assertNoSuchObjectBody(e2.toBody(), "schema", "schema:type:hl7v2.v27.NOPE");

        // unknown element key -> 404
        ProducerException e3 = assertThrows(ProducerException.class,
            () -> op(f, "CollectionsApi.getCollectionElement",
                "objectId", "/hl7-v2-receiver/messages", "elementKey", "ZZZ"));
        assertEquals("err.no.such.object", e3.key());

        // write surface rejected -> 400, illegalArgumentError body {…, msg} (UnsupportedOperationError response)
        ProducerException e4 = assertThrows(ProducerException.class,
            () -> op(f, "CollectionsApi.addCollectionElement",
                "objectId", "/hl7-v2-receiver/messages", "element", Map.of("x", 1)));
        assertEquals(400, e4.httpStatus());
        assertEquals("err.unsupported.operation", e4.key());
        assertIllegalArgumentBody(e4.toBody());

        // malformed filter -> 400, illegalArgumentError
        ProducerException e5 = assertThrows(ProducerException.class,
            () -> op(f, "CollectionsApi.searchCollectionElements",
                "objectId", "/hl7-v2-receiver/messages", "filter", "not-a-filter"));
        assertEquals(400, e5.httpStatus());
        assertEquals("err.illegal.argument", e5.key());
        assertIllegalArgumentBody(e5.toBody());
    }

    /** errorModelBase required fields are present, non-null, and well-typed. */
    private static void assertErrorModelBase(Map<String, Object> body, int status) {
        assertTrue(body.get("key") instanceof String && !((String) body.get("key")).isBlank(), "key");
        assertTrue(body.get("template") instanceof String && !((String) body.get("template")).isBlank(), "template");
        assertTrue(body.get("timestamp") instanceof String && !((String) body.get("timestamp")).isBlank(), "timestamp");
        assertEquals(status, ((Number) body.get("statusCode")).intValue(), "statusCode");
    }

    private static void assertNoSuchObjectBody(Map<String, Object> body, String type, String id) {
        assertErrorModelBase(body, 404);
        assertEquals(type, body.get("type"));
        assertEquals(id, body.get("id"));
    }

    private static void assertIllegalArgumentBody(Map<String, Object> body) {
        assertErrorModelBase(body, 400);
        assertTrue(body.get("msg") instanceof String, "msg");
    }

    private static List<String> classes(JsonObject obj) {
        return obj.getAsJsonArray("objectClass").asList().stream().map(e -> e.getAsString()).toList();
    }
}

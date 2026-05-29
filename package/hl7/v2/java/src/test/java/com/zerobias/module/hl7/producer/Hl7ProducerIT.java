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
        writeSchema(dir, "schemas/v251/messages/ADT_A01.json", "schema:table:hl7v2.v251.ADT_A01");
        writeSchema(dir, "schemas/v251/messages/ORU_R01.json", "schema:table:hl7v2.v251.ORU_R01");
        writeSchema(dir, "schemas/v251/segments/PID.json", "schema:type:hl7v2.v251.PID");

        BufferStore buffer = new BufferStore(dir.resolve("buffer.db").toString(), false);
        insert(buffer, "M1", "ADT_A01", "ADT", "EPIC", "new", "SMITH");
        insert(buffer, "M2", "ADT_A01", "ADT", "EPIC", "acked", "ASHWORTH");
        insert(buffer, "M3", "ORU_R01", "ORU", "CERNER", "new", "BOOTH");

        SchemaRegistry schemas = SchemaRegistry.fromDirectory(dir);
        ObjectTree tree = new ObjectTree(buffer, schemas, "v251");
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
            app, "HOSP", "2.5.1", "schema:table:hl7v2.v251." + struct, ("raw-" + cid).getBytes(),
            json, MessageStatus.fromWire(status), null, null, null));
    }

    private static String op(Hl7ProducerFacade f, String method, Object... kv) throws Exception {
        Map<String, Object> args = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            args.put((String) kv[i], kv[i + 1]);
        }
        return OperationRouter.executeOperation(f, method, args);
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
        JsonArray rootChildren = GSON.fromJson(
            op(f, "ObjectsApi.getChildren", "objectId", "/"), JsonArray.class);
        assertEquals(1, rootChildren.size());
        assertEquals("/hl7-v2-receiver", rootChildren.get(0).getAsJsonObject().get("id").getAsString());

        // receiver has messages, by-type, by-sender, stats, ops
        JsonArray kids = GSON.fromJson(
            op(f, "ObjectsApi.getChildren", "objectId", "/hl7-v2-receiver"), JsonArray.class);
        assertEquals(5, kids.size());
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
        JsonArray types = GSON.fromJson(
            op(f, "ObjectsApi.getChildren", "objectId", "/hl7-v2-receiver/by-type"), JsonArray.class);
        List<String> ids = types.asList().stream()
            .map(e -> e.getAsJsonObject().get("id").getAsString()).toList();
        assertEquals(List.of("/hl7-v2-receiver/by-type/ADT_A01", "/hl7-v2-receiver/by-type/ORU_R01"), ids);
    }

    @Test
    void bySenderChildrenFromBuffer(@TempDir Path dir) throws Exception {
        Hl7ProducerFacade f = facade(dir);
        JsonArray senders = GSON.fromJson(
            op(f, "ObjectsApi.getChildren", "objectId", "/hl7-v2-receiver/by-sender"), JsonArray.class);
        List<String> ids = senders.asList().stream()
            .map(e -> e.getAsJsonObject().get("id").getAsString()).toList();
        assertEquals(List.of("/hl7-v2-receiver/by-sender/CERNER", "/hl7-v2-receiver/by-sender/EPIC"), ids);
    }

    @Test
    void searchCollectionElementsScopeAndFilter(@TempDir Path dir) throws Exception {
        Hl7ProducerFacade f = facade(dir);

        // all messages
        JsonArray all = GSON.fromJson(
            op(f, "CollectionsApi.searchCollectionElements", "objectId", "/hl7-v2-receiver/messages"),
            JsonArray.class);
        assertEquals(3, all.size());

        // scoped to ADT_A01 -> M1, M2
        JsonArray adt = GSON.fromJson(
            op(f, "CollectionsApi.searchCollectionElements",
                "objectId", "/hl7-v2-receiver/by-type/ADT_A01"), JsonArray.class);
        assertEquals(2, adt.size());

        // scope + RFC4515 filter (status column) -> only the un-acked ADT
        JsonArray adtNew = GSON.fromJson(
            op(f, "CollectionsApi.searchCollectionElements",
                "objectId", "/hl7-v2-receiver/by-type/ADT_A01", "filter", "(status=new)"), JsonArray.class);
        assertEquals(1, adtNew.size());
        assertEquals("M1", adtNew.get(0).getAsJsonObject().get("controlId").getAsString());

        // json-path filter into the typed body
        JsonArray booth = GSON.fromJson(
            op(f, "CollectionsApi.searchCollectionElements",
                "objectId", "/hl7-v2-receiver/messages", "filter", "(pid.patientFamilyName=BOOTH)"),
            JsonArray.class);
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

        // unknown object -> 404 noSuchObjectError
        ProducerException e1 = assertThrows(ProducerException.class,
            () -> op(f, "ObjectsApi.getObject", "objectId", "/hl7-v2-receiver/by-type/NOPE"));
        assertEquals("noSuchObjectError", e1.code());
        assertEquals(404, e1.httpStatus());

        // unknown schema -> 404
        ProducerException e2 = assertThrows(ProducerException.class,
            () -> op(f, "SchemasApi.getSchema", "objectId", "schema:type:hl7v2.v251.NOPE"));
        assertEquals(404, e2.httpStatus());

        // unknown element key -> 404
        ProducerException e3 = assertThrows(ProducerException.class,
            () -> op(f, "CollectionsApi.getCollectionElement",
                "objectId", "/hl7-v2-receiver/messages", "elementKey", "ZZZ"));
        assertEquals("noSuchObjectError", e3.code());

        // write surface rejected -> 400 UnsupportedOperationError
        ProducerException e4 = assertThrows(ProducerException.class,
            () -> op(f, "CollectionsApi.addCollectionElement",
                "objectId", "/hl7-v2-receiver/messages", "element", Map.of("x", 1)));
        assertEquals("UnsupportedOperationError", e4.code());
        assertEquals(400, e4.httpStatus());

        // malformed filter -> 400 illegalArgumentError
        ProducerException e5 = assertThrows(ProducerException.class,
            () -> op(f, "CollectionsApi.searchCollectionElements",
                "objectId", "/hl7-v2-receiver/messages", "filter", "not-a-filter"));
        assertEquals("illegalArgumentError", e5.code());
    }

    private static List<String> classes(JsonObject obj) {
        return obj.getAsJsonArray("objectClass").asList().stream().map(e -> e.getAsString()).toList();
    }
}

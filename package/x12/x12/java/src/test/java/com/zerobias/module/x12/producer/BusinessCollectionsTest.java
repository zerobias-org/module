package com.zerobias.module.x12.producer;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zerobias.module.x12.ModuleRuntimeConfig;
import com.zerobias.module.x12.SourceConfig;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.inbox.FileConsumer;
import com.zerobias.module.x12.materializer.StructureResolver;
import com.zerobias.module.x12.parser.Fixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DESIGN §8.5 end to end: consume a real 835, then query the business collections through the
 * facade the way a caller does — named columns, segmentation ("claims from this file", "claims
 * for this payer") and typed filters.
 */
class BusinessCollectionsTest {

    private static final Gson GSON = new Gson();
    private static final SchemaRegistry SCHEMAS = SchemaRegistry.fromClasspath();
    private static final String R = ObjectTreeApi.RECEIVER;

    @TempDir
    Path dir;
    private BufferStore buffer;
    private X12ProducerFacade facade;
    private String fileId;

    @BeforeEach
    void ingest() throws Exception {
        Path inbox = Files.createDirectories(dir.resolve("inbox"));
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false);
        SourceConfig source = new SourceConfig("inbox", inbox.toString(), "*", 1, 0);
        ModuleRuntimeConfig cfg = new ModuleRuntimeConfig(List.of(source), ".done", ".error", false,
            com.zerobias.module.x12.buffer.RetentionConfig.none(), true, false);

        Path drop = inbox.resolve("remit.835");
        Files.write(drop, Fixtures.bytes(Fixtures.F835));
        FileConsumer consumer = new FileConsumer(buffer, null, cfg, new StructureResolver(), Clock.systemUTC());
        FileConsumer.Result result = consumer.consume(source, drop, java.time.Instant.now());
        assertEquals(FileConsumer.Outcome.CONSUMED, result.outcome(), result.message());
        fileId = result.fileId();

        ObjectTree tree = new ObjectTree(buffer, SCHEMAS, () -> null, ".done", List.of(source), ".error");
        facade = new X12ProducerFacade(buffer, tree, SCHEMAS,
            new X12Operations(buffer, X12ProducerFacade::toElement, () -> null, SCHEMAS, RecastHook.NONE));
    }

    @AfterEach
    void close() throws Exception {
        buffer.close();
    }

    @Test
    void businessCollectionsHangOffTheReceiverWithServableSchemas() throws Exception {
        List<String> names = names(page(facade.getChildren(R, 100, 1)));
        assertTrue(names.containsAll(List.of("remittances", "claims", "service-lines")), names.toString());

        JsonObject claims = GSON.fromJson(facade.getObject(R + "/claims"), JsonObject.class);
        assertEquals(List.of("collection"), classes(claims));
        assertEquals("schema:business:x12.835.Claim", claims.get("collectionSchema").getAsString());
        assertEquals(2, claims.get("collectionSize").getAsLong(), "two claims in the fixture");
        assertTrue(SCHEMAS.has("schema:business:x12.835.Claim"),
            "a collection may not advertise a schema the registry cannot serve");

        JsonObject schema = GSON.fromJson(SCHEMAS.getSchema("schema:business:x12.835.Claim"), JsonObject.class);
        List<String> props = new ArrayList<>();
        for (var el : schema.getAsJsonArray("properties")) {
            props.add(el.getAsJsonObject().get("name").getAsString());
        }
        assertTrue(props.containsAll(List.of("claimId", "paidAmount", "patientLastName", "elementKey",
            "fileId", "payerName")), props.toString());
    }

    @Test
    void claimsComeBackAsNamedRows() throws Exception {
        JsonObject page = page(facade.getCollectionElements(R + "/claims", null, null, null, 50, 1, null));
        assertEquals(2, page.get("count").getAsLong());
        JsonObject first = page.getAsJsonArray("items").get(0).getAsJsonObject();

        assertEquals("CLM0001", first.get("claimId").getAsString());
        assertEquals(0, first.get("paidAmount").getAsBigDecimal().compareTo(new java.math.BigDecimal("220.00")));
        assertEquals("DOE", first.get("patientLastName").getAsString());
        assertEquals("EXAMPLE HEALTH PLAN", first.get("payerName").getAsString(), "dimension carried onto the row");
        assertEquals(fileId, first.get("fileId").getAsString(), "provenance back to the interchange");
        assertTrue(first.get("elementKey").getAsString().startsWith(fileId));
    }

    @Test
    void segmentsAreEmergentAndScopeTheRows() throws Exception {
        // the collection is also a container of its segments
        List<String> segments = names(page(facade.getChildren(R + "/claims", 100, 1)));
        assertTrue(segments.containsAll(List.of("by-file", "by-payerName", "by-payeeNpi")), segments.toString());

        // "claims from this file"
        List<String> files = names(page(facade.getChildren(R + "/claims/by-file", 100, 1)));
        assertEquals(List.of(fileId), files);
        String scoped = R + "/claims/by-file/" + ObjectTree.encodeSegment(fileId);
        assertEquals(2, page(facade.getCollectionElements(scoped, null, null, null, 50, 1, null))
            .get("count").getAsLong());

        // "claims for this payer"
        List<String> payers = names(page(facade.getChildren(R + "/claims/by-payerName", 100, 1)));
        assertEquals(List.of("EXAMPLE HEALTH PLAN"), payers);
        String byPayer = R + "/claims/by-payerName/" + ObjectTree.encodeSegment("EXAMPLE HEALTH PLAN");
        JsonObject payerClaims = page(facade.getCollectionElements(byPayer, null, null, null, 50, 1, null));
        assertEquals(2, payerClaims.get("count").getAsLong());

        // an unknown segment value is a 404, not an empty page
        assertEquals(404, assertThrows(ProducerException.class, () -> facade.getCollectionElements(
            R + "/claims/by-payerName/NOBODY", null, null, null, 10, 1, null)).httpStatus());
    }

    @Test
    void filtersCompareByTheColumnsDeclaredType() throws Exception {
        // the query this whole layer exists for
        assertEquals(1, filtered("/claims", "(paidAmount>=230)"), "only CLM0002 paid 240.00");
        assertEquals(2, filtered("/claims", "(paidAmount>=220)"), "inclusive bound");
        assertEquals(0, filtered("/claims", "(paidAmount>=1000)"));
        assertEquals(1, filtered("/claims", "(&(claimStatus=1)(paidAmount<=220))"));
        assertEquals(2, filtered("/claims", "(|(claimId=CLM0001)(claimId=CLM0002))"));
        assertEquals(1, filtered("/claims", "(!(claimId=CLM0001))"));
        assertEquals(1, filtered("/claims", "(patientLastName=DO*)"), "prefix match");
        assertEquals(2, filtered("/claims", "(allowedAmount=*)"), "presence");

        // service lines have their own grain: three lines across the two claims
        assertEquals(3, filtered("/service-lines", null));
        assertEquals(1, filtered("/service-lines", "(procedureCode=99214)"));
        assertEquals(2, filtered("/service-lines", "(paidAmount>=160)"));

        // a numeric column never compares lexically: 60.00 < 160.00 even though "6" > "1"
        assertEquals(1, filtered("/service-lines", "(paidAmount<=60)"));
    }

    @Test
    void anUnknownColumnIsReportedNotSilentlyEmpty() {
        ProducerException e = assertThrows(ProducerException.class, () -> facade.getCollectionElements(
            R + "/claims", "(nope=1)", null, null, 10, 1, null));
        assertEquals(400, e.httpStatus());
        assertTrue(e.getMessage().contains("nope"), e.getMessage());
    }

    @Test
    void remittanceGrainIsOneRowPerTransactionSet() throws Exception {
        JsonObject page = page(facade.getCollectionElements(R + "/remittances", null, null, null, 50, 1, null));
        assertEquals(1, page.get("count").getAsLong());
        JsonObject r = page.getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals(0, r.get("paymentAmount").getAsBigDecimal().compareTo(new java.math.BigDecimal("450.00")));
        assertEquals("EFT000000101", r.get("checkOrEftNumber").getAsString());
        assertEquals("EXAMPLE MEDICAL GROUP", r.get("payeeName").getAsString());
    }

    // --- helpers ------------------------------------------------------------

    private long filtered(String collection, String filter) throws Exception {
        return page(facade.getCollectionElements(R + collection, filter, null, null, 50, 1, null))
            .get("count").getAsLong();
    }

    private static JsonObject page(String json) {
        return GSON.fromJson(json, JsonObject.class);
    }

    private static List<String> names(JsonObject page) {
        List<String> out = new ArrayList<>();
        JsonArray items = page.getAsJsonArray("items");
        for (int i = 0; i < items.size(); i++) {
            out.add(items.get(i).getAsJsonObject().get("name").getAsString());
        }
        return out;
    }

    private static List<String> classes(JsonObject o) {
        List<String> out = new ArrayList<>();
        for (var el : o.getAsJsonArray("objectClass")) {
            out.add(el.getAsString());
        }
        return out;
    }
}

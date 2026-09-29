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

import java.nio.charset.StandardCharsets;
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
 * DESIGN §8.5 across guides: the 837P/837I claim and service-line collections next to the
 * 835's, and the dimension-grain {@code /payers} and {@code /payees} collections spanning
 * them — queried through the facade the way a caller does.
 *
 * <p>The buffer holds: the 835 fixture (payer named, no id), the same 835 from a second payer
 * that is only ever named ("ACME PAYER"), the 837P fixture, an 837P whose single payer is
 * "OTHER PLAN" (OTHID00002), the 837I fixture, and the 837I again under the aliased GS08
 * {@code 005010X223A1}.
 */
class BusinessParties837Test {

    private static final Gson GSON = new Gson();
    private static final SchemaRegistry SCHEMAS = SchemaRegistry.fromClasspath();
    private static final String R = ObjectTreeApi.RECEIVER;

    @TempDir
    Path dir;
    private BufferStore buffer;
    private X12ProducerFacade facade;

    @BeforeEach
    void ingest() throws Exception {
        Path inbox = Files.createDirectories(dir.resolve("inbox"));
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false);
        SourceConfig source = new SourceConfig("inbox", inbox.toString(), "*", 1, 0);
        ModuleRuntimeConfig cfg = new ModuleRuntimeConfig(List.of(source), ".done", ".error", false,
            com.zerobias.module.x12.buffer.RetentionConfig.none(), true, false);
        FileConsumer consumer = new FileConsumer(buffer, null, cfg, new StructureResolver(), Clock.systemUTC());

        String f835 = Fixtures.text(Fixtures.F835);
        String f837p = Fixtures.text(Fixtures.F837P);
        String f837i = Fixtures.text(Fixtures.F837I);
        drop(consumer, source, inbox, "remit.835", f835);
        drop(consumer, source, inbox, "acme.835", f835.replace("N1*PR*EXAMPLE HEALTH PLAN~", "N1*PR*ACME PAYER~"));
        drop(consumer, source, inbox, "prof.837", f837p);
        drop(consumer, source, inbox, "other.837", f837p
            .replace("NM1*PR*2*EXAMPLE HEALTH PLAN*****PI*EHPID00001~", "NM1*PR*2*OTHER PLAN*****PI*OTHID00002~")
            .replace("CLM*CLM0001*", "CLM*CLM0101*"));
        drop(consumer, source, inbox, "inst.837", f837i);
        drop(consumer, source, inbox, "inst-a1.837", f837i
            .replace("005010X223A2", "005010X223A1")
            .replace("CLM*CLM0002*", "CLM*CLM0003*"));

        ObjectTree tree = new ObjectTree(buffer, SCHEMAS, () -> null, ".done", List.of(source), ".error");
        facade = new X12ProducerFacade(buffer, tree, SCHEMAS,
            new X12Operations(buffer, X12ProducerFacade::toElement, () -> null, SCHEMAS, RecastHook.NONE));
    }

    private static void drop(FileConsumer consumer, SourceConfig source, Path inbox, String name, String text)
            throws Exception {
        Path file = inbox.resolve(name);
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
        FileConsumer.Result r = consumer.consume(source, file, java.time.Instant.now());
        assertEquals(FileConsumer.Outcome.CONSUMED, r.outcome(), name + ": " + r.message());
    }

    @AfterEach
    void close() throws Exception {
        buffer.close();
    }

    @Test
    void everyNewCollectionHangsOffTheReceiverWithAServableSchema() throws Exception {
        List<String> names = names(page(facade.getChildren(R, 100, 1)));
        List<String> expected = List.of("claims", "service-lines", "remittances", "professional-claims",
            "professional-service-lines", "institutional-claims", "institutional-service-lines", "payers",
            "payees");
        assertTrue(names.containsAll(expected), names.toString());

        for (String c : expected) {
            JsonObject node = GSON.fromJson(facade.getObject(R + "/" + c), JsonObject.class);
            String schemaId = node.get("collectionSchema").getAsString();
            assertTrue(SCHEMAS.has(schemaId), c + " advertises " + schemaId + " which the registry cannot serve");
        }
        assertEquals("schema:business:x12.837P.Claim", collectionSchema("professional-claims"));
        assertEquals("schema:business:x12.837I.ServiceLine", collectionSchema("institutional-service-lines"));
        assertEquals("schema:business:x12.Payer", collectionSchema("payers"));

        // the Payer schema is generated from the mapping: its columns, typed, no provenance
        JsonObject schema = GSON.fromJson(SCHEMAS.getSchema("schema:business:x12.Payer"), JsonObject.class);
        List<String> props = new ArrayList<>();
        for (var el : schema.getAsJsonArray("properties")) {
            props.add(el.getAsJsonObject().get("name").getAsString());
        }
        assertEquals(List.of("payerKey", "payerName", "payerId", "transactionCount", "firstSeen", "lastSeen",
            "transactionTypes"), props);
    }

    @Test
    void claimCollectionsStayWithinTheirGuide() throws Exception {
        assertEquals(4, count("/claims", null), "the two 835s, two CLPs each — no 837 claim leaks in");
        assertEquals(2, count("/professional-claims", null));
        assertEquals(4, count("/professional-service-lines", null));
        assertEquals(2, count("/institutional-claims", null), "X223A2 and its X223A1 alias share one grain");
        assertEquals(6, count("/institutional-service-lines", null));
        assertEquals(1, count("/institutional-claims", "(claimId=CLM0003)"), "the aliased guide's claim");

        JsonObject claim = first("/professional-claims", "(claimId=CLM0001)");
        assertEquals("EXAMPLE MEDICAL GROUP", claim.get("billingProviderName").getAsString(), "dimension on the row");
        assertEquals("EHPID00001", claim.get("payerId").getAsString());
        assertEquals("DOE", claim.get("subscriberLastName").getAsString());
        String raw = facade.getCollectionElements(R + "/professional-claims", "(claimId=CLM0001)", null, null,
            10, 1, null);
        assertTrue(raw.contains("\"chargedAmount\":300.00"), raw);

        // typed filters and sorts work on the new collections exactly as on /claims
        assertEquals(1, count("/institutional-service-lines", "(&(chargedAmount>=1000)(claimId=CLM0002))"));
        assertEquals(2, count("/institutional-service-lines", "(revenueCode=0450)"));
        JsonObject cheapest = first("/professional-service-lines", null, "chargedAmount", "asc");
        assertEquals("36415", cheapest.get("procedureCode").getAsString());
        assertEquals(1, count("/professional-service-lines", "(&(claimId=CLM0101)(lineNumber=2))"),
            "an integer column filters numerically");
    }

    @Test
    void segmentValuesAreScopedToTheEntitysOwnRows() throws Exception {
        // payerName is a dimension of both the 835 and the 837s. "OTHER PLAN" only ever sent an
        // 837, so it must not be offered as a segment of the 835's /claims — that would be a
        // node leading to an empty page.
        List<String> remitPayers = names(page(facade.getChildren(R + "/claims/by-payerName", 100, 1)));
        assertEquals(List.of("ACME PAYER", "EXAMPLE HEALTH PLAN"), remitPayers);
        List<String> profPayers = names(page(facade.getChildren(R + "/professional-claims/by-payerName", 100, 1)));
        assertEquals(List.of("EXAMPLE HEALTH PLAN", "OTHER PLAN"), profPayers);

        assertEquals(1, page(facade.getCollectionElements(R + "/professional-claims/by-payerName/"
            + ObjectTree.encodeSegment("OTHER PLAN"), null, null, null, 10, 1, null)).get("count").getAsLong());
        assertEquals(404, assertThrows(ProducerException.class, () -> facade.getCollectionElements(
            R + "/claims/by-payerName/" + ObjectTree.encodeSegment("OTHER PLAN"), null, null, null, 10, 1, null))
            .httpStatus());

        List<String> segments = names(page(facade.getChildren(R + "/institutional-claims", 100, 1)));
        assertTrue(segments.containsAll(List.of("by-file", "by-billingProviderNpi", "by-payerId")), segments.toString());
    }

    @Test
    void payersAreOneRowPerPartyAcrossGuides() throws Exception {
        JsonObject all = page(facade.getCollectionElements(R + "/payers", null, "payerName", "asc", 50, 1, null));
        assertEquals(3, all.get("count").getAsLong(), all.toString());
        JsonArray items = all.getAsJsonArray("items");

        JsonObject acme = items.get(0).getAsJsonObject();
        assertEquals("name:ACME PAYER", acme.get("payerKey").getAsString(), "never stated an id: keyed by name");
        assertTrue(acme.get("payerId").isJsonNull(), "present and null: one row shape");
        assertEquals(1, acme.get("transactionCount").getAsLong());
        assertEquals("835", acme.get("transactionTypes").getAsString());

        JsonObject example = items.get(1).getAsJsonObject();
        assertEquals("id:EHPID00001", example.get("payerKey").getAsString());
        assertEquals("EXAMPLE HEALTH PLAN", example.get("payerName").getAsString());
        assertEquals("EHPID00001", example.get("payerId").getAsString());
        assertEquals(4, example.get("transactionCount").getAsLong(),
            "the name-only 835 joins the one identified payer of that name, plus 837P and both 837Is");
        assertEquals("835,837I,837P", example.get("transactionTypes").getAsString());
        assertFalse(example.get("firstSeen").isJsonNull());
        assertTrue(example.get("lastSeen").getAsString().compareTo(example.get("firstSeen").getAsString()) >= 0);

        JsonObject other = items.get(2).getAsJsonObject();
        assertEquals("id:OTHID00002", other.get("payerKey").getAsString());
        assertEquals("837P", other.get("transactionTypes").getAsString());

        // the collection node counts parties, not transactions
        JsonObject node = GSON.fromJson(facade.getObject(R + "/payers"), JsonObject.class);
        assertEquals(3, node.get("collectionSize").getAsLong());
        assertEquals(List.of(), names(page(facade.getChildren(R + "/payers", 100, 1))), "no segments");
    }

    @Test
    void payersFilterSortAndPageThroughTheSameMachinery() throws Exception {
        assertEquals(1, count("/payers", "(payerName=EXAMPLE*)"));
        assertEquals(2, count("/payers", "(transactionTypes:contains:837P)"), "EXAMPLE and OTHER");
        assertEquals(1, count("/payers", "(transactionCount>=2)"), "integer compares numerically");
        assertEquals(2, count("/payers", "(payerId=*)"), "presence: ACME has no id");
        assertEquals(1, count("/payers", "(!(payerId=*))"));

        assertEquals(List.of("OTHER PLAN", "EXAMPLE HEALTH PLAN", "ACME PAYER"), payerNames("payerName", "desc"));
        assertEquals("EXAMPLE HEALTH PLAN", payerNames("transactionCount", "desc").get(0));
        assertEquals("ACME PAYER", payerNames("payerId", "desc").get(2), "NULLs last in both directions");
        assertEquals("ACME PAYER", payerNames("payerId", "asc").get(2));

        JsonObject p2 = page(facade.getCollectionElements(R + "/payers", null, "payerName", "asc", 1, 2, null));
        assertEquals(3, p2.get("count").getAsLong());
        assertEquals(1, p2.getAsJsonArray("items").size());
        assertEquals("EXAMPLE HEALTH PLAN", p2.getAsJsonArray("items").get(0).getAsJsonObject()
            .get("payerName").getAsString());

        // a party has no elementKey: filtering on one is an unknown attribute, not an empty page
        ProducerException e = assertThrows(ProducerException.class, () -> facade.getCollectionElements(
            R + "/payers", "(elementKey=*)", null, null, 10, 1, null));
        assertEquals(400, e.httpStatus());
        assertEquals(400, assertThrows(ProducerException.class, () -> facade.getCollectionElements(
            R + "/payers", null, "fileId", "asc", 10, 1, null)).httpStatus());
    }

    @Test
    void payeesComeFromThe835PayeeSide() throws Exception {
        JsonObject all = page(facade.getCollectionElements(R + "/payees", null, null, null, 50, 1, null));
        assertEquals(1, all.get("count").getAsLong());
        JsonObject payee = all.getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals("id:1234567893", payee.get("payeeKey").getAsString());
        assertEquals("EXAMPLE MEDICAL GROUP", payee.get("payeeName").getAsString());
        assertEquals("1234567893", payee.get("payeeNpi").getAsString());
        assertEquals(2, payee.get("transactionCount").getAsLong(), "both 835s pay the same group");
    }

    @Test
    void aMultiPayerBatchIsInNoPayerSegmentButEachClaimKeepsItsPayer() throws Exception {
        Path inbox = dir.resolve("inbox");
        SourceConfig source = new SourceConfig("inbox", inbox.toString(), "*", 1, 0);
        ModuleRuntimeConfig cfg = new ModuleRuntimeConfig(List.of(source), ".done", ".error", false,
            com.zerobias.module.x12.buffer.RetentionConfig.none(), true, false);
        drop(new FileConsumer(buffer, null, cfg, new StructureResolver(), Clock.systemUTC()), source, inbox,
            "two-payers.837", Entity837MappingTest.twoPayer837P());

        assertEquals(4, count("/professional-claims", null));
        JsonObject c = first("/professional-claims", "(claimId=CLM0009)");
        assertTrue(c.get("payerName").isJsonNull(), "the transaction names two payers: no dimension");
        assertEquals("OTHER PLAN", c.get("claimPayerName").getAsString(), "the claim's own payer is exact");
        assertEquals(2, count("/professional-claims", "(claimPayerName=OTHER PLAN)"), "CLM0101 and CLM0009");
        assertEquals(0, count("/professional-claims/by-payerName/" + ObjectTree.encodeSegment("OTHER PLAN"),
            "(claimId=CLM0009)"), "not attributed to either payer's segment");
        assertEquals(1, count("/professional-claims/by-billingProviderNpi/1234567893", "(claimId=CLM0009)"),
            "the unambiguous dimensions of the same transaction still segment it");
    }

    // --- helpers ------------------------------------------------------------

    private String collectionSchema(String c) throws Exception {
        return GSON.fromJson(facade.getObject(R + "/" + c), JsonObject.class).get("collectionSchema").getAsString();
    }

    private long count(String collection, String filter) throws Exception {
        return page(facade.getCollectionElements(R + collection, filter, null, null, 50, 1, null))
            .get("count").getAsLong();
    }

    private JsonObject first(String collection, String filter) throws Exception {
        return first(collection, filter, null, null);
    }

    private JsonObject first(String collection, String filter, String sortBy, String sortDir) throws Exception {
        JsonArray items = page(facade.getCollectionElements(R + collection, filter, sortBy, sortDir, 50, 1, null))
            .getAsJsonArray("items");
        assertFalse(items.isEmpty(), collection + " " + filter);
        return items.get(0).getAsJsonObject();
    }

    private List<String> payerNames(String sortBy, String sortDir) throws Exception {
        List<String> out = new ArrayList<>();
        for (var el : page(facade.getCollectionElements(R + "/payers", null, sortBy, sortDir, 50, 1, null))
                .getAsJsonArray("items")) {
            out.add(el.getAsJsonObject().get("payerName").getAsString());
        }
        return out;
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
}

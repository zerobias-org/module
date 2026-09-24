package com.zerobias.module.x12.producer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zerobias.module.x12.ModuleRuntimeConfig;
import com.zerobias.module.x12.SourceConfig;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.RetentionConfig;
import com.zerobias.module.x12.buffer.TestRows;
import com.zerobias.module.x12.buffer.TransactionRow;
import com.zerobias.module.x12.inbox.FileConsumer;
import com.zerobias.module.x12.inbox.InboxFixtures;
import com.zerobias.module.x12.materializer.StructureResolver;
import com.zerobias.module.x12.parser.Fixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The production {@link RecastHook}: rows ingested by the real {@link FileConsumer} are
 * re-materialized from their stored raw — a fresh row reproduces byte-for-byte, a stale
 * mapping is restored by {@code ops/recast}, and {@code ops/validate} reports {@code repsAgree}.
 */
class MaterializerRecastHookTest {

    private static final SchemaRegistry SCHEMAS = SchemaRegistry.fromClasspath();

    @TempDir
    Path dir;
    private TestRows.MutableClock clock;
    private BufferStore buffer;
    private X12Operations ops;
    private String key835;
    private String key837;

    @BeforeEach
    void ingest() throws Exception {
        clock = new TestRows.MutableClock(TestRows.BASE);
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false, clock);
        Path inbox = Files.createDirectories(dir.resolve("inbox"));
        ModuleRuntimeConfig cfg = new ModuleRuntimeConfig(
            List.of(new SourceConfig("inbox", inbox.toString(), "*", 1, 0)),
            ".done", ".error", false, RetentionConfig.none(), true, ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES);
        StructureResolver resolver = new StructureResolver();
        FileConsumer consumer = new FileConsumer(buffer, null, cfg, resolver, clock);
        SourceConfig src = cfg.sources().get(0);
        Path a = Files.write(inbox.resolve("remit.835"), Fixtures.bytes(Fixtures.F835));
        Path b = Files.write(inbox.resolve("claims.837"), Fixtures.bytes(Fixtures.F837P));
        // <fileId>:<ISA13>:<GS06>:<ST02>, from the fixtures' envelopes
        key835 = InboxFixtures.consume(consumer, src, a, TestRows.BASE).fileId() + ":000000101:101:0001";
        key837 = InboxFixtures.consume(consumer, src, b, TestRows.BASE).fileId() + ":000000102:102:0001";
        ops = new X12Operations(buffer, new ProducerFixture.StubPoller(inbox), SCHEMAS,
            new MaterializerRecastHook(resolver, clock));
    }

    @AfterEach
    void close() throws Exception {
        buffer.close();
    }

    @Test
    void freshRowsReproduceByteForByte() throws Exception {
        MaterializerRecastHook hook = new MaterializerRecastHook(new StructureResolver(), clock);
        for (String key : List.of(key835, key837)) {
            TransactionRow row = buffer.byElementKey(key).orElseThrow();
            assertTrue(hook.recast(row).isEmpty(), key + ": identical → nothing to rewrite");
            RecastHook.Mapping m = hook.rematerialize(row);
            assertEquals(row.schemaId(), m.schemaId());
            assertEquals(row.mappedJson(), m.mappedJson());
            assertEquals(List.of(), m.parserErrors());
        }
        Map<String, Object> out = ops.invoke("recast", Map.of());
        assertEquals(2, out.get("examined"));
        assertEquals(0, out.get("recast"));
        assertEquals(2, out.get("unchanged"));
        assertEquals(0, out.get("failed"));
    }

    @Test
    void freshRowsReproduceUnderTheSystemClock() throws Exception {
        // The production clock has nanos; the buffer stores millis. The ingest must truncate so the
        // stored JSON's receivedAt reproduces from the column (this is what the container e2e hits).
        java.time.Clock system = java.time.Clock.systemUTC();
        try (BufferStore b = new BufferStore(dir.resolve("sys.db").toString(), false, system)) {
            Path inbox = Files.createDirectories(dir.resolve("sys-inbox"));
            ModuleRuntimeConfig cfg = new ModuleRuntimeConfig(
                List.of(new SourceConfig("inbox", inbox.toString(), "*", 1, 0)),
                ".done", ".error", false, RetentionConfig.none(), false, ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES);
            StructureResolver resolver = new StructureResolver();
            FileConsumer consumer = new FileConsumer(b, null, cfg, resolver, system);
            Path a = Files.write(inbox.resolve("remit.835"), Fixtures.bytes(Fixtures.F835));
            String key = InboxFixtures.consume(consumer, cfg.sources().get(0), a, java.time.Instant.now()).fileId()
                + ":000000101:101:0001";
            TransactionRow row = b.byElementKey(key).orElseThrow();
            RecastHook.Mapping m = new MaterializerRecastHook(resolver, system).rematerialize(row);
            assertEquals(row.mappedJson(), m.mappedJson(), "receivedAt must round-trip through the epoch-millis column");
            assertTrue(m.reproduces(row));
        }
    }

    @Test
    void recastRestoresAStaleMapping() throws Exception {
        TransactionRow row = buffer.byElementKey(key835).orElseThrow();
        String original = row.mappedJson();
        assertTrue(buffer.updateMapping(row.id(), StructureResolver.ENVELOPE_SCHEMA, "{}"), "simulate a stale mapping");
        assertEquals("{}", buffer.byElementKey(key835).orElseThrow().mappedJson());

        Map<String, Object> out = ops.invoke("recast", Map.of("filter", "(transactionType=835)"));
        assertEquals(1, out.get("examined"));
        assertEquals(1, out.get("recast"));
        assertEquals(0, out.get("unchanged"));
        assertEquals(0, out.get("failed"));

        TransactionRow restored = buffer.byElementKey(key835).orElseThrow();
        assertEquals(original, restored.mappedJson(), "the real JSON is back");
        assertEquals(TestRows.SCHEMA_835, restored.schemaId());
        JsonObject json = JsonParser.parseString(restored.mappedJson()).getAsJsonObject();
        assertEquals(key835, json.get("elementKey").getAsString());
        assertEquals("remit.835", json.get("fileName").getAsString());
        assertTrue(json.has("header"), json.keySet().toString());

        assertEquals(0, ops.invoke("recast", Map.of()).get("recast"), "second pass: nothing left to rewrite");
    }

    @Test
    void validateReportsRepsAgree() throws Exception {
        Map<String, Object> fresh = ops.invoke("validate", Map.of("elementKey", key837));
        assertEquals(true, fresh.get("repsAgree"));
        @SuppressWarnings("unchecked")
        Map<String, Object> rv = (Map<String, Object>) fresh.get("rematerialized");
        assertEquals(true, rv.get("valid"), String.valueOf(rv.get("errors")));
        assertEquals(TestRows.SCHEMA_837P, rv.get("schemaId"));
        assertEquals(List.of(), fresh.get("parserErrors"));

        TransactionRow row = buffer.byElementKey(key837).orElseThrow();
        buffer.updateMapping(row.id(), row.schemaId(), "{\"header\":{}}");
        Map<String, Object> tampered = ops.invoke("validate", Map.of("elementKey", key837));
        assertEquals(false, tampered.get("repsAgree"));
        @SuppressWarnings("unchecked")
        Map<String, Object> stored = (Map<String, Object>) tampered.get("stored");
        assertEquals(true, stored.get("valid"), "still parses as JSON");
        @SuppressWarnings("unchecked")
        Map<String, Object> re = (Map<String, Object>) tampered.get("rematerialized");
        assertEquals(true, re.get("valid"));
        assertNotEquals("{\"header\":{}}", new MaterializerRecastHook(new StructureResolver(), clock)
            .rematerialize(buffer.byElementKey(key837).orElseThrow()).mappedJson());
    }

    @Test
    void aFileMixingGuidesStoresEachTransactionUnderItsOwnGuide() throws Exception {
        // One ISA carrying a 999 group (005010X231A1) and then a 277CA group (005010X214).
        String ack = Fixtures.text(Fixtures.F999);
        String status = Fixtures.text(Fixtures.F277CA);
        String statusGroup = status.substring(status.indexOf("GS*"), status.indexOf("IEA*"));
        String mixed = ack.replace("IEA*1*000000105~", statusGroup + "IEA*2*000000105~");
        Path inbox = dir.resolve("inbox");
        Path file = Files.write(inbox.resolve("acks.x12"), mixed.getBytes(StandardCharsets.UTF_8));
        StructureResolver resolver = new StructureResolver();
        ModuleRuntimeConfig cfg = new ModuleRuntimeConfig(
            List.of(new SourceConfig("inbox", inbox.toString(), "*", 1, 0)),
            ".done", ".error", false, RetentionConfig.none(), true, ModuleRuntimeConfig.DEFAULT_MAX_FILE_BYTES);
        String fileId = InboxFixtures.consume(new FileConsumer(buffer, null, cfg, resolver, clock),
            cfg.sources().get(0), file, TestRows.BASE).fileId();

        TransactionRow ack999 = buffer.byElementKey(fileId + ":000000105:105:0001").orElseThrow();
        TransactionRow claim277 = buffer.byElementKey(fileId + ":000000105:104:0001").orElseThrow();
        assertEquals("005010X231A1", ack999.gs08());
        assertEquals("999", ack999.transactionType());
        assertEquals("schema:table:x12.005010X231A1.999", ack999.schemaId());
        assertEquals("005010X214", claim277.gs08(), "the second group's guide, not the file's first");
        assertEquals("277CA", claim277.transactionType());
        assertEquals("schema:table:x12.005010X214.277CA", claim277.schemaId());
        assertEquals("005010X214", JsonParser.parseString(claim277.mappedJson()).getAsJsonObject().get("gs08").getAsString());

        MaterializerRecastHook hook = new MaterializerRecastHook(resolver, clock);
        for (TransactionRow row : List.of(ack999, claim277)) {
            assertTrue(hook.recast(row).isEmpty(), row.elementKey() + " reproduces under its own guide");
        }
    }

    @Test
    void unparseableRawIsFailedNotFatal() throws Exception {
        TestRows.insert(buffer, TestRows.tx("/in/x.835@000000000000", "inbox", "9", "0009", 0,
            "005010X221A1", "835", TestRows.SCHEMA_835, "P"));   // rawX12 = bare ST/SE with no body
        String garbageKey = TestRows.key("/in/y.835@000000000000", "8", "0008");
        TestRows.insert(buffer, TransactionRow.builder()
            .fileId("/in/y.835@000000000000").sourceName("inbox").gsControl("8").stControl("0008").elementKey(garbageKey)
            .receivedAt(TestRows.BASE).gs08("005010X221A1").transactionType("835").schemaId(TestRows.SCHEMA_835)
            .rawX12("garbage".getBytes()).mappedJson("{}").build());
        Map<String, Object> out = ops.invoke("recast", Map.of());
        assertEquals(4, out.get("examined"));
        assertEquals(2, out.get("failed"), "garbage raw + an ST/SE with no body (imsweb rejects it)");
        assertEquals(0, out.get("recast"));
        assertEquals(2, out.get("unchanged"), "the real rows are untouched");
        assertEquals("{}", buffer.byElementKey(garbageKey).orElseThrow().mappedJson());

        Map<String, Object> v = ops.invoke("validate", Map.of("elementKey", garbageKey));
        assertEquals(false, v.get("repsAgree"));
        @SuppressWarnings("unchecked")
        Map<String, Object> rv = (Map<String, Object>) v.get("rematerialized");
        assertEquals(false, rv.get("valid"));
        assertTrue(((List<?>) rv.get("errors")).get(0).toString().startsWith("re-materialization failed"));
        assertTrue(rv.containsKey("schemaId"), "the verdict's shape does not change on failure");
        assertNull(rv.get("schemaId"));
        assertTrue(Optional.ofNullable(v.get("parserErrors")).isPresent());
    }
}

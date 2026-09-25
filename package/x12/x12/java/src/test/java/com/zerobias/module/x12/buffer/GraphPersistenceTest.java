package com.zerobias.module.x12.buffer;

import com.google.gson.Gson;
import com.zerobias.module.x12.materializer.EntityGraph;
import com.zerobias.module.x12.materializer.Materializer;
import com.zerobias.module.x12.materializer.StructureResolver;
import com.zerobias.module.x12.parser.Fixtures;
import com.zerobias.module.x12.parser.X12Parse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DESIGN §8.4: the object graph survives the database. Flatten → commit → read back →
 * reassemble must equal what the materializer produced, or the rows are not a faithful
 * substitute for the document and nothing queried off them can be trusted.
 */
class GraphPersistenceTest {

    private static final Gson GSON = new Gson();
    private static final String KEY = "/in/remit.835@abc123456789:101:0001";

    @TempDir
    Path dir;
    private BufferStore buffer;

    @BeforeEach
    void open() throws Exception {
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false);
    }

    @AfterEach
    void close() throws Exception {
        buffer.close();
    }

    @Test
    void graphSurvivesACommitAndReassemblesExactly() throws Exception {
        Map<String, Object> tree = materialize(Fixtures.F835);
        List<EntityGraph.Entity> flattened = flatten(Fixtures.F835, tree);

        int written = buffer.consumeFile(file(), List.of(row(KEY)), Map.of(KEY, flattened));
        assertEquals(1, written);
        assertEquals(flattened.size(), buffer.entityCount(KEY), "every instance persisted");

        List<EntityGraph.Entity> readBack = buffer.graphFor(KEY);
        assertEquals(flattened.size(), readBack.size());
        assertEquals(GSON.toJson(tree), GSON.toJson(EntityGraph.assemble(readBack)),
            "read back from SQLite it must reassemble into the materialized form");
    }

    @Test
    void typedValuesComeBackTypedSoFiltersCanCompare() throws Exception {
        Map<String, Object> tree = materialize(Fixtures.F835);
        buffer.consumeFile(file(), List.of(row(KEY)), Map.of(KEY, flatten(Fixtures.F835, tree)));

        EntityGraph.Value paid = null;
        EntityGraph.Value effective = null;
        for (EntityGraph.Entity e : buffer.graphFor(KEY)) {
            if ("BPR".equals(e.xid)) {
                for (EntityGraph.Value v : e.values) {
                    if ("bpr02".equals(v.property())) {
                        paid = v;
                    } else if ("bpr16".equals(v.property())) {
                        effective = v;
                    }
                }
            }
        }
        assertNotNull(paid);
        assertEquals("decimal", paid.dataType());
        assertEquals(0, paid.num().compareTo(new BigDecimal("450")), "money round-trips as a number");
        assertNotNull(effective);
        assertEquals("date", effective.dataType());
        assertNotNull(effective.date(), "a date round-trips as epoch-millis");
    }

    @Test
    void claimsAreAddressableRowsNotPathsInABlob() throws Exception {
        Map<String, Object> tree = materialize(Fixtures.F835);
        buffer.consumeFile(file(), List.of(row(KEY)), Map.of(KEY, flatten(Fixtures.F835, tree)));

        // The 835 fixture carries two claims; each CLP is its own row with its own schema.
        List<String> claimIds = new ArrayList<>();
        for (EntityGraph.Entity e : buffer.graphFor(KEY)) {
            if ("CLP".equals(e.xid)) {
                assertEquals("schema:type:x12.005010X221A1.CLP", e.schemaId);
                for (EntityGraph.Value v : e.values) {
                    if ("clp01".equals(v.property())) {
                        claimIds.add(v.text());
                    }
                }
            }
        }
        assertEquals(List.of("CLM0001", "CLM0002"), claimIds, "one row per claim, in wire order");
    }

    @Test
    void purgingATransactionTakesItsGraphWithIt() throws Exception {
        Map<String, Object> tree = materialize(Fixtures.F835);
        buffer.consumeFile(file(), List.of(row(KEY)), Map.of(KEY, flatten(Fixtures.F835, tree)));
        assertTrue(buffer.entityCount(KEY) > 0);

        Lease lease = buffer.take(null, 10, Duration.ofMinutes(5));
        assertEquals(1, buffer.ack(lease.leaseId(), null));
        assertEquals(1, buffer.purge(Duration.ZERO));

        assertEquals(0, buffer.entityCount(KEY), "no orphaned entities after purge");
        assertEquals(List.of(), buffer.graphFor(KEY));
    }

    // --- helpers ------------------------------------------------------------

    private static Map<String, Object> materialize(String fixture) throws Exception {
        X12Parse.ParsedFile p = X12Parse.parse(Fixtures.bytes(fixture), true);
        Materializer m = new StructureResolver().materializerFor(p.gs08(), p.separators()).orElseThrow();
        return m.materializeTransaction(p.transactions().get(0).loop());
    }

    private static List<EntityGraph.Entity> flatten(String fixture, Map<String, Object> tree) throws Exception {
        X12Parse.ParsedFile p = X12Parse.parse(Fixtures.bytes(fixture), true);
        Materializer m = new StructureResolver().materializerFor(p.gs08(), p.separators()).orElseThrow();
        List<EntityGraph.Entity> flattened = EntityGraph.flatten(m.index(), tree);
        assertFalse(flattened.isEmpty());
        return flattened;
    }

    private FileRow file() {
        return new FileRow(0, "/in/remit.835@abc123456789", "/in/remit.835", "remit.835", "inbox",
            "/in/remit.835.done", 1319, "abc123456789", java.time.Instant.EPOCH, java.time.Instant.EPOCH,
            java.time.Instant.EPOCH, FileStatus.CONSUMED, 1, 1, null, false, 0);
    }

    private TransactionRow row(String elementKey) {
        return TransactionRow.builder()
            .elementKey(elementKey)
            .fileId("/in/remit.835@abc123456789")
            .sourceName("inbox")
            .receivedAt(java.time.Instant.EPOCH)
            .gs08("005010X221A1")
            .transactionType("835")
            .schemaId("schema:table:x12.005010X221A1.835")
            .rawX12("ISA*".getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .mappedJson("{}")
            .envelope(TransactionRow.ENVELOPE_FILE)
            .build();
    }
}

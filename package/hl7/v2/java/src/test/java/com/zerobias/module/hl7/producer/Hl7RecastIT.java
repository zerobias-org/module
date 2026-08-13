package com.zerobias.module.hl7.producer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zerobias.module.hl7.buffer.BufferRow;
import com.zerobias.module.hl7.buffer.BufferStore;
import com.zerobias.module.hl7.buffer.MessageStatus;
import com.zerobias.module.hl7.materializer.Materializer;
import com.zerobias.module.hl7.materializer.MaterializerRegistry;
import com.zerobias.module.hl7.materializer.StructureIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@code ops/recast} (DESIGN §2.5): re-materialize stored rows from their retained
 * raw ER7 under the current schema, driven through {@code FunctionsApi.invokeFunction}
 * on {@link OperationRouter} — the exact HTTP code path — over a real SQLite buffer
 * and the real v27 {@link Materializer}.
 *
 * <p>Seeds rows with a deliberately degraded {@code mapped_json} ("{}", as an
 * envelope-fallback or pre-extension cast would leave it) and asserts recast
 * upgrades exactly the rows that improve, is idempotent, skips leased rows, honors
 * the filter, and survives an un-parseable raw.
 */
class Hl7RecastIT {

    private static final Gson GSON = new Gson();
    private static final String CR = "\r";

    /** A well-formed ADT^A01 (structure ADT_A01) — full materialization yields msh/pid/pv1. */
    private static final String ADT_ER7 = String.join(CR,
        "MSH|^~\\&|EPIC|HOSP|RECV|DEST|20260529103000||ADT^A01^ADT_A01|MSG1|P|2.7",
        "EVN|A01|20260529103000",
        "PID|1||5551212^^^EPIC&&ISO^MR||SMITH^JOHN||19800101|M",
        "PV1|1|I") + CR;

    private BufferStore buffer;

    /** Facade wired with the real v27 materializer-backed {@link Recaster}. */
    private Hl7ProducerFacade facade(Path dir) throws Exception {
        StructureIndex index = StructureIndex.fromClasspath("v27");
        if (index == null) {
            String idxDir = System.getenv("HL7_INDEX_DIR");
            assumeTrue(idxDir != null && !idxDir.isBlank(),
                "no classpath structure-index and HL7_INDEX_DIR unset; skipping");
            index = StructureIndex.fromFile(Path.of(idxDir, "structure-index", "v27.json"));
        }
        Recaster recaster = new Recaster(MaterializerRegistry.pinned("v27", new Materializer(index)));

        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false);
        Files.createDirectories(dir.resolve("schemas"));
        SchemaRegistry schemas = SchemaRegistry.fromDirectory(dir);
        return new Hl7ProducerFacade(buffer, new ObjectTree(buffer, schemas, "v27"), schemas, recaster);
    }

    /** Insert a row carrying real raw ER7 but a degraded (empty) stored mapped_json. */
    private void insertDegraded(String cid, String rawEr7, MessageStatus status) throws Exception {
        buffer.insert(new BufferRow(0, Instant.parse("2026-05-28T10:00:00Z"), cid, "ADT_A01", "ADT", "A01",
            "EPIC", "HOSP", "2.7", "schema:table:hl7v2.v27.ADT_A01", rawEr7.getBytes(),
            "{}", status, null, null, null));
    }

    private JsonObject invoke(Hl7ProducerFacade f, String fn, Map<String, Object> input) throws Exception {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("objectId", "/hl7-v2-receiver/ops/" + fn);
        args.put("requestBody", input);
        return GSON.fromJson(OperationRouter.executeOperation(f, "FunctionsApi.invokeFunction", args),
            JsonObject.class);
    }

    private BufferRow row(String cid) throws Exception {
        return buffer.search("control_id = '" + cid + "'", 1).get(0);
    }

    @Test
    void recastUpgradesDegradedJson(@TempDir Path dir) throws Exception {
        Hl7ProducerFacade f = facade(dir);
        insertDegraded("M1", ADT_ER7, MessageStatus.NEW);
        insertDegraded("M2", ADT_ER7, MessageStatus.ACKED);   // acked rows are recast too

        JsonObject r = invoke(f, "recast", Map.of());
        assertEquals(2, r.get("examined").getAsInt());
        assertEquals(2, r.get("recast").getAsInt());
        assertEquals(0, r.get("unchanged").getAsInt());
        assertEquals(0, r.get("failed").getAsInt());

        // the degraded "{}" is replaced by the full typed body
        assertTrue(row("M1").mappedJson().contains("\"pid\""), "M1 upgraded: " + row("M1").mappedJson());
        assertTrue(row("M2").mappedJson().contains("\"pid\""), "M2 upgraded");
    }

    @Test
    void recastIsIdempotent(@TempDir Path dir) throws Exception {
        Hl7ProducerFacade f = facade(dir);
        insertDegraded("M1", ADT_ER7, MessageStatus.NEW);

        invoke(f, "recast", Map.of());                        // first pass upgrades it
        String afterFirst = row("M1").mappedJson();

        JsonObject second = invoke(f, "recast", Map.of());    // nothing left to improve
        assertEquals(1, second.get("examined").getAsInt());
        assertEquals(0, second.get("recast").getAsInt());
        assertEquals(1, second.get("unchanged").getAsInt());
        assertEquals(afterFirst, row("M1").mappedJson(), "unchanged row not rewritten");
    }

    @Test
    void recastSkipsInFlight(@TempDir Path dir) throws Exception {
        Hl7ProducerFacade f = facade(dir);
        insertDegraded("M1", ADT_ER7, MessageStatus.NEW);
        insertDegraded("M2", ADT_ER7, MessageStatus.NEW);
        buffer.takeWhere(null, 1, Duration.ofMinutes(5));     // lease one row -> in_flight

        JsonObject r = invoke(f, "recast", Map.of());
        assertEquals(1, r.get("examined").getAsInt(), "the leased row is excluded");
        assertEquals(1, r.get("recast").getAsInt());

        List<BufferRow> leased = buffer.search("status = 'in_flight'", 10);
        assertEquals(1, leased.size());
        assertEquals("{}", leased.get(0).mappedJson(), "leased row left untouched mid-flight");
    }

    @Test
    void recastHonorsFilter(@TempDir Path dir) throws Exception {
        Hl7ProducerFacade f = facade(dir);
        insertDegraded("M1", ADT_ER7, MessageStatus.NEW);
        // controlId is a denormalized envelope column, so the scope holds even while the
        // row's mapped_json is still the degraded "{}" (a body-path filter could not).
        JsonObject none = invoke(f, "recast", Map.of("filter", "(controlId=NOPE)"));
        assertEquals(0, none.get("examined").getAsInt());
        assertEquals(0, none.get("recast").getAsInt());
        assertEquals("{}", row("M1").mappedJson(), "filtered out, not recast");

        JsonObject match = invoke(f, "recast", Map.of("filter", "(controlId=M1)"));
        assertEquals(1, match.get("examined").getAsInt());
        assertEquals(1, match.get("recast").getAsInt());
    }

    @Test
    void recastCountsUnparseableRawAsFailedNotFatal(@TempDir Path dir) throws Exception {
        Hl7ProducerFacade f = facade(dir);
        insertDegraded("BAD", "this is not an HL7 message", MessageStatus.NEW);
        insertDegraded("M1", ADT_ER7, MessageStatus.NEW);

        JsonObject r = invoke(f, "recast", Map.of());
        assertEquals(2, r.get("examined").getAsInt());
        assertEquals(1, r.get("recast").getAsInt(), "the good row still upgrades");
        assertEquals(1, r.get("failed").getAsInt(), "the bad raw is counted, not thrown");
        assertEquals("{}", row("BAD").mappedJson(), "failed row left as-is");
    }

    @Test
    void recastUnavailableWithoutMaterializer(@TempDir Path dir) throws Exception {
        // A facade built without a Recaster (back-compat 3-arg ctor) rejects recast.
        buffer = new BufferStore(dir.resolve("buffer.db").toString(), false);
        Files.createDirectories(dir.resolve("schemas"));
        SchemaRegistry schemas = SchemaRegistry.fromDirectory(dir);
        Hl7ProducerFacade f = new Hl7ProducerFacade(buffer, new ObjectTree(buffer, schemas, "v27"), schemas);
        insertDegraded("M1", ADT_ER7, MessageStatus.NEW);

        ProducerException e = assertThrows(ProducerException.class, () -> invoke(f, "recast", Map.of()));
        assertEquals(400, e.httpStatus());
        assertEquals("err.unsupported.operation", e.key());
    }
}

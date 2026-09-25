package com.zerobias.module.x12.producer;

import com.zerobias.module.x12.ModuleRuntimeConfig;
import com.zerobias.module.x12.SourceConfig;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.RetentionConfig;
import com.zerobias.module.x12.buffer.TestRows;
import com.zerobias.module.x12.inbox.FileConsumer;
import com.zerobias.module.x12.materializer.StructureResolver;
import com.zerobias.module.x12.parser.Fixtures;
import com.zerobias.module.x12.parser.X12Parse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The buffer volume survives redeploys, so a new image opens a database an older image wrote.
 * Built from the frozen pre-graph schema ({@code transactions.mapped_json TEXT NOT NULL}): the
 * upgraded store must drop the column so ingest works, and the startup backfill must give the
 * legacy row a graph and dimensions from its {@code raw_x12}.
 */
class LegacyBufferUpgradeTest {

    @TempDir
    Path dir;

    @Test
    void upgradedBufferIngestsAndBackfillsLegacyRows() throws Exception {
        final String db = dir.resolve("buffer.db").toString();
        final String legacyKey = "/var/lib/x12/inbox/old.835@0a1b2c3d4e5f:101:0001";
        writeLegacyBuffer(db, legacyKey);

        final TestRows.MutableClock clock = new TestRows.MutableClock(TestRows.BASE);
        try (BufferStore buffer = new BufferStore(db, false, clock)) {
            assertFalse(columns(db).contains("mapped_json"), "legacy column dropped on open");
            assertEquals(1, userVersion(db));
            assertEquals(0, buffer.entityCount(legacyKey), "legacy row has no graph before backfill");

            final MaterializerRecastHook hook = new MaterializerRecastHook(new StructureResolver(), clock);
            final GraphBackfill.Result first = GraphBackfill.run(buffer, hook);
            assertEquals(new GraphBackfill.Result(1, 0, 0), first);
            assertTrue(buffer.entityCount(legacyKey) > 0, "graph rebuilt from raw_x12");
            assertTrue(buffer.dimsFor(legacyKey).containsKey("payerName"), "dimensions rebuilt too");
            assertTrue(buffer.documentFor(legacyKey).containsKey("header"), "body reassembles");
            assertEquals(new GraphBackfill.Result(0, 0, 0), GraphBackfill.run(buffer, hook), "idempotent");

            // The failure this guards: an upgraded container could not insert a transaction row.
            final Path inbox = Files.createDirectories(dir.resolve("inbox"));
            final ModuleRuntimeConfig cfg = new ModuleRuntimeConfig(
                List.of(new SourceConfig("inbox", inbox.toString(), "*", 1, 0)),
                ".done", ".error", false, RetentionConfig.none(), true, false);
            final FileConsumer consumer = new FileConsumer(buffer, null, cfg, new StructureResolver(), clock);
            final Path f = Files.write(inbox.resolve("claims.837"), Fixtures.bytes(Fixtures.F837P));
            final FileConsumer.Result r = consumer.consume(cfg.sources().get(0), f, TestRows.BASE);
            assertEquals(FileConsumer.Outcome.CONSUMED, r.outcome(), r.message());
            assertEquals(2, buffer.count());
        }

        // Reopening a migrated buffer is a no-op.
        try (BufferStore reopened = new BufferStore(db, false, clock)) {
            assertEquals(2, reopened.count());
            assertEquals(1, userVersion(db));
        }
    }

    /** A buffer as the pre-graph image left it: old DDL, one consumed 835 with a mapped_json body. */
    private static void writeLegacyBuffer(String db, String elementKey) throws Exception {
        final X12Parse.Transaction tx = X12Parse.parse(Fixtures.bytes(Fixtures.F835), false).transactions().get(0);
        final String ddl = new String(LegacyBufferUpgradeTest.class
            .getResourceAsStream("/buffer/schema-v0-mapped-json.sql").readAllBytes(), StandardCharsets.UTF_8);
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db)) {
            try (Statement st = c.createStatement()) {
                for (String stmt : ddl.replaceAll("(?m)--.*$", "").split(";")) {
                    if (!stmt.isBlank()) {
                        st.execute(stmt);
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO transactions (element_key, file_id, source_name, received_at, isa_control, "
                    + "gs_control, st_control, gs08, transaction_type, sender_id, receiver_id, schema_id, raw_x12, "
                    + "mapped_json) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                int i = 1;
                ps.setString(i++, elementKey);
                ps.setString(i++, elementKey.substring(0, elementKey.indexOf(":101:")));
                ps.setString(i++, "inbox");
                ps.setLong(i++, TestRows.BASE.toEpochMilli());
                ps.setString(i++, tx.interchange().controlNumber());
                ps.setString(i++, tx.group().controlNumber());
                ps.setString(i++, tx.st02());
                ps.setString(i++, "005010X221A1");
                ps.setString(i++, "835");
                ps.setString(i++, tx.interchange().senderId());
                ps.setString(i++, tx.interchange().receiverId());
                ps.setString(i++, TestRows.SCHEMA_835);
                ps.setBytes(i++, tx.rawX12().getBytes(StandardCharsets.UTF_8));
                ps.setString(i, "{\"header\":{}}");
                ps.executeUpdate();
            }
        }
    }

    private static List<String> columns(String db) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             ResultSet rs = c.createStatement().executeQuery("PRAGMA table_info(transactions)")) {
            final java.util.ArrayList<String> out = new java.util.ArrayList<>();
            while (rs.next()) {
                out.add(rs.getString("name"));
            }
            return out;
        }
    }

    private static int userVersion(String db) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             ResultSet rs = c.createStatement().executeQuery("PRAGMA user_version")) {
            return rs.getInt(1);
        }
    }
}

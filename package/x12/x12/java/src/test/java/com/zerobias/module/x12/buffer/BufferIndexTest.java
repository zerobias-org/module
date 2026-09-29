package com.zerobias.module.x12.buffer;

import com.zerobias.module.x12.buffer.TestRows.MutableClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static com.zerobias.module.x12.buffer.TestRows.BASE;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hot queries reach their rows through an index rather than a scan of the whole
 * {@code transactions} table (acked rows are most of it), and the indexes appear on a buffer
 * created before they existed without any migration step.
 */
class BufferIndexTest {

    private static final List<String> ADDED = List.of("transactions_unacked", "transactions_acked",
        "transactions_type", "transactions_gs08", "transactions_sender", "transactions_source");

    private static Connection side(Path dir) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("buffer.db"));
    }

    private static String plan(Path dir, String sql) throws SQLException {
        StringBuilder out = new StringBuilder();
        try (Connection c = side(dir); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("EXPLAIN QUERY PLAN " + sql)) {
            while (rs.next()) {
                out.append(rs.getString("detail")).append('\n');
            }
        }
        return out.toString();
    }

    private static List<String> indexes(Path dir) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection c = side(dir); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='index'")) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }

    @Test
    void hotQueriesUseAnIndex(@TempDir Path dir) throws Exception {
        try (BufferStore s = new BufferStore(dir.resolve("buffer.db").toString(), false, new MutableClock(BASE))) {
            String[][] cases = {
                {"SELECT min(received_at) FROM transactions WHERE status <> 'acked'", "transactions_unacked"},
                // take's candidate query (LeaseManager): equality probes on status, not a scan of acked rows
                {"SELECT id FROM transactions WHERE (status='new' OR (status='in_flight' "
                    + "AND in_flight_until < 0)) ORDER BY received_at ASC, id ASC LIMIT 10", "transactions_acked"},
                {"SELECT id, element_key FROM transactions WHERE status = 'acked' AND acked_at <= 0 "
                    + "ORDER BY acked_at ASC, id ASC LIMIT 500", "transactions_acked"},
                {"SELECT DISTINCT transaction_type FROM transactions WHERE transaction_type IS NOT NULL "
                    + "ORDER BY transaction_type", "transactions_type"},
                {"SELECT DISTINCT gs08 FROM transactions WHERE gs08 IS NOT NULL ORDER BY gs08", "transactions_gs08"},
                {"SELECT DISTINCT sender_id FROM transactions WHERE sender_id IS NOT NULL ORDER BY sender_id",
                    "transactions_sender"},
                {"SELECT DISTINCT source_name FROM transactions WHERE source_name IS NOT NULL ORDER BY source_name",
                    "transactions_source"},
            };
            for (String[] c : cases) {
                String p = plan(dir, c[0]);
                assertTrue(p.contains(c[1]), c[0] + "\n-> " + p);
                assertTrue(!p.contains("SCAN transactions\n"), "full table scan: " + c[0] + "\n-> " + p);
            }
        }
    }

    @Test
    void anExistingBufferGainsTheIndexesOnOpen(@TempDir Path dir) throws Exception {
        String db = dir.resolve("buffer.db").toString();
        new BufferStore(db, false, new MutableClock(BASE)).close();
        try (Connection c = side(dir); Statement st = c.createStatement()) {
            for (String idx : ADDED) {
                st.execute("DROP INDEX IF EXISTS " + idx);   // the shape a buffer written before them has
            }
        }
        new BufferStore(db, false, new MutableClock(BASE)).close();
        List<String> present = indexes(dir);
        assertTrue(present.containsAll(ADDED), "missing after reopen: " + present);
    }
}

package com.zerobias.module.x12.producer;

import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.TransactionRow;
import com.zerobias.module.x12.producer.mapping.EntityMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Startup backfill: re-derive the object graph and dimensions of every transaction row that
 * has none (DESIGN §8.4). Rows land here when they were buffered before the graph existed —
 * the {@code mapped_json} era — and the buffer migration dropped their only body. Their
 * {@code raw_x12} is intact, so the graph is rebuilt from it exactly as ingest would.
 *
 * <p>Runs before the pollers and routes start, so {@code ops/take} never drains a row whose
 * content is missing. A row that still has no graph afterwards (a guide with no materializer)
 * is legitimately envelope-only and is simply left alone; a row whose raw fails to parse is
 * logged and skipped — neither blocks startup.
 */
public final class GraphBackfill {

    private static final Logger LOG = LoggerFactory.getLogger(GraphBackfill.class);
    private static final int BATCH = 200;

    private GraphBackfill() {
    }

    /** Result counts, for the startup log and the tests. */
    public record Result(int rebuilt, int envelopeOnly, int failed) {
    }

    public static Result run(BufferStore buffer, RecastHook recaster) throws SQLException {
        if (!recaster.available()) {
            return new Result(0, 0, 0);
        }
        final Set<String> tried = new HashSet<>();
        int rebuilt = 0;
        int envelopeOnly = 0;
        int failed = 0;
        for (List<TransactionRow> batch = buffer.graphless(BATCH, tried); !batch.isEmpty();
                batch = buffer.graphless(BATCH, tried)) {
            for (TransactionRow row : batch) {
                tried.add(row.elementKey());
                final RecastHook.Mapping m;
                try {
                    m = recaster.rematerialize(row);
                } catch (Exception e) {
                    failed++;
                    LOG.warn("graph backfill: cannot re-materialize {}: {}", row.elementKey(), e.toString());
                    continue;
                }
                if (m.graph().isEmpty()) {
                    envelopeOnly++;
                    continue;
                }
                if (buffer.replaceGraph(row, m.schemaId(), m.graph(), EntityMapping.dimensions(row.gs08(), m.graph()))) {
                    rebuilt++;
                }
            }
        }
        if (rebuilt + envelopeOnly + failed > 0) {
            LOG.info("graph backfill: {} rebuilt from raw_x12, {} envelope-only (no materializer), {} failed",
                rebuilt, envelopeOnly, failed);
        }
        return new Result(rebuilt, envelopeOnly, failed);
    }
}

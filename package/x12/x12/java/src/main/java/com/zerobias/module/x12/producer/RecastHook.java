package com.zerobias.module.x12.producer;

import com.zerobias.module.x12.buffer.TransactionRow;
import com.zerobias.module.x12.materializer.EntityGraph;

import java.util.List;
import java.util.Map;

/**
 * Re-materialization of a buffered row from its stored {@code raw_x12} under the
 * currently-loaded definitions, for {@code ops/recast}, {@code ops/validate} (DESIGN §2.5) and
 * the startup {@link GraphBackfill}. {@link MaterializerRecastHook} is the implementation. This
 * stays an interface because the real hook reproduces every row it ingested, so the rewrite
 * path of {@code recast} is only reachable through a hook whose output differs from the store.
 */
@FunctionalInterface
public interface RecastHook {

    /**
     * A re-derived mapping: the schema id, the typed body and its object graph the current
     * definitions produce, and the non-fatal parser errors imsweb reported on the way
     * ({@code validate.parserErrors}).
     */
    record Mapping(String schemaId, Map<String, Object> body,
            List<EntityGraph.Entity> graph, List<String> parserErrors) {

        public Mapping {
            parserErrors = parserErrors == null ? List.of() : List.copyOf(parserErrors);
            graph = graph == null ? List.of() : List.copyOf(graph);
        }

        /**
         * True when the current definitions reproduce what the buffer already holds — compared
         * against the document reassembled from the stored graph, since that IS the stored
         * representation (DESIGN §8.4).
         */
        public boolean reproduces(TransactionRow row, Map<String, Object> storedBody) {
            return schemaId != null && schemaId.equals(row.schemaId())
                && body != null && body.equals(storedBody);
        }
    }

    /**
     * Re-materialize {@code row} from its raw bytes. Throws on an un-parseable raw, which the
     * callers count as {@code failed} rather than treat as fatal.
     */
    Mapping rematerialize(TransactionRow row) throws Exception;
}

package com.zerobias.module.x12.producer;

import com.zerobias.module.x12.buffer.TransactionRow;
import com.zerobias.module.x12.materializer.EntityGraph;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ==== MATERIALIZER SEAM ====
 * Re-materialization of a buffered row from its stored {@code raw_x12} under the
 * currently-loaded definitions, for {@code ops/recast} and {@code ops/validate}
 * (DESIGN §2.5). {@link MaterializerRecastHook} is the production implementation
 * (parse {@code raw_x12} with imsweb → {@code Materializer} → tree + object graph); {@link #NONE}
 * is the degrade: {@code recast} rewrites nothing and says so, {@code validate} reports
 * {@code rematerialized: null}.
 */
public interface RecastHook {

    /**
     * A re-derived mapping: the schema id + typed JSON the current definitions produce,
     * and the non-fatal parser errors imsweb reported on the way ({@code validate.parserErrors}).
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
         * representation now (DESIGN §8.4).
         */
        public boolean reproduces(TransactionRow row, Map<String, Object> storedBody) {
            return schemaId != null && schemaId.equals(row.schemaId())
                && body != null && body.equals(storedBody);
        }
    }

    /**
     * Re-materialize {@code row} from its raw bytes. Returns the new mapping, or empty
     * when the current definitions reproduce the stored mapping exactly (nothing to
     * rewrite). Throws on an un-parseable raw (counted as {@code failed}, never fatal).
     */
    Optional<Mapping> recast(TransactionRow row) throws Exception;

    /**
     * Re-materialize {@code row} unconditionally (for {@code validate}: the
     * {@code rematerialized} verdict + {@code repsAgree}). Default derives from
     * {@link #recast}: empty there means "identical to stored".
     */
    default Mapping rematerialize(TransactionRow row) throws Exception {
        return recast(row).orElse(new Mapping(row.schemaId(), Map.of(), List.of(), List.of()));
    }

    /**
     * Whether {@code mapping} reproduces what the buffer holds for {@code row}, given the
     * document reassembled from its stored graph.
     */
    default boolean reproduces(Mapping mapping, TransactionRow row, Map<String, Object> stored) {
        return mapping.reproduces(row, stored);
    }

    /** True when a real materializer is behind this hook. */
    default boolean available() {
        return true;
    }

    /** No materializer configured: nothing is ever recast. */
    RecastHook NONE = new RecastHook() {
        @Override
        public Optional<Mapping> recast(TransactionRow row) {
            return Optional.empty();
        }

        @Override
        public boolean available() {
            return false;
        }
    };
}

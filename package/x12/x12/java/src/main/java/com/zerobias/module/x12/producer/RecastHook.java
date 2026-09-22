package com.zerobias.module.x12.producer;

import com.zerobias.module.x12.buffer.TransactionRow;

import java.util.List;
import java.util.Optional;

/**
 * ==== MATERIALIZER SEAM ====
 * Re-materialization of a buffered row from its stored {@code raw_x12} under the
 * currently-loaded definitions, for {@code ops/recast} and {@code ops/validate}
 * (DESIGN §2.5). {@link MaterializerRecastHook} is the production implementation
 * (parse {@code raw_x12} with imsweb → {@code Materializer} → typed JSON); {@link #NONE}
 * is the degrade: {@code recast} rewrites nothing and says so, {@code validate} reports
 * {@code rematerialized: null}.
 */
public interface RecastHook {

    /**
     * A re-derived mapping: the schema id + typed JSON the current definitions produce,
     * and the non-fatal parser errors imsweb reported on the way ({@code validate.parserErrors}).
     */
    record Mapping(String schemaId, String mappedJson, List<String> parserErrors) {

        public Mapping {
            parserErrors = parserErrors == null ? List.of() : List.copyOf(parserErrors);
        }

        public Mapping(String schemaId, String mappedJson) {
            this(schemaId, mappedJson, List.of());
        }

        /** True when this mapping is byte-identical to what {@code row} stores. */
        public boolean reproduces(TransactionRow row) {
            return schemaId != null && schemaId.equals(row.schemaId())
                && mappedJson != null && mappedJson.equals(row.mappedJson());
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
        return recast(row).orElse(new Mapping(row.schemaId(), row.mappedJson()));
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

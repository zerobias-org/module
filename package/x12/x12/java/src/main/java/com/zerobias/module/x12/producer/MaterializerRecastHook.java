package com.zerobias.module.x12.producer;

import com.zerobias.module.x12.buffer.TransactionRow;
import com.zerobias.module.x12.materializer.EntityGraph;
import com.zerobias.module.x12.materializer.Materializer;
import com.zerobias.module.x12.materializer.StructureResolver;
import com.zerobias.module.x12.parser.X12Parse;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@link RecastHook}: re-parses a row's stored {@code raw_x12} (a complete
 * single-transaction ISA…IEA interchange, see {@link X12Parse.Transaction#rawX12()}) with
 * imsweb and materializes it through the {@link StructureResolver}'s current index for the
 * set's own GS08 — the same materializer and flattening the ingest path uses — so a row that
 * ingested under the same definitions reproduces exactly ({@code repsAgree}) and
 * {@code ops/recast} rewrites only rows whose graph actually changed. The envelope is not part
 * of the result: it is overlaid from the row's columns at read time.
 */
public final class MaterializerRecastHook implements RecastHook {

    private final StructureResolver resolver;
    private final Clock clock;

    public MaterializerRecastHook(StructureResolver resolver, Clock clock) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Mapping rematerialize(TransactionRow row) throws Exception {
        byte[] raw = row.rawX12();
        if (raw == null || raw.length == 0) {
            throw new IllegalStateException("raw_x12 is empty for " + row.elementKey());
        }
        // Bare ST rows cannot exist (the raw always carries an envelope), but tolerate one.
        X12Parse.ParsedFile parsed = X12Parse.parse(raw, true, clock);
        X12Parse.Transaction tx = select(parsed, row);
        Optional<Materializer> materializer = resolver.materializerFor(tx.gs08(), parsed.separators());
        if (materializer.isEmpty()) {
            return new Mapping(StructureResolver.ENVELOPE_SCHEMA, new LinkedHashMap<>(), List.of(), parsed.errors());
        }
        final Materializer m = materializer.get();
        final Map<String, Object> body = m.materializeTransaction(tx.loop());
        return new Mapping(m.index().tableSchemaId, body, EntityGraph.flatten(m.index(), body), parsed.errors());
    }

    /** The transaction set the row describes: by (GS06, ST02) when the raw carries several, else the only one. */
    private static X12Parse.Transaction select(X12Parse.ParsedFile parsed, TransactionRow row) {
        X12Parse.Transaction first = null;
        for (X12Parse.Transaction tx : parsed.transactions()) {
            if (first == null) {
                first = tx;
            }
            if (tx.st02().equals(row.stControl())
                    && (row.gsControl() == null || row.gsControl().equals(tx.group().controlNumber()))) {
                return tx;
            }
        }
        if (first == null) {
            throw new IllegalStateException("raw_x12 holds no transaction set for " + row.elementKey());
        }
        return first;
    }
}

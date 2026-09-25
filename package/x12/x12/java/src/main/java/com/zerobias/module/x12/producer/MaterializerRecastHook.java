package com.zerobias.module.x12.producer;

import com.zerobias.module.x12.buffer.FileRow;
import com.zerobias.module.x12.buffer.TransactionRow;
import com.zerobias.module.x12.materializer.Materializer;
import com.zerobias.module.x12.materializer.StructureResolver;
import com.zerobias.module.x12.materializer.TransactionJson;
import com.zerobias.module.x12.parser.X12Parse;

import java.time.Clock;
import java.util.Optional;

/**
 * The production {@link RecastHook}: re-parses a row's stored {@code raw_x12} (a complete
 * single-transaction ISA…IEA interchange, see {@link X12Parse.Transaction#rawX12()}) with
 * imsweb, materializes it through the {@link StructureResolver}'s current index for its
 * GS08, and rebuilds the stored JSON with {@link TransactionJson} — the same builder the
 * ingest path uses, with the row's own envelope columns as the overlay — so a row that
 * ingested under the same definitions reproduces byte-for-byte ({@code repsAgree}) and
 * {@code ops/recast} rewrites only rows whose JSON actually changed.
 */
public final class MaterializerRecastHook implements RecastHook {

    private final StructureResolver resolver;
    private final Clock clock;

    public MaterializerRecastHook(StructureResolver resolver, Clock clock) {
        this.resolver = resolver == null ? new StructureResolver() : resolver;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public Optional<Mapping> recast(TransactionRow row) throws Exception {
        return Optional.of(rematerialize(row));
    }

    /**
     * Whether a re-derived mapping matches what the buffer holds. The caller supplies the
     * stored document (reassembled from the graph), because the hook has no buffer.
     */
    @Override
    public boolean reproduces(Mapping mapping, TransactionRow row, java.util.Map<String, Object> stored) {
        return mapping.reproduces(row, stored);
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
        Optional<Materializer> materializer = resolver.materializerFor(parsed.gs08(), parsed.separators());
        String schemaId = materializer.map(m -> m.index().tableSchemaId).orElse(StructureResolver.ENVELOPE_SCHEMA);
        TransactionJson.Envelope env = new TransactionJson.Envelope(row.elementKey(), row.fileId(),
            FileRow.fileNameOf(row.fileId()), row.sourceName(), row.isaControl(), row.gsControl(), row.stControl(),
            row.gs08(), row.transactionType(), row.senderId(), row.receiverId(), row.interchangeAt(),
            row.receivedAt(), row.envelope(), row.parserErrorCount());
        // The materialized tree IS the content; the graph is how it is stored (DESIGN §8.4).
        // Flattened here because only the hook holds the structure index it needs.
        final java.util.Map<String, Object> body = materializer.isPresent()
            ? materializer.get().materializeTransaction(tx.loop())
            : new java.util.LinkedHashMap<>();
        final java.util.List<com.zerobias.module.x12.materializer.EntityGraph.Entity> graph =
            materializer.isPresent()
                ? com.zerobias.module.x12.materializer.EntityGraph.flatten(materializer.get().index(), body)
                : java.util.List.of();
        // The envelope overlay is applied at read time by the facade, not stored.
        assert env != null;
        return new Mapping(schemaId, body, graph, parsed.errors());
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

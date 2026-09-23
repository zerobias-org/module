package com.zerobias.module.x12.materializer;

import com.zerobias.module.x12.parser.TransactionTypes;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * GS08 → {@link StructureIndex} (DESIGN §5): the guide-bound index when the classpath has
 * one, else the envelope-only degrade — the transaction is stored under
 * {@link #ENVELOPE_SCHEMA} with just its envelope fields and {@code parserErrorCount}, so
 * nothing is lost and the row is still drainable and browsable. Indexes are loaded once
 * and cached; a miss is cached too (the classpath does not change at runtime).
 */
public final class StructureResolver {

    public static final String ENVELOPE_SCHEMA = "schema:shared:x12.transaction-envelope";

    private final Map<String, Optional<StructureIndex>> cache = new ConcurrentHashMap<>();
    private final Function<String, Optional<StructureIndex>> loader;

    /** Classpath-backed (production). */
    public StructureResolver() {
        this(StructureIndex::fromClasspath);
    }

    /** Custom loader (tests, or a future extension pack). */
    public StructureResolver(Function<String, Optional<StructureIndex>> loader) {
        this.loader = loader;
    }

    /** A resolver that finds nothing: every transaction takes the envelope-only degrade. */
    public static StructureResolver none() {
        return new StructureResolver(gs08 -> Optional.empty());
    }

    public Optional<StructureIndex> resolve(String gs08) {
        if (gs08 == null || gs08.isBlank()) {
            return Optional.empty();
        }
        String key = TransactionTypes.canonical(gs08).orElse(gs08.trim());
        return cache.computeIfAbsent(key, loader);
    }

    /** {@code schema:table:x12.<GS08>.<TS>} when an index exists, else the shared envelope id. */
    public String schemaIdFor(String gs08) {
        return resolve(gs08).map(i -> i.tableSchemaId).orElse(ENVELOPE_SCHEMA);
    }

    /** A materializer for the guide, or empty for the envelope-only degrade. */
    public Optional<Materializer> materializerFor(String gs08, com.zerobias.module.x12.parser.Separators separators) {
        return resolve(gs08).map(i -> new Materializer(i, separators));
    }
}

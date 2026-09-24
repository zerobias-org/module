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

    /** Indexes from {@code loader} (GS08 → index, empty for the envelope-only degrade) instead of the classpath. */
    public StructureResolver(Function<String, Optional<StructureIndex>> loader) {
        this.loader = loader;
    }

    public Optional<StructureIndex> resolve(String gs08) {
        if (gs08 == null || gs08.isBlank()) {
            return Optional.empty();
        }
        String key = TransactionTypes.canonical(gs08).orElse(gs08.trim());
        return cache.computeIfAbsent(key, loader);
    }

    /** A materializer for the guide, or empty for the envelope-only degrade. */
    public Optional<Materializer> materializerFor(String gs08, com.zerobias.module.x12.parser.Separators separators) {
        return resolve(gs08).map(i -> new Materializer(i, separators));
    }
}

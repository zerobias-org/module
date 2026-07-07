package com.zerobias.module.hl7.producer;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.GenericModelClassFactory;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.validation.impl.ValidationContextFactory;
import com.zerobias.module.hl7.buffer.BufferRow;
import com.zerobias.module.hl7.materializer.MaterializerRegistry;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Re-derives a stored message's typed JSON from its retained raw ER7 using the
 * <em>current</em> {@link MaterializerRegistry} (DESIGN §5). This is what lets
 * {@code ops/recast} upgrade rows that were first cast under a poorer schema — the
 * envelope fallback, an unknown structure, or before a version slot / extension pack
 * was bundled — once a better schema ships in a new image.
 *
 * <p>Routing is per-row by the message's own stored HL7 version (MSH-12), exactly as
 * the receive path routes at ingest, so recast re-materializes each row against the
 * best schema now bundled <em>for that version</em> and re-labels its schema id the
 * same way {@code BufferingApp} does — including the envelope-fallback id when the
 * row's version still isn't bundled.
 *
 * <p>Re-parsing mirrors ingest: generic parsing (no typed HAPI structure jars,
 * DESIGN §4.1) with validation disabled, so the canonical {@code raw_er7} round-trips
 * to the {@link Message} the materializer expects.
 */
public final class Recaster {

    /** The recomputed persisted fields for a row. */
    public record Mapping(String schemaId, String mappedJson) {
    }

    private final MaterializerRegistry materializers;
    private final PipeParser parser;

    public Recaster(MaterializerRegistry materializers) {
        this.materializers = materializers;
        // A parser-only context (never a server), so it is immune to the shared-executor
        // shutdown footgun that forces Hl7ListenerService to own a dedicated executor —
        // PipeParser.parse is synchronous and never touches the executor service.
        HapiContext ctx = new DefaultHapiContext();
        ctx.setValidationContext(ValidationContextFactory.noValidation());
        ctx.setModelClassFactory(new GenericModelClassFactory());
        this.parser = ctx.getPipeParser();
    }

    /**
     * Re-materialize {@code row} from its raw ER7 under the current registry, routing
     * by the row's stored HL7 version. Returns the new mapping when it differs from
     * what is stored, or empty when re-materialization reproduces the stored value
     * (nothing to upgrade — the "only if improved" contract). Throws when the raw
     * can't be parsed/materialized.
     *
     * <p>{@code synchronized}: a single {@link PipeParser} is reused across calls, and
     * HAPI parsers are not guaranteed thread-safe, so concurrent recast invocations
     * serialize here (an admin op, not a hot path).
     */
    public synchronized Optional<Mapping> recast(BufferRow row) throws HL7Exception {
        Message message = parser.parse(new String(row.rawEr7(), StandardCharsets.UTF_8));
        String version = row.hl7Version();
        String schemaId = materializers.schemaIdFor(version, row.messageStructure());
        String mappedJson = materializers.materializerFor(version).toTypedJson(message);
        if (mappedJson.equals(row.mappedJson()) && schemaId.equals(row.schemaId())) {
            return Optional.empty();
        }
        return Optional.of(new Mapping(schemaId, mappedJson));
    }
}

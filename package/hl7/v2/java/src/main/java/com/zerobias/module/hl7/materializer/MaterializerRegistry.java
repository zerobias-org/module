package com.zerobias.module.hl7.materializer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chooses the materializer and collection schema id for each inbound message by
 * its declared HL7 version (MSH-12), so one listener can serve an emergent,
 * mixed-version feed (DESIGN §5 / §11.5).
 *
 * <p>A version maps to a slot the same way everywhere: {@code "2.4" -> "v24"},
 * {@code "2.5.1" -> "v251"}. Each slot's {@link StructureIndex} is loaded from the
 * classpath on first use and cached; a version whose slot isn't baked into the
 * image degrades to generic {@link EnvelopeMaterializer} + the shared message
 * envelope schema — the message is still buffered, labelled with its true version,
 * and searchable. Never drops on an unseen version.
 *
 * <p>Extensions (DESIGN §7) apply to the configured default slot only; other slots
 * load plain. A {@code pinned} registry (config-fixed deployments / tests) ignores
 * MSH-12 and routes everything through one materializer.
 */
public final class MaterializerRegistry {

    /** Heterogeneous / unbundled fallback schema (DESIGN §2.2). */
    public static final String ENVELOPE_SCHEMA = "schema:shared:hl7v2.message-envelope";

    private final String defaultSlot;
    private final boolean routeByVersion;
    private final MessageMaterializer generic = new EnvelopeMaterializer();
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    private record Entry(MessageMaterializer materializer, boolean typed) {
    }

    private MaterializerRegistry(String defaultSlot, MessageMaterializer defaultMaterializer,
            boolean defaultTyped, boolean routeByVersion) {
        this.defaultSlot = defaultSlot;
        this.routeByVersion = routeByVersion;
        cache.put(defaultSlot, new Entry(defaultMaterializer, defaultTyped));
    }

    /**
     * Per-message routing: the configured slot uses {@code defaultMaterializer}
     * (extension-merged); any other version's slot is loaded lazily from the classpath.
     */
    public static MaterializerRegistry routing(String defaultSlot,
            MessageMaterializer defaultMaterializer, boolean defaultTyped) {
        return new MaterializerRegistry(defaultSlot, defaultMaterializer, defaultTyped, true);
    }

    /** Fixed-slot registry: every message uses one materializer, MSH-12 ignored. */
    public static MaterializerRegistry pinned(String slot, MessageMaterializer materializer) {
        return new MaterializerRegistry(slot, materializer, materializer instanceof Materializer, false);
    }

    /** HAPI structure slot for an MSH-12 version string ({@code "2.4" -> "v24"}). */
    public String slotFor(String hl7Version) {
        if (!routeByVersion || hl7Version == null || hl7Version.isBlank()) {
            return defaultSlot;
        }
        return "v" + hl7Version.replace(".", "");
    }

    /** The materializer for a message of the given HL7 version (generic if the slot isn't bundled). */
    public MessageMaterializer materializerFor(String hl7Version) {
        return entryFor(hl7Version).materializer();
    }

    /**
     * The collection schema id for {@code structure} at the message's version: the
     * version-scoped table schema when the slot is bundled, else the shared envelope
     * (so unbundled-version rows still resolve to a real schema).
     */
    public String schemaIdFor(String hl7Version, String structure) {
        Entry e = entryFor(hl7Version);
        return e.typed()
            ? "schema:table:hl7v2." + slotFor(hl7Version) + "." + structure
            : ENVELOPE_SCHEMA;
    }

    private Entry entryFor(String hl7Version) {
        return cache.computeIfAbsent(slotFor(hl7Version), this::load);
    }

    private Entry load(String slot) {
        StructureIndex index = StructureIndex.fromClasspath(slot);
        return index == null ? new Entry(generic, false) : new Entry(new Materializer(index), true);
    }
}

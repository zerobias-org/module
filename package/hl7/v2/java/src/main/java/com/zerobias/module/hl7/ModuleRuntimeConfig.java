package com.zerobias.module.hl7;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zerobias.module.hl7.buffer.RetentionConfig;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The opaque, module-defined runtime config the platform transports verbatim as
 * the {@code MODULE_CONFIG} env (DESIGN §3.2, post-2026-06-01 review). The
 * platform never parses it — this module owns its shape:
 *
 * <pre>
 * { "activeExtensions": ["epic-adt"],
 *   "hl7Version": "2.7",
 *   "ackDurability": "full",
 *   "retention": { "maxBytes": 10737418240, "maxAge": "P7D" } }
 * </pre>
 *
 * <p>These are <em>daemon/container-level</em> knobs: they configure the buffer
 * and listener at boot, before any connection exists, so they travel here (the
 * only channel the platform delivers to the running module) rather than in the
 * per-connection {@code connectionProfile}. Absent/blank/malformed
 * {@code MODULE_CONFIG} → safe defaults (no extension filter, {@code normal}
 * durability, no retention) rather than a boot crash.
 */
public record ModuleRuntimeConfig(
        Set<String> activeExtensions,
        boolean fullDurability,
        RetentionConfig retention,
        String hl7Version) {

    private static final Gson GSON = new Gson();
    /** Target HL7 version when MODULE_CONFIG omits it (the version this image bakes). */
    static final String DEFAULT_HL7_VERSION = "2.7";

    private static ModuleRuntimeConfig defaults() {
        return new ModuleRuntimeConfig(
            new LinkedHashSet<>(), false, RetentionConfig.none(), DEFAULT_HL7_VERSION);
    }

    /**
     * HAPI structure package / structure-index slot for {@link #hl7Version()} —
     * {@code "2.7" -> "v27"}, {@code "2.5.1" -> "v251"} (HAPI's
     * {@code ca.uhn.hl7v2.model.vNN} naming). If the configured version's index
     * isn't baked into the image, the runtime degrades to the envelope schema.
     */
    public String versionSlot() {
        return "v" + hl7Version.replace(".", "");
    }

    public static ModuleRuntimeConfig fromEnv() {
        return parse(System.getenv("MODULE_CONFIG"));
    }

    static ModuleRuntimeConfig parse(String json) {
        if (json == null || json.isBlank()) {
            return defaults();
        }
        try {
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj == null) {
                return defaults();
            }
            Set<String> active = new LinkedHashSet<>();
            if (obj.has("activeExtensions") && obj.get("activeExtensions").isJsonArray()) {
                for (JsonElement e : obj.getAsJsonArray("activeExtensions")) {
                    active.add(e.getAsString());
                }
            }
            boolean full = obj.has("ackDurability")
                && obj.get("ackDurability").isJsonPrimitive()
                && "full".equalsIgnoreCase(obj.get("ackDurability").getAsString());
            String version = (obj.has("hl7Version") && obj.get("hl7Version").isJsonPrimitive()
                && !obj.get("hl7Version").getAsString().isBlank())
                ? obj.get("hl7Version").getAsString() : DEFAULT_HL7_VERSION;
            return new ModuleRuntimeConfig(active, full, parseRetention(obj), version);
        } catch (RuntimeException malformed) {
            // Malformed MODULE_CONFIG (bad JSON, wrong-typed/garbage fields) → safe
            // defaults rather than crashing the daemon at boot. The platform transports
            // this opaque; a deploy with a typo must not wedge an always-on receiver.
            return defaults();
        }
    }

    private static RetentionConfig parseRetention(JsonObject obj) {
        if (!obj.has("retention") || !obj.get("retention").isJsonObject()) {
            return RetentionConfig.none();
        }
        JsonObject r = obj.getAsJsonObject("retention");
        Long maxBytes = (r.has("maxBytes") && r.get("maxBytes").isJsonPrimitive()
                && r.getAsJsonPrimitive("maxBytes").isNumber())
            ? r.get("maxBytes").getAsLong() : null;
        Duration maxAge = null;
        if (r.has("maxAge") && r.get("maxAge").isJsonPrimitive()) {
            try {
                maxAge = Duration.parse(r.get("maxAge").getAsString());
            } catch (java.time.format.DateTimeParseException badDuration) {
                // A bad maxAge disables age-based eviction only; it must not discard
                // maxBytes or the rest of the config.
                maxAge = null;
            }
        }
        return new RetentionConfig(maxAge, maxBytes);
    }
}

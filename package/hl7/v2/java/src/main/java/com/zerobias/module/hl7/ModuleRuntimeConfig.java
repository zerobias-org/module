package com.zerobias.module.hl7;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The opaque, module-defined runtime config the platform transports verbatim as
 * the {@code MODULE_CONFIG} env (DESIGN §3.2, post-2026-06-01 review). The
 * platform never parses it — this module owns its shape:
 *
 * <pre>{ "activeExtensions": ["epic-adt"], "hl7Version": "2.5.1" }</pre>
 *
 * Absent/blank {@code MODULE_CONFIG} → no active-extension filter (all baked-in
 * packs load).
 */
public record ModuleRuntimeConfig(Set<String> activeExtensions) {

    private static final Gson GSON = new Gson();

    public static ModuleRuntimeConfig fromEnv() {
        return parse(System.getenv("MODULE_CONFIG"));
    }

    static ModuleRuntimeConfig parse(String json) {
        Set<String> active = new LinkedHashSet<>();
        if (json != null && !json.isBlank()) {
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj != null && obj.has("activeExtensions") && obj.get("activeExtensions").isJsonArray()) {
                JsonArray arr = obj.getAsJsonArray("activeExtensions");
                for (JsonElement e : arr) {
                    active.add(e.getAsString());
                }
            }
        }
        return new ModuleRuntimeConfig(active);
    }
}

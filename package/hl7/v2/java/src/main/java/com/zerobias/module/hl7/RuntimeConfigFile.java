package com.zerobias.module.hl7;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The canonical runtime config the Hub Node delivers to the container as a file —
 * the hydra {@code DeploymentRuntimeConfig}, serialized (one schema, one source of
 * truth; the node never invents a parallel shape). The env var
 * {@code RUNTIME_CONFIG_FILE} points at it (the node sets it to e.g.
 * {@code /etc/zerobias/runtime.json}).
 *
 * <p>The node fills {@code listenerPorts[].port} with the ports it actually
 * allocated and carries {@code listenerPorts[].name} <em>verbatim</em> (no env-key
 * normalization), so this file is the collision-safe source of listener identity
 * for the many-named-ports model.
 *
 * <p>We deliberately read <em>only</em> {@code listenerPorts} here. The opaque
 * {@code config} blob stays sourced from the {@code MODULE_CONFIG} env var, which the
 * node keeps emitting on every node version (back-compat is permanent) and which is
 * therefore present whether or not this file is — see {@link ModuleRuntimeConfig}.
 *
 * <p>This is a hand-rolled Gson read of the canonical {@code DeploymentRuntimeConfig}
 * shape (the same wire keys the schema authors in hydra), not a redefinition of it:
 * the module is a Java daemon and cannot import the TypeScript {@code hydra-core}
 * client, so it binds to the schema's shape directly, exactly as it already does for
 * {@code MODULE_CONFIG}.
 *
 * <p>An absent/unreadable/garbage file yields {@link Optional#empty()} so the caller
 * falls back to {@code LISTENER_PORT_*} env scanning on older nodes — never a boot
 * crash from a malformed deploy.
 */
public final class RuntimeConfigFile {

    private static final Logger LOG = LoggerFactory.getLogger(RuntimeConfigFile.class);
    private static final Gson GSON = new Gson();

    private final List<ListenerSpec> listeners;

    private RuntimeConfigFile(List<ListenerSpec> listeners) {
        this.listeners = List.copyOf(listeners);
    }

    /** Listener ports declared by the file, in document order (verbatim names, resolved ports). */
    public List<ListenerSpec> listeners() {
        return listeners;
    }

    /**
     * Load via the {@code RUNTIME_CONFIG_FILE} pointer. Empty when the pointer is
     * unset (older node), or the file is missing/unreadable/unparseable — the caller
     * then falls back to env.
     */
    public static Optional<RuntimeConfigFile> load() {
        final String pointer = System.getenv("RUNTIME_CONFIG_FILE");
        if (pointer == null || pointer.isBlank()) {
            return Optional.empty();
        }
        final Path path = Path.of(pointer.trim());
        if (!Files.isReadable(path)) {
            LOG.warn("RUNTIME_CONFIG_FILE={} not readable; falling back to LISTENER_PORT_* env", pointer);
            return Optional.empty();
        }
        try {
            RuntimeConfigFile parsed = parse(Files.readString(path));
            LOG.info("Loaded runtime config file {} ({} listener port(s))", path, parsed.listeners.size());
            return Optional.of(parsed);
        } catch (Exception e) {
            LOG.warn("RUNTIME_CONFIG_FILE={} unreadable/unparseable ({}); falling back to env",
                pointer, e.toString());
            return Optional.empty();
        }
    }

    /**
     * Parse a {@code DeploymentRuntimeConfig} JSON document into listener specs.
     * Testable seam; never throws on shape problems — malformed entries are skipped.
     */
    static RuntimeConfigFile parse(String json) {
        final List<ListenerSpec> out = new ArrayList<>();
        final JsonElement rootEl = GSON.fromJson(json, JsonElement.class);
        final JsonObject root = (rootEl != null && rootEl.isJsonObject()) ? rootEl.getAsJsonObject() : null;
        if (root != null && root.has("listenerPorts") && root.get("listenerPorts").isJsonArray()) {
            final JsonArray arr = root.getAsJsonArray("listenerPorts");
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) {
                    continue;
                }
                final JsonObject lp = el.getAsJsonObject();
                final String name = (lp.has("name") && lp.get("name").isJsonPrimitive())
                    ? lp.get("name").getAsString() : null;
                // Resolved form: port is a non-null number. Unresolved (null) or
                // garbage entries are skipped — we only bind ports the node committed.
                final Integer port = (lp.has("port") && lp.get("port").isJsonPrimitive()
                    && lp.getAsJsonPrimitive("port").isNumber())
                    ? lp.get("port").getAsInt() : null;
                if (name == null || name.isBlank() || port == null) {
                    LOG.warn("skipping listenerPort entry with missing name/resolved port: {}", lp);
                    continue;
                }
                out.add(new ListenerSpec(name, port));
            }
        }
        return new RuntimeConfigFile(out);
    }
}

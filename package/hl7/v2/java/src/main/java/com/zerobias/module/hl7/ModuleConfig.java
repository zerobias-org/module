package com.zerobias.module.hl7;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Environment-driven module configuration (DESIGN §3.2).
 *
 * <p>The daemon binds one MLLP listener per declared {@link ListenerSpec}. At least
 * one is mandatory and there is no default — a daemon listener with no port is a
 * misconfiguration, not a fallback case.
 *
 * <p>Listener ports are resolved <b>file-first</b>: when the Hub Node delivers the
 * canonical {@link RuntimeConfigFile} (hydra {@code DeploymentRuntimeConfig}), its
 * {@code listenerPorts} are authoritative — verbatim names, resolved ports,
 * collision-safe. On an older node with no file, we fall back to scanning
 * {@code LISTENER_PORT_<NAME>} env vars (lossy names, but back-compatible).
 */
public record ModuleConfig(int internalPort, List<ListenerSpec> listeners, String extensionDir) {

    private static final int DEFAULT_INTERNAL_PORT = 8889;
    private static final String DEFAULT_EXTENSION_DIR = "/opt/module/extensions";
    static final String ENV_PREFIX = "LISTENER_PORT_";
    /** The optional env index var (a list, not a port) — must not be read as a listener. */
    static final String ENV_INDEX = "LISTENER_PORTS";

    public static ModuleConfig fromEnv() {
        return resolve(RuntimeConfigFile.load(), System.getenv());
    }

    /**
     * Resolve listeners file-first, env-fallback. Testable seam: callers inject the
     * loaded file (or empty) and the env map, with no process-env/filesystem access.
     */
    static ModuleConfig resolve(Optional<RuntimeConfigFile> file, Map<String, String> env) {
        final List<ListenerSpec> listeners = file
            .map(RuntimeConfigFile::listeners)
            .filter(l -> !l.isEmpty())
            .orElseGet(() -> listenersFromEnv(env));
        return build(env.get("INTERNAL_PORT"), listeners, env.get("EXTENSION_DIR"));
    }

    /**
     * Collect {@code LISTENER_PORT_<NAME>} vars (old-node fallback). The listener name
     * is the lowercased suffix; the index var {@code LISTENER_PORTS} is skipped (it is
     * a name list, not a port). Sorted for deterministic logs/self-test order.
     */
    static List<ListenerSpec> listenersFromEnv(Map<String, String> env) {
        final Map<String, String> sorted = new TreeMap<>(env);
        final List<ListenerSpec> out = new ArrayList<>();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            final String key = e.getKey();
            if (!key.startsWith(ENV_PREFIX) || key.equals(ENV_INDEX)) {
                continue;
            }
            final String name = key.substring(ENV_PREFIX.length()).toLowerCase();
            if (name.isBlank()) {
                continue;
            }
            out.add(new ListenerSpec(name, parseRequiredPort(key, e.getValue())));
        }
        return out;
    }

    /** Validate, apply defaults, and enforce the daemon precondition (≥1 listener). */
    static ModuleConfig build(String internalRaw, List<ListenerSpec> listeners, String extDirRaw) {
        final int internalPort = (internalRaw == null || internalRaw.isBlank())
            ? DEFAULT_INTERNAL_PORT
            : parseRequiredPort("INTERNAL_PORT", internalRaw);

        if (listeners == null || listeners.isEmpty()) {
            throw new IllegalStateException(
                "No listener ports configured. Hub Node must inject them via the runtime config "
                + "file (RUNTIME_CONFIG_FILE -> listenerPorts) or LISTENER_PORT_* env. Refusing to start.");
        }
        for (ListenerSpec s : listeners) {
            checkPortRange("listener '" + s.name() + "'", s.port());
        }

        final String extensionDir = (extDirRaw == null || extDirRaw.isBlank())
            ? DEFAULT_EXTENSION_DIR : extDirRaw;

        return new ModuleConfig(internalPort, List.copyOf(listeners), extensionDir);
    }

    private static int parseRequiredPort(String name, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(name + " is not set");
        }
        final int port;
        try {
            port = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(name + " is not a valid port: '" + raw + "'", e);
        }
        checkPortRange(name, port);
        return port;
    }

    private static void checkPortRange(String name, int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalStateException(name + " out of range (1-65535): " + port);
        }
    }
}

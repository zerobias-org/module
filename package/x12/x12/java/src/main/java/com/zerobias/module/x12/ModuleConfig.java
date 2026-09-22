package com.zerobias.module.x12;

import java.util.Map;

/**
 * Environment-driven process configuration (DESIGN §3). Unlike hl7/v2 there are
 * <b>no listener ports</b>: ingress is the mounted inbox, discovered by polling. What
 * remains is the ops port and the two file locations:
 * <ul>
 *   <li>{@code INTERNAL_PORT} — Javalin RPC port behind nginx (default 8889)</li>
 *   <li>{@code BUFFER_DB} — the SQLite buffer path (default {@code /var/lib/module/buffer.db})</li>
 *   <li>{@code RUNTIME_CONFIG_YML} — the {@code runtimeConfig.yml} copied into the image
 *       (default {@code /opt/module/runtimeConfig.yml}); its {@code config:} block is the
 *       dev/e2e fallback when {@code MODULE_CONFIG} is absent</li>
 *   <li>{@code RUNTIME_CONFIG_FILE} — optional node-delivered runtime config JSON, read by
 *       {@link RuntimeConfigFile}</li>
 * </ul>
 * The opaque module knobs ({@code MODULE_CONFIG}) are parsed by {@link ModuleRuntimeConfig}.
 */
public record ModuleConfig(int internalPort, String bufferDbPath, String runtimeConfigYml) {

    static final int DEFAULT_INTERNAL_PORT = 8889;
    static final String DEFAULT_BUFFER_DB = "/var/lib/module/buffer.db";
    static final String DEFAULT_RUNTIME_CONFIG_YML = "/opt/module/runtimeConfig.yml";

    public static ModuleConfig fromEnv() {
        return resolve(System.getenv());
    }

    /** Testable seam: resolve from an env map with no process-env access. */
    static ModuleConfig resolve(Map<String, String> env) {
        final String internalRaw = env.get("INTERNAL_PORT");
        final int internalPort = (internalRaw == null || internalRaw.isBlank())
            ? DEFAULT_INTERNAL_PORT
            : parseRequiredPort("INTERNAL_PORT", internalRaw);
        return new ModuleConfig(
            internalPort,
            orDefault(env.get("BUFFER_DB"), DEFAULT_BUFFER_DB),
            orDefault(env.get("RUNTIME_CONFIG_YML"), DEFAULT_RUNTIME_CONFIG_YML));
    }

    private static String orDefault(String raw, String dflt) {
        return raw == null || raw.isBlank() ? dflt : raw.trim();
    }

    private static int parseRequiredPort(String name, String raw) {
        final int port;
        try {
            port = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(name + " is not a valid port: '" + raw + "'", e);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalStateException(name + " out of range (1-65535): " + port);
        }
        return port;
    }
}

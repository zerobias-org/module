package com.zerobias.module.hl7;

/**
 * Environment-driven module configuration (DESIGN §3.2).
 *
 * <p>The MLLP listener port is mandatory and has no default — a daemon listener
 * with no port is a misconfiguration, not a fallback case. Hub Node injects it
 * from {@code runtimeConfig.listenerPorts[name=mllp]}.
 */
public record ModuleConfig(int internalPort, int mllpPort, String extensionDir) {

    private static final int DEFAULT_INTERNAL_PORT = 8889;
    private static final String DEFAULT_EXTENSION_DIR = "/opt/module/extensions";

    public static ModuleConfig fromEnv() {
        final int internalPort = parsePort("INTERNAL_PORT", DEFAULT_INTERNAL_PORT);

        final String rawMllp = System.getenv("LISTENER_PORT_MLLP");
        if (rawMllp == null || rawMllp.isBlank()) {
            throw new IllegalStateException(
                "LISTENER_PORT_MLLP is not set. Hub Node must inject it from "
                + "runtimeConfig.listenerPorts[name=mllp]. Refusing to start.");
        }
        final int mllpPort = parseRequiredPort("LISTENER_PORT_MLLP", rawMllp);

        String extensionDir = System.getenv("EXTENSION_DIR");
        if (extensionDir == null || extensionDir.isBlank()) {
            extensionDir = DEFAULT_EXTENSION_DIR;
        }

        return new ModuleConfig(internalPort, mllpPort, extensionDir);
    }

    private static int parsePort(String name, int fallback) {
        final String raw = System.getenv(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return parseRequiredPort(name, raw);
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

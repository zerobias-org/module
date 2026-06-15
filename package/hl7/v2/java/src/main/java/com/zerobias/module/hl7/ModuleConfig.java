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
        return from(System.getenv("INTERNAL_PORT"),
                    System.getenv("LISTENER_PORT_MLLP"),
                    System.getenv("EXTENSION_DIR"));
    }

    /** Testable seam: same logic as {@link #fromEnv()} but with the raw values injected. */
    static ModuleConfig from(String internalRaw, String mllpRaw, String extDirRaw) {
        final int internalPort = (internalRaw == null || internalRaw.isBlank())
            ? DEFAULT_INTERNAL_PORT
            : parseRequiredPort("INTERNAL_PORT", internalRaw);

        if (mllpRaw == null || mllpRaw.isBlank()) {
            throw new IllegalStateException(
                "LISTENER_PORT_MLLP is not set. Hub Node must inject it from "
                + "runtimeConfig.listenerPorts[name=mllp]. Refusing to start.");
        }
        final int mllpPort = parseRequiredPort("LISTENER_PORT_MLLP", mllpRaw);

        final String extensionDir = (extDirRaw == null || extDirRaw.isBlank())
            ? DEFAULT_EXTENSION_DIR : extDirRaw;

        return new ModuleConfig(internalPort, mllpPort, extensionDir);
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

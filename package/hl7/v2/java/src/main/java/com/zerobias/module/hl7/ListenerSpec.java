package com.zerobias.module.hl7;

/**
 * One MLLP listener the daemon binds: a stable {@code name} (the feed identity —
 * stamped on every message received on this port as provenance, DESIGN §11.4) and
 * the resolved TCP {@code port}.
 *
 * <p>Names come verbatim from the runtime-config file's {@code listenerPorts[].name}
 * (collision-safe). On the old-node env fallback the name is the lowercased
 * {@code LISTENER_PORT_<NAME>} suffix, which can be lossy under key normalization —
 * which is exactly why the file is preferred.
 */
public record ListenerSpec(String name, int port) {
}

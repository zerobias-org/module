package com.zerobias.module.ocsf.buffer;

/**
 * Outcome recorded for a discovered event file (DESIGN §4, {@code files.status}):
 * {@code consumed} (every event stored, renamed {@code .done}), {@code partial} (some events
 * rejected, the rest stored, renamed {@code .done}), {@code error} (unreadable, unsupported,
 * or every event rejected; renamed {@code .error}), {@code duplicate} (checksum already
 * consumed; renamed {@code .done}).
 */
public enum FileStatus {
    CONSUMED("consumed"),
    PARTIAL("partial"),
    ERROR("error"),
    DUPLICATE("duplicate");

    private final String wire;

    FileStatus(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static FileStatus fromWire(String wire) {
        for (FileStatus s : values()) {
            if (s.wire.equals(wire)) {
                return s;
            }
        }
        throw new IllegalArgumentException("unknown file status: " + wire);
    }
}

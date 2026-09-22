package com.zerobias.module.x12.buffer;

/**
 * Outcome recorded for a discovered interchange file (DESIGN §8 {@code files.status}):
 * {@code consumed} (parsed + committed, renamed {@code .done}), {@code error} (parse/IO
 * failure, renamed {@code .error}), {@code duplicate} (checksum already consumed;
 * renamed {@code .done} — DESIGN §4.2a).
 */
public enum FileStatus {
    CONSUMED("consumed"),
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

package com.zerobias.module.x12.buffer;

/**
 * Lifecycle status of a buffered transaction set (DESIGN §8). Wire values match
 * the SQLite {@code transactions.status} column: {@code new → in_flight → acked}.
 */
public enum Status {
    NEW("new"),
    IN_FLIGHT("in_flight"),
    ACKED("acked");

    private final String wire;

    Status(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static Status fromWire(String wire) {
        for (Status s : values()) {
            if (s.wire.equals(wire)) {
                return s;
            }
        }
        throw new IllegalArgumentException("unknown transaction status: " + wire);
    }
}

package com.zerobias.module.hl7.buffer;

/**
 * Lifecycle status of a buffered message (DESIGN §8). Wire values match the
 * SQLite {@code status} column and the {@code schema:enum:hl7v2.ops.MessageStatus}
 * value set: {@code new → in_flight → acked}.
 */
public enum MessageStatus {
    NEW("new"),
    IN_FLIGHT("in_flight"),
    ACKED("acked");

    private final String wire;

    MessageStatus(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static MessageStatus fromWire(String wire) {
        for (MessageStatus s : values()) {
            if (s.wire.equals(wire)) {
                return s;
            }
        }
        throw new IllegalArgumentException("unknown message status: " + wire);
    }
}

package com.zerobias.module.x12.health;

/** Shared fixtures for poller status: a watched source as a stub poller reports it. */
public final class SourceStatuses {

    private SourceStatuses() {
    }

    /** A source with no scan history: no cadence (stall check off), never scanned, never failed. */
    public static PollerStatus.SourceStatus idle(String name, String path, boolean writable, int pending, int errored) {
        return new PollerStatus.SourceStatus(name, path, writable, pending, errored, 0, null, null, null, null, 0);
    }
}

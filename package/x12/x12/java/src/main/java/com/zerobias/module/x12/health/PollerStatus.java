package com.zerobias.module.x12.health;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * What the inbox poller reports into {@code /healthz} and {@code /stats} (DESIGN §9).
 * Implemented by the poller (the {@code inbox} package); the foundation ships only
 * {@link #DOWN}, which reports {@code up=false} so a build without a poller wired in
 * is visibly degraded (503) rather than silently healthy.
 */
public interface PollerStatus {

    /** One watched source as reported in {@code poller.sources[]}. */
    record SourceStatus(String name, String path, boolean writable, int pending, int errored) {
    }

    /** True while every poller thread is alive. */
    boolean up();

    /** Time of the most recent scan across all sources, if any. */
    Optional<Instant> lastScan();

    /** Time of the most recent successful file consumption, if any. */
    Optional<Instant> lastConsumed();

    /** True while the buffer is over its byte ceiling and files are being left untouched (DESIGN §4.2 step 4). */
    boolean backpressure();

    /** Per-source state, in configuration order. */
    List<SourceStatus> sources();

    /** No poller wired in: down, nothing scanned, no sources. */
    PollerStatus DOWN = new PollerStatus() {
        @Override
        public boolean up() {
            return false;
        }

        @Override
        public Optional<Instant> lastScan() {
            return Optional.empty();
        }

        @Override
        public Optional<Instant> lastConsumed() {
            return Optional.empty();
        }

        @Override
        public boolean backpressure() {
            return false;
        }

        @Override
        public List<SourceStatus> sources() {
            return List.of();
        }
    };
}

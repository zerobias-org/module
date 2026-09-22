package com.zerobias.module.x12;

import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.RetentionSweeper;
import com.zerobias.module.x12.health.PollerStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * <b>HOOK for the inbox package.</b> {@link X12ApiServer} boots the buffer and sweeper,
 * then asks this factory for the pollers (DESIGN §4). The foundation ships only
 * {@link #NONE}; the {@code inbox} package plugs in by registering its implementation
 * as a {@link java.util.ServiceLoader} provider
 * ({@code META-INF/services/com.zerobias.module.x12.InboxPollerFactory}) — no edit to
 * {@code X12ApiServer} needed — or by replacing {@link X12ApiServer#pollerFactory()}.
 *
 * <p>Contract for the implementation: one poller thread per {@code config.sources[]};
 * every consumed file goes through {@link BufferStore#consumeFile} then the
 * {@code .done} rename; {@code sweeper} (nullable when retention is unbounded) is
 * consulted for backpressure ({@link RetentionSweeper#overCapacity()}).
 */
public interface InboxPollerFactory {

    /** Start the pollers for {@code config.sources()} against {@code buffer}; never returns null. */
    PollerHandle start(ModuleRuntimeConfig config, BufferStore buffer, RetentionSweeper sweeper);

    /** No-op factory: no pollers, {@link PollerStatus#DOWN} semantics, rescan reports nothing. */
    InboxPollerFactory NONE = (config, buffer, sweeper) -> new PollerHandle() {
        @Override
        public RescanResult rescan(String source) {
            return new RescanResult(0, 0, 0, 0);
        }

        @Override
        public void close() {
        }

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

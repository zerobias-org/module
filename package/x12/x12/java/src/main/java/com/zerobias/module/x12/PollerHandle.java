package com.zerobias.module.x12;

import com.zerobias.module.x12.health.PollerStatus;

/**
 * A running set of inbox pollers: reports {@link PollerStatus} for health/stats and
 * stops on {@link #close()}. Also exposes {@link #rescan(String)} for the
 * {@code ops/rescan} function (DESIGN §2.5). Returned by
 * {@link com.zerobias.module.x12.inbox.X12InboxPollerFactory#start}.
 */
public interface PollerHandle extends PollerStatus, AutoCloseable {

    /** Result of a forced scan ({@code ops/rescan} output shape, DESIGN §2.5). */
    record RescanResult(int scanned, int discovered, int consumed, int errored) {
    }

    /**
     * Trigger an immediate poll of one source ({@code source} = its name) or of every
     * source ({@code null}). Blocks until the scan completes.
     */
    RescanResult rescan(String source) throws Exception;

    @Override
    void close();
}

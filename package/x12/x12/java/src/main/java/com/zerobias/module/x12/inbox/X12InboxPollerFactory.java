package com.zerobias.module.x12.inbox;

import com.zerobias.module.x12.ModuleRuntimeConfig;
import com.zerobias.module.x12.PollerHandle;
import com.zerobias.module.x12.SourceConfig;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.RetentionSweeper;
import com.zerobias.module.x12.materializer.StructureResolver;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Starts the inbox pollers once {@link com.zerobias.module.x12.X12ApiServer} has booted the
 * buffer and sweeper (DESIGN §4): one {@link InboxPoller} per {@code config.sources[]}, all
 * sharing one {@link FileConsumer} and {@link StructureResolver}. Every consumed file goes
 * through {@link BufferStore#consumeFile} then the {@code .done} rename; the sweeper (null
 * when retention is unbounded) is consulted for backpressure. The returned {@link Handle}
 * reports the aggregate {@link com.zerobias.module.x12.health.PollerStatus}.
 */
public final class X12InboxPollerFactory {

    private X12InboxPollerFactory() {
    }

    /** Start the scheduled pollers for {@code config.sources()} on the buffer's clock. */
    public static Handle start(ModuleRuntimeConfig config, BufferStore buffer, RetentionSweeper sweeper) {
        return start(config, buffer, sweeper, buffer.clock(), true);
    }

    /**
     * Build the pollers with an explicit clock, optionally without starting the schedule
     * (tests drive {@link InboxPoller#scan()} themselves).
     */
    public static Handle start(ModuleRuntimeConfig config, BufferStore buffer, RetentionSweeper sweeper,
                               Clock clock, boolean schedule) {
        Objects.requireNonNull(clock, "clock");
        FileConsumer consumer = new FileConsumer(buffer, sweeper, config, new StructureResolver(), clock);
        List<InboxPoller> pollers = new ArrayList<>();
        for (SourceConfig s : config.sources()) {
            pollers.add(new InboxPoller(s, consumer, buffer, clock, config.consumedSuffix(), config.errorSuffix()));
        }
        Handle h = new Handle(pollers);
        if (schedule) {
            for (InboxPoller p : pollers) {
                p.start();
            }
        }
        return h;
    }

    /** The running set of pollers. */
    public static final class Handle implements PollerHandle {

        private final List<InboxPoller> pollers;
        private volatile boolean closed;

        Handle(List<InboxPoller> pollers) {
            this.pollers = List.copyOf(pollers);
        }

        public List<InboxPoller> pollers() {
            return pollers;
        }

        @Override
        public RescanResult rescan(String source) throws Exception {
            int scanned = 0;
            int discovered = 0;
            int consumed = 0;
            int errored = 0;
            boolean matched = false;
            for (InboxPoller p : pollers) {
                if (source != null && !source.isBlank() && !p.source().name().equals(source)) {
                    continue;
                }
                matched = true;
                RescanResult r = p.scan();
                scanned += r.scanned();
                discovered += r.discovered();
                consumed += r.consumed();
                errored += r.errored();
            }
            if (!matched) {
                throw new IllegalArgumentException("unknown source: " + source);
            }
            return new RescanResult(scanned, discovered, consumed, errored);
        }

        @Override
        public boolean up() {
            if (closed || pollers.isEmpty()) {
                return false;
            }
            for (InboxPoller p : pollers) {
                if (!p.up()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public Optional<Instant> lastScan() {
            Instant best = null;
            for (InboxPoller p : pollers) {
                Instant i = p.lastScan().orElse(null);
                if (i != null && (best == null || i.isAfter(best))) {
                    best = i;
                }
            }
            return Optional.ofNullable(best);
        }

        @Override
        public Optional<Instant> lastConsumed() {
            Instant best = null;
            for (InboxPoller p : pollers) {
                Instant i = p.lastConsumed().orElse(null);
                if (i != null && (best == null || i.isAfter(best))) {
                    best = i;
                }
            }
            return Optional.ofNullable(best);
        }

        @Override
        public boolean backpressure() {
            for (InboxPoller p : pollers) {
                if (p.backpressure()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public List<SourceStatus> sources() {
            List<SourceStatus> out = new ArrayList<>();
            for (InboxPoller p : pollers) {
                out.add(p.status());
            }
            return out;
        }

        /** Every poller is told to stop first, then all share one {@link InboxPoller#CLOSE_WAIT}. */
        @Override
        public void close() {
            closed = true;
            for (InboxPoller p : pollers) {
                p.stop();
            }
            Instant deadline = Instant.now().plus(InboxPoller.CLOSE_WAIT);
            for (InboxPoller p : pollers) {
                p.awaitStopped(deadline);
            }
        }
    }
}

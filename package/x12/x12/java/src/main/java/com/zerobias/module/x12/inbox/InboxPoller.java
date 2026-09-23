package com.zerobias.module.x12.inbox;

import com.zerobias.module.x12.PollerHandle.RescanResult;
import com.zerobias.module.x12.SourceConfig;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.FileStatus;
import com.zerobias.module.x12.health.PollerStatus.SourceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * One watched directory (DESIGN §4.2): a scheduled thread runs {@link #scan()} every
 * {@code pollIntervalSec}. A scan lists regular files matching the source's glob, skips
 * {@code .done}/{@code .error}/{@code .tmp}/{@code .part}/{@code .partial} (and the
 * configured suffixes) and dotfiles, tracks {@code (size, mtime)} through
 * {@link FileStability}, and hands each stable file to the {@link FileConsumer}. Nothing
 * is skipped by path: every stable candidate is hashed and its identity
 * ({@code <path>@<hash>}) decides consume / duplicate / redelivery / retry (DESIGN §4.2).
 * The one in-memory guard is {@link #seen}: a file this process already consumed that is
 * <em>still at its path</em> (the post-commit rename failed) is keyed by
 * {@code (path, size, mtime)} and not re-hashed every poll; the key is dropped as soon as
 * the path leaves the listing, and nothing survives a restart. Under backpressure the scan
 * stops touching files and reports it. {@link #scan()} is synchronized so an
 * {@code ops/rescan} never overlaps the schedule.
 */
public final class InboxPoller implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(InboxPoller.class);

    /** Always skipped, whatever the configured suffixes (DESIGN §4.2 step 1). */
    static final List<String> SKIP_SUFFIXES = List.of(".done", ".error", ".tmp", ".part", ".partial");

    private final SourceConfig source;
    private final FileConsumer consumer;
    private final BufferStore buffer;
    private final Clock clock;
    private final FileStability stability;
    private final List<String> skipSuffixes;
    /** Consumed-in-this-process files still sitting at their path (rename failed): never re-hashed. */
    private final Set<SeenKey> seen = new HashSet<>();

    /** The identity of a listing entry as far as re-hashing is concerned. */
    record SeenKey(Path path, long size, Instant mtime) {
    }

    private ScheduledExecutorService scheduler;
    private volatile boolean closed;
    private volatile Instant lastScan;
    private volatile Instant lastConsumed;
    private volatile boolean backpressure;
    private volatile int pending;
    private volatile Boolean writable;

    public InboxPoller(SourceConfig source, FileConsumer consumer, BufferStore buffer, Clock clock,
                       String consumedSuffix, String errorSuffix) {
        this.source = source;
        this.consumer = consumer;
        this.buffer = buffer;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.stability = new FileStability(Duration.ofSeconds(source.stableForSec()));
        List<String> suffixes = new ArrayList<>(SKIP_SUFFIXES);
        for (String s : new String[] {consumedSuffix, errorSuffix}) {
            if (s != null && !s.isBlank() && !suffixes.contains(s.toLowerCase(Locale.ROOT))) {
                suffixes.add(s.toLowerCase(Locale.ROOT));
            }
        }
        this.skipSuffixes = List.copyOf(suffixes);
    }

    public SourceConfig source() {
        return source;
    }

    /** Start the scheduled scans (first one immediately). Idempotent. */
    public synchronized void start() {
        if (scheduler != null || closed) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "x12-inbox-" + source.name());
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::safeScan, 0, source.pollIntervalSec(), TimeUnit.SECONDS);
        LOG.info("poller '{}' watching {} (pattern {}, every {}s, stable for {}s)", source.name(), source.path(),
            source.pattern(), source.pollIntervalSec(), source.stableForSec());
    }

    private void safeScan() {
        try {
            scan();
        } catch (Throwable t) {
            // A thrown exception would silently cancel the fixed-delay schedule.
            LOG.error("poller '{}' scan failed: {}", source.name(), t.toString(), t);
        }
    }

    /** One scan pass. Returns the counts the {@code ops/rescan} function reports. */
    public synchronized RescanResult scan() throws SQLException {
        Instant now = Instant.now(clock);
        int scanned = 0;
        int discovered = 0;
        int consumed = 0;
        int errored = 0;
        int pendingNow = 0;
        boolean pressure = false;
        Path dir = source.dir();
        this.writable = Files.isDirectory(dir) && Files.isWritable(dir);

        List<Path> candidates = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                if (isCandidate(p)) {
                    candidates.add(p);
                }
            }
        } catch (IOException e) {
            LOG.error("poller '{}' cannot list {}: {}", source.name(), dir, e.toString());
            this.writable = false;
            this.lastScan = now;
            return new RescanResult(0, 0, 0, 0);
        }
        candidates.sort(null);
        Set<Path> present = new HashSet<>(candidates);
        stability.retainOnly(present);
        seen.removeIf(k -> !present.contains(k.path()));

        for (Path p : candidates) {
            long size;
            Instant mtime;
            try {
                size = Files.size(p);
                mtime = Files.getLastModifiedTime(p).toInstant();
            } catch (IOException gone) {
                stability.forget(p);
                continue;
            }
            SeenKey key = new SeenKey(p, size, mtime);
            if (seen.contains(key)) {
                LOG.debug("poller '{}': {} already consumed by this process (rename pending); skipped", source.name(), p);
                continue;
            }
            scanned++;
            if (stability.isNew(p)) {
                discovered++;
            }
            boolean stable = stability.observe(p, size, mtime, now);
            if (!stable) {
                LOG.debug("poller '{}': {} not stable yet ({} bytes, mtime {})", source.name(), p, size, mtime);
                pendingNow++;
                continue;
            }
            if (pressure || consumer.backpressure()) {
                pressure = true;
                pendingNow++;
                continue;
            }
            Instant discoveredAt = stability.sighting(p).map(FileStability.Sighting::firstSeen).orElse(now);
            FileConsumer.Result r = consumer.consume(source, p, discoveredAt);
            switch (r.outcome()) {
                case CONSUMED, DUPLICATE -> {
                    consumed++;
                    lastConsumed = Instant.now(clock);
                    settle(p, key);
                }
                case ERROR -> {
                    errored++;
                    settle(p, key);
                }
                case BACKPRESSURE -> {
                    pressure = true;
                    pendingNow++;
                }
                case UNREADABLE -> pendingNow++;   // no identity yet; stability keeps tracking it
            }
        }
        this.pending = pendingNow;
        this.backpressure = pressure;
        this.lastScan = now;
        if (pressure) {
            LOG.warn("poller '{}': backpressure, {} file(s) left untouched", source.name(), pendingNow);
        }
        return new RescanResult(scanned, discovered, consumed, errored);
    }

    private boolean isCandidate(Path p) {
        String name = p.getFileName().toString();
        if (name.startsWith(".")) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String s : skipSuffixes) {
            if (lower.endsWith(s)) {
                return false;
            }
        }
        if (!source.matchesFileName(name)) {
            return false;
        }
        return Files.isRegularFile(p);
    }

    /**
     * A file was handled: stop tracking its stability and, if it is still at its path
     * (the rename failed), remember it so the next polls do not re-hash it.
     */
    private void settle(Path p, SeenKey key) {
        stability.forget(p);
        if (Files.exists(p)) {
            seen.add(key);
        }
    }

    // ---- status --------------------------------------------------------------------------

    public boolean up() {
        return !closed && scheduler != null && !scheduler.isShutdown();
    }

    public Optional<Instant> lastScan() {
        return Optional.ofNullable(lastScan);
    }

    public Optional<Instant> lastConsumed() {
        return Optional.ofNullable(lastConsumed);
    }

    public boolean backpressure() {
        return backpressure;
    }

    public SourceStatus status() {
        Boolean w = writable;
        if (w == null) {
            Path dir = source.dir();
            w = Files.isDirectory(dir) && Files.isWritable(dir);
        }
        int errored;
        try {
            errored = (int) buffer.fileCount(source.name(), FileStatus.ERROR);
        } catch (SQLException e) {
            errored = -1;
        }
        return new SourceStatus(source.name(), source.path(), w, pending, errored);
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}

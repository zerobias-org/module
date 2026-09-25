package com.zerobias.module.x12.inbox;

import com.zerobias.module.x12.PollerHandle.RescanResult;
import com.zerobias.module.x12.SourceConfig;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.FileStatus;
import com.zerobias.module.x12.health.PollerStatus.SourceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
 *
 * <p>Symbolic links are skipped, never followed (a link can point anywhere in the container),
 * and logged once per path.
 *
 * <p><b>Failure isolation.</b> Every file is handled on its own: whatever one file throws —
 * an unexpected exception, an {@link OutOfMemoryError} or {@link StackOverflowError}, a
 * SQLite refusal of that file's rows ({@link FileConsumer#rejectsThisFile}) — is logged,
 * the file is left in place for the next scan, and the scan moves on to the next file. A
 * failure of the <em>buffer</em> (any other {@link SQLException}: the database is unusable,
 * the disk is full, the schema no longer matches) is not the file's fault and would recur for
 * every file, so it ends the scan and is recorded as the scan's error, which {@code /healthz}
 * reports as unhealthy. Errors that leave the JVM itself unreliable (other
 * {@link VirtualMachineError}s) propagate.
 */
public final class InboxPoller implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(InboxPoller.class);

    private static final int MAX_ERROR_CHARS = 300;

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
    /** Symlinks already warned about; pruned when they leave the listing. */
    private final Set<Path> symlinks = new HashSet<>();

    /** The identity of a listing entry as far as re-hashing is concerned. */
    record SeenKey(Path path, long size, Instant mtime) {
    }

    /** A listing entry: a regular file (never a link) and its {@code (size, mtime)} from one stat. */
    private record Candidate(Path path, long size, Instant mtime) {
    }

    private ScheduledExecutorService scheduler;
    private volatile boolean closed;
    /** When {@link #start()} scheduled the scans; the stall reference before any scan completes. */
    private volatile Instant startedAt;
    private volatile Instant lastScanStarted;
    /** The last scan that completed without failing. */
    private volatile Instant lastScan;
    /** The last time the running scan finished a file. */
    private volatile Instant lastProgress;
    private volatile String lastError;
    private volatile Instant lastErrorAt;
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
        startedAt = Instant.now(clock);
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

    /**
     * One scan pass. Returns the counts the {@code ops/rescan} function reports. Throws only
     * when the buffer failed (see the class doc); one file's failure never ends the scan.
     * Records the outcome for {@code /healthz}: a completed scan moves {@link #lastScan()}, a
     * failed one (thrown, or a directory that cannot be listed) records the error instead.
     */
    public synchronized RescanResult scan() throws SQLException {
        Instant now = Instant.now(clock);
        lastScanStarted = now;
        try {
            return scanOnce(now);
        } catch (Throwable t) {
            recordFailure(t.toString());
            throw t;
        }
    }

    private RescanResult scanOnce(Instant now) throws SQLException {
        int scanned = 0;
        int discovered = 0;
        int consumed = 0;
        int errored = 0;
        int pendingNow = 0;
        boolean pressure = false;
        Path dir = source.dir();
        this.writable = Files.isDirectory(dir) && Files.isWritable(dir);

        List<Candidate> candidates;
        try {
            candidates = list(dir);
        } catch (IOException | DirectoryIteratorException e) {
            LOG.error("poller '{}' cannot list {}: {}", source.name(), dir, e.toString());
            this.writable = false;
            recordFailure("cannot list " + dir + ": " + e);
            return new RescanResult(0, 0, 0, 0);
        }
        Set<Path> present = new HashSet<>();
        for (Candidate c : candidates) {
            present.add(c.path());
        }
        stability.retainOnly(present);
        seen.removeIf(k -> !present.contains(k.path()));

        for (Candidate c : candidates) {
            lastProgress = Instant.now(clock);
            Path p = c.path();
            SeenKey key = new SeenKey(p, c.size(), c.mtime());
            if (seen.contains(key)) {
                LOG.debug("poller '{}': {} already consumed by this process (rename pending); skipped", source.name(), p);
                continue;
            }
            scanned++;
            if (stability.isNew(p)) {
                discovered++;
            }
            boolean stable = stability.observe(p, c.size(), c.mtime(), now);
            if (!stable) {
                LOG.debug("poller '{}': {} not stable yet ({} bytes, mtime {})", source.name(), p, c.size(), c.mtime());
                pendingNow++;
                continue;
            }
            if (pressure || consumer.backpressure()) {
                pressure = true;
                pendingNow++;
                continue;
            }
            FileConsumer.Result r;
            try {
                r = consumer.consume(source, p, stability.sighting(p).orElseThrow());
            } catch (SQLException e) {
                if (!FileConsumer.rejectsThisFile(e)) {
                    // The buffer failed, not this file: every later file would fail the same way.
                    this.pending = pendingNow + 1;
                    this.backpressure = pressure;
                    throw e;
                }
                pendingNow++;
                LOG.error("poller '{}': {} refused by the buffer ({}); left in place, retried next scan",
                    source.name(), p, e.toString());
                continue;
            } catch (Throwable e) {
                rethrowIfFatal(e);
                pendingNow++;
                LOG.error("poller '{}': {} failed ({}); left in place, retried next scan", source.name(), p,
                    e.toString(), e);
                continue;
            }
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
                // No identity yet / not the file the window saw: stability keeps tracking it.
                case UNREADABLE, CHANGED -> pendingNow++;
            }
        }
        this.pending = pendingNow;
        this.backpressure = pressure;
        this.lastScan = Instant.now(clock);
        this.lastProgress = this.lastScan;
        if (pressure) {
            LOG.warn("poller '{}': backpressure, {} file(s) left untouched", source.name(), pendingNow);
        }
        return new RescanResult(scanned, discovered, consumed, errored);
    }

    /** Regular files matching the name rules, one no-follow stat each; symlinks are skipped. */
    private List<Candidate> list(Path dir) throws IOException {
        List<Candidate> out = new ArrayList<>();
        Set<Path> links = new HashSet<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                if (!nameMatches(p)) {
                    continue;
                }
                BasicFileAttributes a;
                try {
                    a = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                } catch (IOException gone) {
                    continue;
                }
                if (a.isSymbolicLink()) {
                    links.add(p);
                    if (symlinks.add(p)) {
                        LOG.warn("poller '{}': {} is a symbolic link; skipped (links are never followed)",
                            source.name(), p);
                    }
                    continue;
                }
                if (a.isRegularFile()) {
                    out.add(new Candidate(p, a.size(), a.lastModifiedTime().toInstant()));
                }
            }
        }
        symlinks.retainAll(links);
        out.sort(Comparator.comparing(Candidate::path));
        return out;
    }

    private boolean nameMatches(Path p) {
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
        return source.matchesFileName(name);
    }

    private void recordFailure(String message) {
        lastError = message.length() <= MAX_ERROR_CHARS ? message : message.substring(0, MAX_ERROR_CHARS) + "…";
        lastErrorAt = Instant.now(clock);
    }

    /** Errors that leave the JVM itself unreliable end the scan; a file's OOM or stack overflow does not. */
    private static void rethrowIfFatal(Throwable e) {
        if (e instanceof VirtualMachineError && !(e instanceof OutOfMemoryError) && !(e instanceof StackOverflowError)) {
            throw (VirtualMachineError) e;
        }
    }

    /**
     * A file was handled: stop tracking its stability and, if it is still at its path
     * (the rename failed), remember it so the next polls do not re-hash it.
     */
    private void settle(Path p, SeenKey key) {
        stability.forget(p);
        if (Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
            seen.add(key);
        }
    }

    // ---- status --------------------------------------------------------------------------

    public boolean up() {
        return !closed && scheduler != null && !scheduler.isShutdown();
    }

    /** The last scan that completed without failing. */
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
        return new SourceStatus(source.name(), source.path(), w, pending, errored, source.pollIntervalSec(),
            startedAt, lastScanStarted, lastScan, lastProgress, lastError, lastErrorAt);
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}

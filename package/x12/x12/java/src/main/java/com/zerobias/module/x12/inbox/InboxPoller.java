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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
 * <p>Symbolic links are skipped, never followed, and logged once per path: a link can
 * point anywhere in the container (another tenant's mount, a secrets file), and renaming
 * it would need an identity that only reading through it could give.
 *
 * <p>Every file is handled in isolation: whatever one file throws is logged and counted
 * against this scan, and the scan moves on — a bad file must never stop the others. A
 * file that cannot be read is retried every scan; after {@link #UNREADABLE_ESCALATION}
 * failures in a row it gets an {@code error} row and one ERROR log line, and later
 * retries log at DEBUG. Scan start/completion/progress, the last failure and the run of
 * failed scans feed {@code /healthz} ({@link SourceStatus}).
 *
 * <p>{@link #close()} does not wait behind a running scan: it takes a separate lifecycle
 * lock, marks the poller closed and interrupts the scan thread, and the scan stops at the
 * next file boundary — the file in hand is finished, the rest wait for the next start.
 */
public final class InboxPoller implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(InboxPoller.class);

    /** Always skipped, whatever the configured suffixes (DESIGN §4.2 step 1). */
    static final List<String> SKIP_SUFFIXES = List.of(".done", ".error", ".tmp", ".part", ".partial");

    /** Consecutive failed reads of one file before it is recorded as an error. */
    static final int UNREADABLE_ESCALATION = 5;

    private static final int MAX_ERROR_CHARS = 300;

    /**
     * How long {@link #close()} waits for a running scan to finish its file. Docker stops a
     * container with SIGKILL 10 s after SIGTERM, and the HTTP server and the buffer close in
     * that window too; a scan wedged on a hung mount is abandoned (its thread is a daemon).
     */
    static final Duration CLOSE_WAIT = Duration.ofSeconds(5);

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
    /** Consecutive failed reads per path; pruned when the path leaves the listing. */
    private final Map<Path, Integer> readFailures = new HashMap<>();

    /** The identity of a listing entry as far as re-hashing is concerned. */
    record SeenKey(Path path, long size, Instant mtime) {
    }

    private record Candidate(Path path, long size, Instant mtime) {
    }

    /** Per-scan counters. */
    private static final class Tally {
        int scanned;
        int discovered;
        int consumed;
        int errored;
        int pending;
        boolean pressure;
        String failure;
    }

    /** Guards {@link #scheduler} for start/close; never the scan's monitor, so close never waits on a scan. */
    private final Object lifecycle = new Object();
    private volatile ScheduledExecutorService scheduler;
    private volatile boolean closed;
    private volatile Instant lastScanStarted;
    private volatile Instant lastScanCompleted;
    private volatile Instant lastProgress;
    private volatile Instant lastConsumed;
    private volatile String lastError;
    private volatile int consecutiveFailures;
    private volatile boolean backpressure;
    private volatile int pending;
    private volatile Boolean writable;

    public InboxPoller(SourceConfig source, FileConsumer consumer, BufferStore buffer, Clock clock,
                       String consumedSuffix, String errorSuffix) {
        this.source = Objects.requireNonNull(source, "source");
        this.consumer = Objects.requireNonNull(consumer, "consumer");
        this.buffer = Objects.requireNonNull(buffer, "buffer");
        this.clock = Objects.requireNonNull(clock, "clock");
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
    public void start() {
        synchronized (lifecycle) {
            if (scheduler != null || closed) {
                return;
            }
            ScheduledExecutorService s = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "x12-inbox-" + source.name());
                t.setDaemon(true);
                return t;
            });
            s.scheduleWithFixedDelay(this::safeScan, 0, source.pollIntervalSec(), TimeUnit.SECONDS);
            scheduler = s;
        }
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
    public synchronized RescanResult scan() {
        Instant now = Instant.now(clock);
        lastScanStarted = now;
        Tally t = new Tally();
        Path dir = source.dir();
        this.writable = Files.isDirectory(dir) && Files.isWritable(dir);

        List<Candidate> candidates;
        try {
            candidates = list(dir);
        } catch (IOException | DirectoryIteratorException e) {
            LOG.error("poller '{}' cannot list {}: {}", source.name(), dir, e.toString());
            this.writable = false;
            t.failure = "cannot list " + dir + ": " + e;
            finish(t);
            return new RescanResult(0, 0, 0, 0);
        }
        Set<Path> present = new HashSet<>();
        for (Candidate c : candidates) {
            present.add(c.path());
        }
        stability.retainOnly(present);
        seen.removeIf(k -> !present.contains(k.path()));
        readFailures.keySet().retainAll(present);

        for (int i = 0; i < candidates.size(); i++) {
            if (closed || Thread.currentThread().isInterrupted()) {
                // Shutting down: a backlog (or a hung mount) must not hold the process past its stop timeout.
                int left = candidates.size() - i;
                t.pending += left;
                LOG.info("poller '{}': closing; {} file(s) left for the next start", source.name(), left);
                break;
            }
            Candidate c = candidates.get(i);
            try {
                handle(c, now, t);
            } catch (Throwable e) {
                rethrowIfFatal(e);
                t.pending++;
                t.failure = describe(e);
                LOG.error("poller '{}': {} failed ({}); left in place, retried next scan", source.name(), c.path(),
                    t.failure, e);
            }
            lastProgress = Instant.now(clock);
        }
        this.pending = t.pending;
        this.backpressure = t.pressure;
        if (t.pressure) {
            LOG.warn("poller '{}': backpressure, {} file(s) left untouched", source.name(), t.pending);
        }
        finish(t);
        return new RescanResult(t.scanned, t.discovered, t.consumed, t.errored);
    }

    private void handle(Candidate c, Instant now, Tally t) throws SQLException {
        Path p = c.path();
        SeenKey key = new SeenKey(p, c.size(), c.mtime());
        if (seen.contains(key)) {
            LOG.debug("poller '{}': {} already consumed by this process (rename pending); skipped", source.name(), p);
            return;
        }
        t.scanned++;
        if (stability.isNew(p)) {
            t.discovered++;
        }
        if (!stability.observe(p, c.size(), c.mtime(), now)) {
            LOG.debug("poller '{}': {} not stable yet ({} bytes, mtime {})", source.name(), p, c.size(), c.mtime());
            t.pending++;
            return;
        }
        if (t.pressure || consumer.backpressure()) {
            t.pressure = true;
            t.pending++;
            return;
        }
        FileStability.Sighting sighting = stability.sighting(p).orElseThrow();
        FileConsumer.Result r = consumer.consume(source, p, sighting);
        switch (r.outcome()) {
            case CONSUMED, DUPLICATE -> {
                t.consumed++;
                lastConsumed = Instant.now(clock);
                settle(p, key);
            }
            case ERROR -> {
                t.errored++;
                settle(p, key);
            }
            case BACKPRESSURE -> {
                t.pressure = true;
                t.pending++;
            }
            // The stability window restarts from what the next scan observes.
            case CHANGED -> t.pending++;
            case UNREADABLE -> {
                t.pending++;
                unreadable(p, sighting, r.message());
            }
        }
    }

    /** No identity yet (nothing was hashed): keep retrying, but escalate once and then go quiet. */
    private void unreadable(Path p, FileStability.Sighting sighting, String message) throws SQLException {
        int n = readFailures.merge(p, 1, Integer::sum);
        if (n < UNREADABLE_ESCALATION) {
            LOG.warn("poller '{}': cannot read {} ({}); retrying next scan", source.name(), p, message);
        } else if (n == UNREADABLE_ESCALATION) {
            LOG.error("poller '{}': cannot read {} after {} attempts ({}); recorded as an error row, "
                + "still retried every scan (logged at DEBUG from now on)", source.name(), p, n, message);
            consumer.recordUnreadable(source, p, sighting, message);
        } else {
            LOG.debug("poller '{}': cannot read {} (attempt {}): {}", source.name(), p, n, message);
        }
    }

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

    /**
     * A file was handled: stop tracking its stability and, if it is still at its path
     * (the rename failed), remember it so the next polls do not re-hash it.
     */
    private void settle(Path p, SeenKey key) {
        stability.forget(p);
        readFailures.remove(p);
        if (Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
            seen.add(key);
        }
    }

    private void finish(Tally t) {
        lastScanCompleted = Instant.now(clock);
        lastProgress = lastScanCompleted;
        if (t.failure == null) {
            consecutiveFailures = 0;
        } else {
            consecutiveFailures++;
            lastError = t.failure;
        }
    }

    /** Errors that leave the JVM itself unreliable end the scan; a file's OOM or stack overflow does not. */
    private static void rethrowIfFatal(Throwable e) {
        if (e instanceof VirtualMachineError && !(e instanceof OutOfMemoryError) && !(e instanceof StackOverflowError)) {
            throw (VirtualMachineError) e;
        }
    }

    private static String describe(Throwable e) {
        String s = e.toString();
        return s.length() <= MAX_ERROR_CHARS ? s : s.substring(0, MAX_ERROR_CHARS) + "…";
    }

    // ---- status --------------------------------------------------------------------------

    public boolean up() {
        ScheduledExecutorService s = scheduler;
        return !closed && s != null && !s.isShutdown();
    }

    public Optional<Instant> lastScan() {
        return Optional.ofNullable(lastScanStarted);
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
            lastScanStarted, lastScanCompleted, lastProgress, lastError, consecutiveFailures);
    }

    @Override
    public void close() {
        stop();
        awaitStopped(Instant.now().plus(CLOSE_WAIT));
    }

    /** Mark closed and interrupt a running scan, without waiting for it (see {@link #awaitStopped}). */
    void stop() {
        ScheduledExecutorService s;
        synchronized (lifecycle) {
            closed = true;
            s = scheduler;
        }
        if (s != null) {
            s.shutdownNow();
        }
    }

    /** Wait until a running scan has returned or {@code deadline} passes; true when it returned. */
    boolean awaitStopped(Instant deadline) {
        ScheduledExecutorService s = scheduler;
        if (s == null) {
            return true;
        }
        try {
            long ms = Math.max(0, Duration.between(Instant.now(), deadline).toMillis());
            if (s.awaitTermination(ms, TimeUnit.MILLISECONDS)) {
                return true;
            }
            LOG.warn("poller '{}': scan still running after close; abandoned", source.name());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    }
}

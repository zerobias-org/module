package com.zerobias.module.x12.inbox;

import com.zerobias.module.x12.ModuleRuntimeConfig;
import com.zerobias.module.x12.SourceConfig;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.DuplicateElementKeyException;
import com.zerobias.module.x12.buffer.FileRow;
import com.zerobias.module.x12.buffer.FileStatus;
import com.zerobias.module.x12.buffer.RetentionSweeper;
import com.zerobias.module.x12.buffer.TransactionRow;
import com.zerobias.module.x12.materializer.Materializer;
import com.zerobias.module.x12.materializer.StructureResolver;
import com.zerobias.module.x12.materializer.TransactionJson;
import com.zerobias.module.x12.parser.TransactionTypes;
import com.zerobias.module.x12.parser.X12Parse;
import com.zerobias.module.x12.parser.X12ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Consumes one stable inbox file (DESIGN §4.2 step 3): read + sha256 → identity
 * ({@code fileId = <path>@<hash12>}) → duplicate / redelivery / retry resolution → parse →
 * materialize → {@link BufferStore#consumeFile} (one SQL transaction) → rename
 * {@code .done}. <b>Rename is the ack</b>: the file is renamed only after the commit
 * returns; a rename failure after commit is logged and recorded
 * ({@link BufferStore#markRenameFailed}) — re-hashing the file next scan finds the same
 * id and treats it as a redelivery, so the rows are never duplicated.
 *
 * <p>The read never follows a symlink and never holds more than {@code maxFileBytes}: a
 * larger file is hashed by streaming and sent to {@code .error} as {@code too-large}; one
 * whose bytes do not fit in the heap is hashed the same way and sent there as
 * {@code too-large-for-heap}. A file whose size or mtime differs from what the stability
 * window saw — before or after the read — is left alone for the next scan
 * ({@link Outcome#CHANGED}).
 *
 * <p>Failures that belong to the file — a parse error, {@code too-large}, two transaction
 * sets sharing an element key, the parser exhausting heap or stack on it, SQLite refusing
 * one of its rows ({@code buffer-rejected}) — record a
 * {@code files} row with {@code status=error} and the reason, then rename {@code .error}
 * (row first, so a failed rename is recorded the same way as on the consume path). Known
 * checksum ({@code consumed}|{@code duplicate}) → {@code files} row with
 * {@code status=duplicate} (or a {@code redelivery_count} bump when it is the same path),
 * no transactions, rename {@code .done}. Known checksum with {@code status=error} at the
 * same path → the error row is deleted and the file is consumed again (an operator renamed
 * it back). Renames never replace an existing file: a taken target gets
 * {@code .<discoveredAtEpochMillis>} interposed. Backpressure
 * ({@link RetentionSweeper#overCapacity()} after a sweep) → touch nothing.
 *
 * <p>One instance is shared by every poller, and {@link #consume} is synchronized: the
 * checksum lookup and the insert that follows must not interleave, or the same bytes
 * dropped into two sources at once would be ingested twice. A lock rather than a UNIQUE
 * index on consumed checksums: the buffer has one connection, so its work is serialized
 * anyway (only parsing gives up parallelism), and such an index could not be created on a
 * buffer that already holds a duplicated pair — the daemon would fail to boot.
 *
 * <p>Logs name files and the kind of a failure, never file content: X12 here is PHI.
 */
public final class FileConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(FileConsumer.class);

    private static final String INTERNAL = "internal: ";
    private static final String BUFFER_REJECTED = "buffer-rejected";

    // SQLite primary result codes (sqlite3.h); the xerial driver reports them as the error code.
    private static final int SQLITE_TOOBIG = 18;
    private static final int SQLITE_CONSTRAINT = 19;
    private static final int SQLITE_MISMATCH = 20;

    public enum Outcome { CONSUMED, DUPLICATE, ERROR, BACKPRESSURE, UNREADABLE, CHANGED }

    /**
     * What happened to one file. {@code fileId} is the {@code <path>@<hash>} identity, or
     * the bare path for {@link Outcome#BACKPRESSURE}/{@link Outcome#UNREADABLE}/
     * {@link Outcome#CHANGED} (no identity was established).
     */
    public record Result(Outcome outcome, String fileId, int transactions, String message) {
    }

    /** A file as read: its bytes (null when hashed as a stream without keeping them), sha256 and size. */
    private record Content(byte[] bytes, String checksum, long size) {
    }

    private final BufferStore buffer;
    private final RetentionSweeper sweeper;
    private final StructureResolver resolver;
    private final String consumedSuffix;
    private final String errorSuffix;
    private final boolean allowBareTransactionSets;
    private final long maxFileBytes;
    private final Clock clock;

    /** {@code sweeper} is null when retention is unbounded (no backpressure). */
    public FileConsumer(BufferStore buffer, RetentionSweeper sweeper, ModuleRuntimeConfig config,
                        StructureResolver resolver, Clock clock) {
        this.buffer = Objects.requireNonNull(buffer, "buffer");
        this.sweeper = sweeper;
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.consumedSuffix = config.consumedSuffix();
        this.errorSuffix = config.errorSuffix();
        this.allowBareTransactionSets = config.allowBareTransactionSets();
        this.maxFileBytes = config.maxFileBytes();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * True while live data is over {@code retention.maxBytes} (DESIGN §4.2 step 4). Sweeps
     * before saying so: the scheduled sweep may be minutes away, and refusing files it
     * would make room for stalls ingestion for nothing.
     */
    public boolean backpressure() {
        if (sweeper == null) {
            return false;
        }
        try {
            if (!sweeper.overCapacity()) {
                return false;
            }
            sweeper.sweep();
            return sweeper.overCapacity();
        } catch (SQLException e) {
            LOG.warn("backpressure check failed; assuming over capacity: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Consume a file the poller found stable: {@code stable} carries the {@code (size, mtime)}
     * the window saw and the first sighting. Never throws for a bad file (that is the
     * {@code ERROR} outcome); throws only when the buffer itself is unusable, or for a path
     * that is not an entry of {@code source}'s directory.
     */
    public synchronized Result consume(SourceConfig source, Path path, FileStability.Sighting stable)
            throws SQLException {
        final Path abs = path.toAbsolutePath().normalize();
        if (!source.contains(abs)) {
            throw new IllegalArgumentException(abs + " is not an entry of source '" + source.name() + "' ("
                + source.path() + ")");
        }
        final String filePath = abs.toString();
        final String fileName = abs.getFileName().toString();
        final Instant discoveredAt = stable.firstSeen();
        if (backpressure()) {
            LOG.warn("backpressure: buffer over capacity, leaving {} untouched", filePath);
            return new Result(Outcome.BACKPRESSURE, filePath, 0, "buffer over retention.maxBytes");
        }

        Content content;
        boolean outOfHeap = false;
        try {
            try {
                content = read(abs, stable, stable.size() <= maxFileBytes);
            } catch (OutOfMemoryError e) {
                // No room for the file's bytes. That recurs on every scan, so give the file an
                // identity by hashing it as a stream (constant memory) and send it to .error.
                outOfHeap = true;
                content = read(abs, stable, false);
            }
        } catch (IOException e) {
            // Nothing was hashed, so the file has no identity yet: the poller retries it next scan.
            return new Result(Outcome.UNREADABLE, filePath, 0, "io: " + e);
        }
        if (content == null) {
            LOG.info("{} changed after its stability window; left for the next scan", filePath);
            return new Result(Outcome.CHANGED, filePath, 0, "changed while being read");
        }
        // Readable now: an error row left by earlier failed reads no longer applies.
        buffer.deleteFile(FileRow.unreadableId(filePath));

        final long size = content.size();
        final String checksum = content.checksum();
        final Instant mtime = stable.mtime();
        final String fileId = FileRow.fileId(filePath, checksum);
        // Millisecond precision: the buffer stores epoch-millis, and the JSON overlay's receivedAt
        // must reproduce from the column (ops/validate repsAgree), so never keep the nanos.
        final Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);

        // 3a. identity: the same bytes at the same path (existing) / anywhere (prior).
        Optional<FileRow> existing = buffer.fileById(fileId);
        Optional<FileRow> prior = buffer.findFileByChecksum(checksum);
        if (prior.isPresent() && prior.get().status() != FileStatus.ERROR) {
            Path donePath = renameTarget(abs, consumedSuffix, discoveredAt);
            if (existing.isPresent() && existing.get().status() != FileStatus.ERROR) {
                // Redelivery: the row keeps its status; count it and re-acknowledge the copy.
                buffer.bumpRedelivery(fileId, null);
                acknowledge(abs, donePath, consumedSuffix, discoveredAt, fileId, false);
                int n = existing.get().redeliveryCount() + 1;
                LOG.info("redelivery #{}: {} ({}) re-landed unchanged; acknowledged", n, fileId, existing.get().status().wire());
                return new Result(Outcome.DUPLICATE, fileId, 0, "redelivery #" + n + " of " + fileId);
            }
            if (existing.isPresent()) {
                // These exact bytes errored at this path before but were ingested elsewhere since.
                buffer.deleteFile(fileId);
            }
            String message = "duplicate of " + prior.get().fileId();
            FileRow dup = new FileRow(0, fileId, filePath, fileName, source.name(), donePath.toString(), size, checksum,
                mtime, discoveredAt, now, FileStatus.DUPLICATE, null, 0, message, false, 0);
            buffer.insertFile(dup);
            acknowledge(abs, donePath, consumedSuffix, discoveredAt, fileId, true);
            LOG.info("duplicate: {} matches {} {} (checksum {}); acknowledged", fileId, prior.get().status().wire(),
                prior.get().fileId(), checksum);
            return new Result(Outcome.DUPLICATE, fileId, 0, message);
        }
        if (existing.isPresent()) {
            // Retry: the operator renamed an .error file back (or the same bytes re-landed).
            buffer.deleteFile(fileId);
            LOG.info("retry: {} previously errored ({}); consuming again", fileId, kind(existing.get().errorMessage()));
        }

        if (outOfHeap) {
            return fail(source, abs, fileId, content, stable,
                "too-large-for-heap: " + size + " bytes do not fit in the free heap; raise resources.memoryMb");
        }
        if (content.bytes() == null) {
            return fail(source, abs, fileId, content, stable,
                "too-large: " + size + " bytes exceeds maxFileBytes " + maxFileBytes);
        }
        Path donePath = renameTarget(abs, consumedSuffix, discoveredAt);
        final X12Parse.ParsedFile parsed;
        final int transactions;
        try {
            // 3b/3c. parse + materialize every ST..SE.
            parsed = X12Parse.parse(content.bytes(), allowBareTransactionSets, clock);
            List<TransactionRow> rows = new ArrayList<>();
            for (X12Parse.Transaction tx : parsed.transactions()) {
                rows.add(toRow(tx, parsed, fileId, fileName, source.name(), now));
            }
            FileRow file = new FileRow(0, fileId, filePath, fileName, source.name(), donePath.toString(), size, checksum,
                mtime, discoveredAt, now, FileStatus.CONSUMED, parsed.interchanges().size(), rows.size(), null, false, 0);
            transactions = buffer.consumeFile(file, rows);
        } catch (X12ParseException | DuplicateElementKeyException | RuntimeException
                 | OutOfMemoryError | StackOverflowError e) {
            // 3e. nothing was committed for this file (consumeFile rolls back on any Throwable).
            // Heap/stack exhaustion here is this file's size or shape, so it is this file's error;
            // retrying it every scan would only exhaust the JVM again.
            return fail(source, abs, fileId, content, stable, errorMessage(e));
        } catch (SQLException e) {
            if (!rejectsThisFile(e)) {
                throw e;   // the buffer itself is unusable (disk full, I/O error): retry the file later
            }
            // SQLite refused a value of this file's rows (e.g. a transaction set whose JSON is over
            // its 1e9-byte limit); rolled back like any other failure and just as permanent.
            return fail(source, abs, fileId, content, stable, BUFFER_REJECTED + ": " + e.getMessage());
        }

        // 3d. committed — rename is the ack.
        if (!acknowledge(abs, donePath, consumedSuffix, discoveredAt, fileId, true)) {
            LOG.error("rename after commit failed for {}; row marked rename_failed (the id guards re-consumption)", fileId);
        }
        if (!parsed.errors().isEmpty()) {
            LOG.info("{}: consumed with {} non-fatal parser error(s)", fileId, parsed.errors().size());
        }
        LOG.info("consumed {} ({} bytes, {} transaction(s), {}, envelope={})", fileId, size, transactions,
            parsed.gs08(), parsed.synthetic() ? TransactionRow.ENVELOPE_SYNTHETIC : TransactionRow.ENVELOPE_FILE);
        return new Result(Outcome.CONSUMED, fileId, transactions, null);
    }

    /**
     * Record a file that keeps failing to read as an {@code error} row
     * ({@link FileRow#unreadableId}) so it shows in {@code /files} and the health
     * {@code errored} count. The file is not renamed — it stays in place and is retried
     * every scan; the first successful read removes the row. Idempotent.
     */
    public synchronized void recordUnreadable(SourceConfig source, Path path, FileStability.Sighting seen,
                                              String message) throws SQLException {
        final Path abs = path.toAbsolutePath().normalize();
        final String filePath = abs.toString();
        final String id = FileRow.unreadableId(filePath);
        if (buffer.fileById(id).isPresent()) {
            return;
        }
        buffer.insertFile(new FileRow(0, id, filePath, abs.getFileName().toString(), source.name(), filePath,
            seen.size(), "", seen.mtime(), seen.firstSeen(), null, FileStatus.ERROR, null, null, message, false, 0));
    }

    /**
     * Read and hash the file without following a symlink, or return null when it is not
     * the file the stability window saw: {@code (size, mtime)} differs before or after the
     * read, or it grew or shrank while being read. Without {@code load} (a file over
     * {@code maxFileBytes}, or one whose bytes did not fit in the heap) the file is hashed by
     * streaming and its bytes are not kept. The allocation for the bytes throws
     * {@link OutOfMemoryError} when the heap cannot hold them.
     */
    private Content read(Path abs, FileStability.Sighting stable, boolean load) throws IOException {
        BasicFileAttributes before = Files.readAttributes(abs, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile() || !matches(before, stable)) {
            return null;
        }
        MessageDigest md = sha256Digest();
        byte[] bytes = null;
        long read;
        try (InputStream in = new DigestInputStream(Files.newInputStream(abs, LinkOption.NOFOLLOW_LINKS), md)) {
            if (!load) {
                read = in.transferTo(OutputStream.nullOutputStream());
            } else {
                // Exactly the stable size, then one probe byte: a file that is still being
                // appended to is caught here instead of being parsed truncated.
                bytes = new byte[(int) stable.size()];
                read = in.readNBytes(bytes, 0, bytes.length);
                if (in.read() != -1) {
                    return null;
                }
            }
        }
        BasicFileAttributes after = Files.readAttributes(abs, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (read != stable.size() || !matches(after, stable)) {
            return null;
        }
        return new Content(bytes, hex(md.digest()), read);
    }

    private static boolean matches(BasicFileAttributes attrs, FileStability.Sighting stable) {
        return attrs.size() == stable.size() && attrs.lastModifiedTime().toInstant().equals(stable.mtime());
    }

    /** Error row first, then {@code .error}: the same order as the consume path's row-then-rename. */
    private Result fail(SourceConfig source, Path abs, String fileId, Content content, FileStability.Sighting stable,
                        String message) throws SQLException {
        Path errorPath = renameTarget(abs, errorSuffix, stable.firstSeen());
        buffer.insertFile(new FileRow(0, fileId, abs.toString(), abs.getFileName().toString(), source.name(),
            errorPath.toString(), content.size(), content.checksum(), stable.mtime(), stable.firstSeen(), null,
            FileStatus.ERROR, null, null, message, false, 0));
        boolean renamed = acknowledge(abs, errorPath, errorSuffix, stable.firstSeen(), fileId, true);
        LOG.warn("error: {} -> {}: {}", fileId, renamed ? errorSuffix : "(rename failed)", kind(message));
        return new Result(Outcome.ERROR, fileId, 0, message);
    }

    /**
     * Rename to {@code planned} (or the next free name) and record where the bytes went;
     * false when the rename failed, which is recorded as {@code rename_failed}.
     * {@code rowHasPlanned} says the row already holds {@code planned} with the flag clear.
     */
    private boolean acknowledge(Path abs, Path planned, String suffix, Instant discoveredAt, String fileId,
                                boolean rowHasPlanned) throws SQLException {
        Path actual = moveNoClobber(abs, planned, suffix, discoveredAt);
        if (actual == null) {
            buffer.markRenameFailed(fileId);
            return false;
        }
        if (!rowHasPlanned || !actual.equals(planned)) {
            buffer.markRenamed(fileId, actual.toString());
        }
        return true;
    }

    /** Envelope overlay + materialized body → one {@link TransactionRow}. */
    TransactionRow toRow(X12Parse.Transaction tx, X12Parse.ParsedFile parsed, String fileId, String fileName,
                         String sourceName, Instant receivedAt) {
        // Per transaction, not per file: one interchange may carry groups of different guides.
        String gs08 = tx.gs08();
        String transactionType = TransactionTypes.transactionType(gs08, tx.st01());
        Optional<Materializer> materializer = resolver.materializerFor(gs08, parsed.separators());
        String schemaId = materializer.map(m -> m.index().tableSchemaId).orElse(StructureResolver.ENVELOPE_SCHEMA);
        String envelope = parsed.synthetic() ? TransactionRow.ENVELOPE_SYNTHETIC : TransactionRow.ENVELOPE_FILE;
        Instant interchangeAt = tx.interchange().interchangeAt().orElse(null);
        String elementKey = TransactionJson.elementKey(fileId, tx.interchange().controlNumber(),
            tx.group().controlNumber(), tx.st02());

        TransactionJson.Envelope env = new TransactionJson.Envelope(elementKey, fileId, fileName, sourceName,
            tx.interchange().controlNumber(), tx.group().controlNumber(), tx.st02(), gs08, transactionType,
            tx.interchange().senderId(), tx.interchange().receiverId(), interchangeAt, receivedAt, envelope,
            parsed.errors().size());
        Map<String, Object> json = TransactionJson.build(env, materializer, tx.loop());
        String mapped = TransactionJson.toJson(json);

        return TransactionRow.builder()
            .fileId(fileId).sourceName(sourceName)
            .gsControl(tx.group().controlNumber()).stControl(tx.st02())
            .elementKey(elementKey)
            .receivedAt(receivedAt)
            .isaControl(tx.interchange().controlNumber())
            .gs08(gs08).transactionType(transactionType)
            .senderId(tx.interchange().senderId()).receiverId(tx.interchange().receiverId())
            .interchangeAt(interchangeAt)
            .schemaId(schemaId)
            .rawX12(tx.rawX12().getBytes(StandardCharsets.UTF_8))
            .mappedJson(mapped)
            .parserErrorCount(parsed.errors().size())
            .envelope(envelope)
            .build();
    }

    static String errorMessage(Throwable e) {
        if (e instanceof X12ParseException || e instanceof DuplicateElementKeyException) {
            return e.getMessage();
        }
        return INTERNAL + e;
    }

    /**
     * Whether SQLite refused this file's rows rather than failed as a store: a value over its
     * length limit ({@code SQLITE_TOOBIG}), a constraint or a datatype mismatch. Disk full,
     * I/O, lock and corruption errors say nothing about the file and are left to propagate.
     */
    static boolean rejectsThisFile(SQLException e) {
        int primary = e.getErrorCode() & 0xff;
        return primary == SQLITE_TOOBIG || primary == SQLITE_CONSTRAINT || primary == SQLITE_MISMATCH;
    }

    /**
     * The loggable head of a stored error message: its kebab-case kind ({@code fatal},
     * {@code too-large}, ...) or, for an internal error, the exception class. The rest can
     * quote segments of the file.
     */
    static String kind(String message) {
        if (message == null) {
            return "unknown";
        }
        int from = message.startsWith(INTERNAL) ? INTERNAL.length() : 0;
        int colon = message.indexOf(':', from);
        return colon < 0 ? message : message.substring(0, colon);
    }

    /**
     * {@code <path><suffix>}, or {@code <path>.<discoveredAtEpochMillis><suffix>} when that
     * already exists (the same name delivered twice with different bytes, DESIGN §4.2 step
     * 3d/3e); a further {@code -<n>} disambiguates the vanishingly rare same-millisecond case.
     */
    static Path renameTarget(Path abs, String suffix, Instant discoveredAt) {
        String name = abs.getFileName().toString();
        Path plain = abs.resolveSibling(name + suffix);
        if (!Files.exists(plain, LinkOption.NOFOLLOW_LINKS)) {
            return plain;
        }
        String stamp = "." + (discoveredAt == null ? 0L : discoveredAt.toEpochMilli());
        Path stamped = abs.resolveSibling(name + stamp + suffix);
        for (int n = 1; Files.exists(stamped, LinkOption.NOFOLLOW_LINKS) && n < 10_000; n++) {
            stamped = abs.resolveSibling(name + stamp + "-" + n + suffix);
        }
        return stamped;
    }

    /**
     * Move {@code from} to {@code planned}, or to the next free {@link #renameTarget} name if
     * {@code planned} was taken meanwhile; returns where the bytes are now, or null when the
     * move failed. Never replaces an existing file: {@code ATOMIC_MOVE} is a bare rename(2),
     * which silently overwrites an existing {@code .done}/{@code .error} on Linux, while a
     * plain move refuses an existing target (and within one directory is still a rename).
     */
    static Path moveNoClobber(Path from, Path planned, String suffix, Instant discoveredAt) {
        Path target = planned;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                Files.move(from, target);
                return target;
            } catch (FileAlreadyExistsException taken) {
                target = renameTarget(from, suffix, discoveredAt);
            } catch (IOException e) {
                LOG.error("rename {} -> {} failed: {}", from, target.getFileName(), e.toString());
                return null;
            }
        }
        LOG.error("rename {} failed: every {} target tried was taken", from, suffix);
        return null;
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String hex(byte[] digest) {
        StringBuilder sb = new StringBuilder(64);
        for (byte b : digest) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}

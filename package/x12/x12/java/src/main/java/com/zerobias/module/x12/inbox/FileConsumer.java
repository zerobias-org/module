package com.zerobias.module.x12.inbox;

import com.zerobias.module.x12.ModuleRuntimeConfig;
import com.zerobias.module.x12.SourceConfig;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.DuplicateElementKeyException;
import com.zerobias.module.x12.buffer.FileRow;
import com.zerobias.module.x12.buffer.FileStatus;
import com.zerobias.module.x12.buffer.RetentionSweeper;
import com.zerobias.module.x12.buffer.TransactionRow;
import com.zerobias.module.x12.materializer.EntityGraph;
import com.zerobias.module.x12.producer.mapping.EntityMapping;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Consumes one stable inbox file (DESIGN §4.2 step 3): sha256 → identity
 * ({@code fileId = <path>@<hash12>}) → duplicate / redelivery / retry resolution → parse →
 * materialize → {@link BufferStore#consumeFile} (one SQL transaction) → rename
 * {@code .done}. <b>Rename is the ack</b>: the file is renamed only after the commit
 * returns; a rename failure after commit is logged and recorded
 * ({@link BufferStore#markRenameFailed}) — re-hashing the file next scan finds the same
 * id and treats it as a redelivery, so the rows are never duplicated. Parse failure →
 * rename {@code .error} + a {@code files} row with {@code status=error} and the message
 * (imsweb fatal errors included). Known checksum ({@code consumed}|{@code duplicate}) →
 * {@code files} row with {@code status=duplicate} (or a {@code redelivery_count} bump when
 * it is the same path), no transactions, rename {@code .done}. Known checksum with
 * {@code status=error} at the same path → the error row is deleted and the file is
 * consumed again (an operator renamed it back). A rename target that already exists gets
 * {@code .<discoveredAtEpochMillis>} interposed, and a rename never replaces an existing
 * file (a target taken meanwhile gets the next free name). Backpressure
 * ({@link RetentionSweeper#overCapacity()}) → touch nothing.
 *
 * <p><b>Per-file isolation.</b> The read never follows a symlink and never holds more than
 * {@code maxFileBytes}: the size comes from {@code stat} before a byte is read, a bigger file
 * is hashed by streaming (constant memory, so it still gets an identity) and sent to
 * {@code .error} as {@code too-large}; a file whose bytes do not fit in the heap is handled the
 * same way as {@code too-large-for-heap}. A file whose size or mtime differs from what the
 * stability window saw, or changes while it is being read, is left alone for the next scan
 * ({@link Outcome#CHANGED}). Everything that belongs to the file — a parse error, two sets
 * sharing an element key, the parser exhausting heap or stack on it, SQLite refusing one of
 * its rows ({@link #rejectsThisFile}) — becomes that file's {@code .error}. Only a failure of
 * the buffer itself (disk full, I/O, corruption, a schema that no longer matches its INSERTs)
 * propagates as {@link SQLException}, leaving the file in place: that is not the file's fault,
 * and the poller surfaces it in {@code /healthz} instead of blaming every file in turn.
 *
 * <p>Stateless apart from its collaborators; one instance is shared by every poller.
 */
public final class FileConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(FileConsumer.class);

    public enum Outcome { CONSUMED, DUPLICATE, ERROR, BACKPRESSURE, UNREADABLE, CHANGED }

    /**
     * What happened to one file. {@code fileId} is the {@code <path>@<hash>} identity, or
     * the bare path for {@link Outcome#BACKPRESSURE}/{@link Outcome#UNREADABLE}/
     * {@link Outcome#CHANGED} (no identity was established).
     */
    public record Result(Outcome outcome, String fileId, int transactions, String message) {
    }

    /** A file as read: its bytes (null when hashed as a stream without keeping them), sha256, size, mtime. */
    private record Content(byte[] bytes, String checksum, long size, Instant mtime) {
    }

    private static final String INTERNAL = "internal: ";

    // SQLite result codes (sqlite3.h). The xerial driver reports the extended code.
    private static final int SQLITE_TOOBIG = 18;
    private static final int SQLITE_CONSTRAINT_PRIMARYKEY = 1555;
    private static final int SQLITE_CONSTRAINT_UNIQUE = 2067;

    private final BufferStore buffer;
    private final RetentionSweeper sweeper;
    private final StructureResolver resolver;
    private final String consumedSuffix;
    private final String errorSuffix;
    private final boolean allowBareTransactionSets;
    private final long maxFileBytes;
    private final Clock clock;

    public FileConsumer(BufferStore buffer, RetentionSweeper sweeper, ModuleRuntimeConfig config,
                        StructureResolver resolver, Clock clock) {
        this.buffer = buffer;
        this.sweeper = sweeper;
        this.resolver = resolver == null ? new StructureResolver() : resolver;
        this.consumedSuffix = config.consumedSuffix();
        this.errorSuffix = config.errorSuffix();
        this.allowBareTransactionSets = config.allowBareTransactionSets();
        this.maxFileBytes = config.maxFileBytes();
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /** True while the buffer is over its byte ceiling (DESIGN §4.2 step 4). */
    public boolean backpressure() {
        if (sweeper == null) {
            return false;
        }
        try {
            return sweeper.overCapacity();
        } catch (SQLException e) {
            LOG.warn("backpressure check failed; assuming over capacity: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Consume a file with no stability sighting to hold it to (tests, tools): it is still
     * stat'ed before and after the read. See {@link #consume(SourceConfig, Path, FileStability.Sighting)}.
     */
    public Result consume(SourceConfig source, Path path, Instant discoveredAt) throws SQLException {
        return consume(source, path, discoveredAt, null);
    }

    /**
     * Consume a file the poller found stable; {@code stable} carries the {@code (size, mtime)}
     * the window saw and the first sighting. Never throws for a bad file (that is the
     * {@code ERROR} outcome); throws only when the buffer itself is unusable.
     */
    public Result consume(SourceConfig source, Path path, FileStability.Sighting stable) throws SQLException {
        return consume(source, path, stable.firstSeen(), stable);
    }

    private synchronized Result consume(SourceConfig source, Path path, Instant discoveredAt,
                                        FileStability.Sighting stable) throws SQLException {
        final Path abs = path.toAbsolutePath().normalize();
        final String filePath = abs.toString();
        final String fileName = abs.getFileName().toString();
        if (backpressure()) {
            LOG.warn("backpressure: buffer over capacity, leaving {} untouched", filePath);
            return new Result(Outcome.BACKPRESSURE, filePath, 0, "buffer over retention.maxBytes");
        }

        Content content;
        boolean outOfHeap = false;
        try {
            try {
                content = read(abs, stable, true);
            } catch (OutOfMemoryError e) {
                // No room for this file's bytes. That recurs on every scan, so give the file an
                // identity by hashing it as a stream (constant memory) and send it to .error.
                outOfHeap = true;
                content = read(abs, stable, false);
            }
        } catch (IOException e) {
            // Nothing was hashed, so the file has no identity yet: leave it for the next scan.
            LOG.warn("unreadable: {}: {}; retrying next scan", filePath, e.toString());
            return new Result(Outcome.UNREADABLE, filePath, 0, "io: " + e);
        }
        if (content == null) {
            LOG.info("{} changed after its stability window (or is not a regular file); left for the next scan",
                filePath);
            return new Result(Outcome.CHANGED, filePath, 0, "changed while being read");
        }
        final long size = content.size();
        final String checksum = content.checksum();
        final Instant mtime = content.mtime();
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
                acknowledge(abs, donePath, consumedSuffix, discoveredAt, fileId);
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
            acknowledge(abs, donePath, consumedSuffix, discoveredAt, fileId);
            LOG.info("duplicate: {} matches {} {} (checksum {}); acknowledged", fileId, prior.get().status().wire(),
                prior.get().fileId(), checksum);
            return new Result(Outcome.DUPLICATE, fileId, 0, message);
        }
        if (existing.isPresent()) {
            // Retry: the operator renamed an .error file back (or the same bytes re-landed).
            buffer.deleteFile(fileId);
            LOG.info("retry: {} previously errored ({}); consuming again", fileId, existing.get().errorMessage());
        }

        if (outOfHeap) {
            return fail(source, abs, fileId, content, discoveredAt,
                "too-large-for-heap: " + size + " bytes do not fit in the free heap; raise resources.memoryMb");
        }
        if (content.bytes() == null) {
            return fail(source, abs, fileId, content, discoveredAt,
                "too-large: " + size + " bytes exceeds maxFileBytes " + maxFileBytes);
        }

        Path donePath = renameTarget(abs, consumedSuffix, discoveredAt);
        final X12Parse.ParsedFile parsed;
        final int transactions;
        try {
            // 3b/3c. parse + materialize every ST..SE.
            parsed = X12Parse.parse(content.bytes(), allowBareTransactionSets, clock);
            List<TransactionRow> rows = new ArrayList<>();
            Map<String, List<EntityGraph.Entity>> graphs = new LinkedHashMap<>();
            Map<String, Map<String, EntityGraph.Value>> dims = new LinkedHashMap<>();
            for (X12Parse.Transaction tx : parsed.transactions()) {
                rows.add(toRow(tx, parsed, fileId, fileName, source.name(), now, graphs));
            }
            // Resolve the guide's business dimensions once per transaction set: the payer lives
            // in the header, so segmenting claims by payer must not mean walking up per claim.
            for (Map.Entry<String, List<EntityGraph.Entity>> e : graphs.entrySet()) {
                final Map<String, EntityGraph.Value> resolved = EntityMapping.dimensions(parsed.gs08(), e.getValue());
                if (!resolved.isEmpty()) {
                    dims.put(e.getKey(), resolved);
                }
            }
            FileRow file = new FileRow(0, fileId, filePath, fileName, source.name(), donePath.toString(), size, checksum,
                mtime, discoveredAt, now, FileStatus.CONSUMED, parsed.interchanges().size(), rows.size(), null, false, 0);
            // 3d. COMMIT (graph + dims with their rows, DESIGN §8.4) — then rename: rename is the ack.
            transactions = buffer.consumeFile(file, rows, graphs, dims);
        } catch (X12ParseException | DuplicateElementKeyException | RuntimeException
                 | OutOfMemoryError | StackOverflowError e) {
            // 3e. nothing was committed for this file (consumeFile rolls back on any Throwable).
            // A duplicate element key is two sets of this file sharing ISA13/GS06/ST02:
            // acknowledging it .done would silently drop one of them. Heap/stack exhaustion here
            // is this file's size or shape; retrying it every scan would only exhaust it again.
            return fail(source, abs, fileId, content, discoveredAt, errorMessage(e));
        } catch (SQLException e) {
            if (!rejectsThisFile(e)) {
                throw e;   // the buffer itself is unusable: leave the file, surface the failure
            }
            return fail(source, abs, fileId, content, discoveredAt, "buffer-rejected: " + e.getMessage());
        }

        if (!acknowledge(abs, donePath, consumedSuffix, discoveredAt, fileId)) {
            LOG.error("rename after commit failed for {}; row marked rename_failed (the id guards re-consumption)",
                fileId);
        }
        if (!parsed.errors().isEmpty()) {
            LOG.info("{}: consumed with {} non-fatal parser error(s): {}", fileId, parsed.errors().size(), parsed.errors());
        }
        LOG.info("consumed {} ({} bytes, {} transaction(s), {}, envelope={})", fileId, size, transactions,
            parsed.gs08(), parsed.synthetic() ? TransactionRow.ENVELOPE_SYNTHETIC : TransactionRow.ENVELOPE_FILE);
        return new Result(Outcome.CONSUMED, fileId, transactions, null);
    }

    /**
     * Read and hash the file without following a symlink, or return null when it is not the
     * file the stability window saw: not a regular file, {@code (size, mtime)} different from
     * {@code stable} (when given) or from the stat taken just before the read, or it grew or
     * shrank while being read. A file over {@code maxFileBytes} (decided from that first stat,
     * before anything is read), or any file when {@code load} is false, is hashed by streaming
     * and its bytes are not kept. The allocation for the bytes throws {@link OutOfMemoryError}
     * when the heap cannot hold them.
     */
    private Content read(Path abs, FileStability.Sighting stable, boolean load) throws IOException {
        BasicFileAttributes before = Files.readAttributes(abs, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile()) {
            return null;   // a symlink (never followed), a directory, a device
        }
        if (stable != null && !matches(before, stable.size(), stable.mtime())) {
            return null;
        }
        final long size = before.size();
        final Instant mtime = before.lastModifiedTime().toInstant();
        final boolean keep = load && size <= maxFileBytes;
        MessageDigest md = sha256Digest();
        byte[] bytes = null;
        long read;
        try (InputStream in = new DigestInputStream(Files.newInputStream(abs, LinkOption.NOFOLLOW_LINKS), md)) {
            if (!keep) {
                read = in.transferTo(OutputStream.nullOutputStream());
            } else {
                // Exactly the stat'ed size, then one probe byte: a file still being appended to
                // is caught here instead of being parsed truncated.
                bytes = new byte[(int) size];
                read = in.readNBytes(bytes, 0, bytes.length);
                if (in.read() != -1) {
                    return null;
                }
            }
        }
        BasicFileAttributes after = Files.readAttributes(abs, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (read != size || !matches(after, size, mtime)) {
            return null;
        }
        return new Content(bytes, hex(md.digest()), read, mtime);
    }

    private static boolean matches(BasicFileAttributes attrs, long size, Instant mtime) {
        return attrs.isRegularFile() && attrs.size() == size && attrs.lastModifiedTime().toInstant().equals(mtime);
    }

    /** Error row first, then {@code .error}: the same order as the consume path's row-then-rename. */
    private Result fail(SourceConfig source, Path abs, String fileId, Content content, Instant discoveredAt,
                        String message) throws SQLException {
        Path errorPath = renameTarget(abs, errorSuffix, discoveredAt);
        buffer.insertFile(new FileRow(0, fileId, abs.toString(), abs.getFileName().toString(), source.name(),
            errorPath.toString(), content.size(), content.checksum(), content.mtime(), discoveredAt, null,
            FileStatus.ERROR, null, null, message, false, 0));
        boolean renamed = acknowledge(abs, errorPath, errorSuffix, discoveredAt, fileId);
        LOG.warn("error: {} -> {}: {}", fileId, renamed ? errorSuffix : "(rename failed)", message);
        return new Result(Outcome.ERROR, fileId, 0, message);
    }

    /**
     * Rename to {@code planned} (or the next free name if it was taken meanwhile) and record
     * where the bytes went; false when the rename failed, which is recorded as
     * {@code rename_failed}. The row already holds {@code planned} (or, for a redelivery, its
     * old location, which is then updated).
     */
    private boolean acknowledge(Path abs, Path planned, String suffix, Instant discoveredAt, String fileId)
            throws SQLException {
        Path actual = moveNoClobber(abs, planned, suffix, discoveredAt);
        if (actual == null) {
            buffer.markRenameFailed(fileId);
            return false;
        }
        buffer.updateFilePath(fileId, actual.toString());
        return true;
    }

    /** Envelope overlay + materialized body → one {@link TransactionRow}. */
    TransactionRow toRow(X12Parse.Transaction tx, X12Parse.ParsedFile parsed, String fileId, String fileName,
                         String sourceName, Instant receivedAt, Map<String, List<EntityGraph.Entity>> graphs) {
        String gs08 = parsed.gs08();
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
        // Flatten the same materialized tree into the queryable object graph. Built from the
        // body alone — the envelope columns live on the transaction row, so duplicating them
        // as entity values would make every filter ambiguous about which copy it hit.
        if (materializer.isPresent() && tx.loop() != null) {
            final Materializer m = materializer.get();
            graphs.put(elementKey, EntityGraph.flatten(m.index(), m.materializeTransaction(tx.loop())));
        }

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
            .parserErrorCount(parsed.errors().size())
            .envelope(envelope)
            .build();
    }

    static String errorMessage(Throwable e) {
        if (e instanceof X12ParseException || e instanceof DuplicateElementKeyException) {
            return e.getMessage();
        }
        if (e instanceof IOException) {
            return "io: " + e;
        }
        return INTERNAL + e;
    }

    /**
     * Whether SQLite refused this file's rows rather than failed as a store: a value over its
     * length limit ({@code SQLITE_TOOBIG}), or a UNIQUE / PRIMARY KEY clash on one of this
     * file's keys. Everything else — disk full, I/O, locking, corruption, and NOT NULL / CHECK
     * constraints (those mean the table no longer matches its INSERTs, as when a stale
     * {@code mapped_json NOT NULL} column stopped all ingest) — is a buffer failure.
     */
    public static boolean rejectsThisFile(SQLException e) {
        if (e instanceof DuplicateElementKeyException) {
            return true;
        }
        int code = e.getErrorCode();
        if (e instanceof org.sqlite.SQLiteException se) {
            code = se.getResultCode().code;
        }
        return code == SQLITE_TOOBIG || code == SQLITE_CONSTRAINT_UNIQUE || code == SQLITE_CONSTRAINT_PRIMARYKEY;
    }

    /** The absolute, normalized discovery path of a file (the {@code file_path} column). */
    static String filePath(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    /** The {@code <path>@<hash12>} identity a file at {@code path} with these bytes gets. */
    public static String fileId(Path path, byte[] bytes) {
        return FileRow.fileId(filePath(path), sha256(bytes));
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
     * which silently overwrites an existing {@code .done}/{@code .error} on Linux, while a plain
     * move refuses an existing target (and within one directory is still a rename).
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
                LOG.error("rename {} -> {} failed: {}", from, target, e.toString());
                return null;
            }
        }
        LOG.error("rename {} failed: every {} target tried was taken", from, suffix);
        return null;
    }

    public static String sha256(byte[] bytes) {
        return hex(sha256Digest().digest(bytes));
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

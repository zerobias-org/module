package com.zerobias.module.x12.inbox;

import com.zerobias.module.x12.ModuleRuntimeConfig;
import com.zerobias.module.x12.SourceConfig;
import com.zerobias.module.x12.buffer.BufferStore;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
 * {@code .<discoveredAtEpochMillis>} interposed. Backpressure
 * ({@link RetentionSweeper#overCapacity()}) → touch nothing.
 *
 * <p>Stateless apart from its collaborators; one instance is shared by every poller.
 */
public final class FileConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(FileConsumer.class);

    public enum Outcome { CONSUMED, DUPLICATE, ERROR, BACKPRESSURE, UNREADABLE }

    /**
     * What happened to one file. {@code fileId} is the {@code <path>@<hash>} identity, or
     * the bare path for {@link Outcome#BACKPRESSURE}/{@link Outcome#UNREADABLE} (no bytes
     * were hashed).
     */
    public record Result(Outcome outcome, String fileId, int transactions, String message) {
    }

    private final BufferStore buffer;
    private final RetentionSweeper sweeper;
    private final StructureResolver resolver;
    private final String consumedSuffix;
    private final String errorSuffix;
    private final boolean allowBareTransactionSets;
    private final Clock clock;

    public FileConsumer(BufferStore buffer, RetentionSweeper sweeper, ModuleRuntimeConfig config,
                        StructureResolver resolver, Clock clock) {
        this.buffer = buffer;
        this.sweeper = sweeper;
        this.resolver = resolver == null ? new StructureResolver() : resolver;
        this.consumedSuffix = config.consumedSuffix();
        this.errorSuffix = config.errorSuffix();
        this.allowBareTransactionSets = config.allowBareTransactionSets();
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
     * Consume a file that the poller has found stable. {@code discoveredAt} is the first
     * sighting. Never throws for a bad file (that is the {@code ERROR} outcome); throws only
     * when the buffer itself is unusable.
     */
    public Result consume(SourceConfig source, Path path, Instant discoveredAt) throws SQLException {
        final Path abs = path.toAbsolutePath().normalize();
        final String filePath = abs.toString();
        final String fileName = abs.getFileName().toString();
        if (backpressure()) {
            LOG.warn("backpressure: buffer over capacity, leaving {} untouched", filePath);
            return new Result(Outcome.BACKPRESSURE, filePath, 0, "buffer over retention.maxBytes");
        }

        final byte[] bytes;
        final Instant mtime;
        try {
            bytes = Files.readAllBytes(abs);
            mtime = Files.getLastModifiedTime(abs).toInstant();
        } catch (IOException e) {
            // Nothing was hashed, so the file has no identity yet: leave it for the next scan.
            LOG.warn("unreadable: {}: {}; retrying next scan", filePath, e.toString());
            return new Result(Outcome.UNREADABLE, filePath, 0, "io: " + e);
        }
        final long size = bytes.length;
        final String checksum = sha256(bytes);
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
                if (rename(abs, donePath)) {
                    buffer.updateFilePath(fileId, donePath.toString());
                } else {
                    buffer.markRenameFailed(fileId);
                }
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
            if (!rename(abs, donePath)) {
                buffer.markRenameFailed(fileId);
            }
            LOG.info("duplicate: {} matches {} {} (checksum {}); acknowledged", fileId, prior.get().status().wire(),
                prior.get().fileId(), checksum);
            return new Result(Outcome.DUPLICATE, fileId, 0, message);
        }
        if (existing.isPresent()) {
            // Retry: the operator renamed an .error file back (or the same bytes re-landed).
            buffer.deleteFile(fileId);
            LOG.info("retry: {} previously errored ({}); consuming again", fileId, existing.get().errorMessage());
        }

        Path donePath = renameTarget(abs, consumedSuffix, discoveredAt);
        try {
            // 3b/3c. parse + materialize every ST..SE.
            X12Parse.ParsedFile parsed = X12Parse.parse(bytes, allowBareTransactionSets, clock);
            List<TransactionRow> rows = new ArrayList<>();
            Map<String, List<EntityGraph.Entity>> graphs = new LinkedHashMap<>();
            Map<String, Map<String, EntityGraph.Value>> dims = new LinkedHashMap<>();
            for (X12Parse.Transaction tx : parsed.transactions()) {
                rows.add(toRow(tx, parsed, fileId, fileName, source.name(), now, graphs));
            }
            // Resolve the guide's business dimensions once per transaction set: the payer lives
            // in the header, so segmenting claims by payer must not mean walking up per claim.
            for (Map.Entry<String, List<EntityGraph.Entity>> e : graphs.entrySet()) {
                final Map<String, EntityGraph.Value> resolved = resolveDimensions(parsed.gs08(), e.getValue());
                if (!resolved.isEmpty()) {
                    dims.put(e.getKey(), resolved);
                }
            }
            FileRow file = new FileRow(0, fileId, filePath, fileName, source.name(), donePath.toString(), size, checksum,
                mtime, discoveredAt, now, FileStatus.CONSUMED, parsed.interchanges().size(), rows.size(), null, false, 0);

            // 3d. COMMIT, then rename — rename is the ack.
            // The object graph commits with its transaction rows (DESIGN §8.4).
            int inserted = buffer.consumeFile(file, rows, graphs, dims);
            if (!rename(abs, donePath)) {
                buffer.markRenameFailed(fileId);
                LOG.error("rename after commit failed for {} -> {}; row marked rename_failed (the id guards re-consumption)",
                    fileId, donePath);
            }
            if (inserted != rows.size()) {
                LOG.warn("{}: {} of {} transaction(s) already present by element key; not re-inserted", fileId,
                    rows.size() - inserted, rows.size());
            }
            if (!parsed.errors().isEmpty()) {
                LOG.info("{}: consumed with {} non-fatal parser error(s): {}", fileId, parsed.errors().size(), parsed.errors());
            }
            LOG.info("consumed {} ({} bytes, {} transaction(s), {}, envelope={})", fileId, size, rows.size(),
                parsed.gs08(), parsed.synthetic() ? TransactionRow.ENVELOPE_SYNTHETIC : TransactionRow.ENVELOPE_FILE);
            return new Result(Outcome.CONSUMED, fileId, rows.size(), null);
        } catch (X12ParseException | RuntimeException e) {
            // 3e. before commit: nothing was written for this file; rename .error + record.
            String message = errorMessage(e);
            Path errorPath = renameTarget(abs, errorSuffix, discoveredAt);
            LOG.warn("error: {} -> {}: {}", fileId, errorPath.getFileName(), message);
            boolean renamed = rename(abs, errorPath);
            FileRow err = new FileRow(0, fileId, filePath, fileName, source.name(), renamed ? errorPath.toString() : filePath,
                size, checksum, mtime, discoveredAt, null, FileStatus.ERROR, null, null, message, !renamed, 0);
            buffer.insertFile(err);
            return new Result(Outcome.ERROR, fileId, 0, message);
        }
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
        String elementKey = TransactionJson.elementKey(fileId, tx.group().controlNumber(), tx.st02());

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

    /** The guide's declared dimensions, read off the transaction root (DESIGN §8.5). */
    private Map<String, EntityGraph.Value> resolveDimensions(String gs08, List<EntityGraph.Entity> graph) {
        if (graph == null || graph.isEmpty()) {
            return Map.of();
        }
        final List<EntityMapping> mappings = mappingCache.computeIfAbsent(gs08 == null ? "" : gs08,
            EntityMapping::forGuide);
        if (mappings.isEmpty()) {
            return Map.of();
        }
        final EntityGraph.Entity root = graph.get(0);
        final Map<String, EntityGraph.Value> out = new LinkedHashMap<>();
        for (EntityMapping.Dimension d : mappings.get(0).dimensions()) {
            final EntityGraph.Value v = EntityMapping.read(graph, root, d.path());
            if (v != null) {
                out.put(d.name(), v);
            }
        }
        return out;
    }

    private final Map<String, List<EntityMapping>> mappingCache = new LinkedHashMap<>();

    static String errorMessage(Exception e) {
        if (e instanceof X12ParseException pe) {
            return pe.getMessage();
        }
        if (e instanceof IOException) {
            return "io: " + e;
        }
        return "internal: " + e;
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
        if (!Files.exists(plain)) {
            return plain;
        }
        String stamp = "." + (discoveredAt == null ? 0L : discoveredAt.toEpochMilli());
        Path stamped = abs.resolveSibling(name + stamp + suffix);
        for (int n = 1; Files.exists(stamped) && n < 10_000; n++) {
            stamped = abs.resolveSibling(name + stamp + "-" + n + suffix);
        }
        return stamped;
    }

    static boolean rename(Path from, Path to) {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException | UnsupportedOperationException atomicFailed) {
            try {
                Files.move(from, to);
                return true;
            } catch (IOException e) {
                LOG.error("rename {} -> {} failed: {}", from, to, e.toString());
                return false;
            }
        }
    }

    public static String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(bytes);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}

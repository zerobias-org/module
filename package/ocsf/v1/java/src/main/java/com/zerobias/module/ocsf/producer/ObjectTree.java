package com.zerobias.module.ocsf.producer;

import com.zerobias.module.ocsf.SourceConfig;
import com.zerobias.module.ocsf.buffer.BufferStore;
import com.zerobias.module.ocsf.buffer.FileRow;
import com.zerobias.module.ocsf.health.PollerStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The DataProducer object hierarchy (DESIGN §3) — files only; events are read through the
 * OCSF interface operations, never as DataProducer objects.
 *
 * <pre>
 * /                              container (root; id == name == "/")
 * └─ /ocsf-receiver              container
 *    ├─ /inbox                   container → the live source directories ({@link InboxFiles})
 *    ├─ /files                   container → /files/&lt;fileId&gt; ["document","binary"]
 *    └─ /stats                   document
 * </pre>
 *
 * <p><b>Id encoding.</b> {@code fileId} ({@code <absolute path>@<hash>}) contains {@code /}, so
 * it is embedded percent-encoded for {@code /} and {@code %} only ({@link #encodeSegment});
 * every id round-trips through {@code getObject}/{@code getChildren} verbatim.
 */
public final class ObjectTree {

    public static final String ROOT = "/";
    public static final String RECEIVER = "/ocsf-receiver";

    static final String FILES = RECEIVER + "/files";
    static final String STATS = RECEIVER + "/stats";

    static final String STATS_SCHEMA = "schema:shared:ocsf.receiver-stats";
    static final String FILE_SCHEMA = "schema:shared:ocsf.file";
    static final List<String> FILE_CLASSES = List.of("document", "binary");

    /** One page of children plus the total. */
    public record ChildPage(List<Map<String, Object>> items, long total) {
    }

    private final BufferStore buffer;
    private final PollerStatus poller;
    private final InboxFiles inbox;

    /**
     * @param poller         feeds {@code /stats}
     * @param consumedSuffix the {@code .done} suffix, for the {@code ingest} field on live file nodes
     * @param sources        the configured inbox directories, the live {@code /inbox/<source>} branch
     * @param errorSuffix    the {@code .error} suffix, likewise
     */
    public ObjectTree(BufferStore buffer, PollerStatus poller, String consumedSuffix,
            List<SourceConfig> sources, String errorSuffix) {
        this.buffer = Objects.requireNonNull(buffer, "buffer");
        this.poller = Objects.requireNonNull(poller, "poller");
        this.inbox = new InboxFiles(Objects.requireNonNull(sources, "sources"),
            Objects.requireNonNull(consumedSuffix, "consumedSuffix"), Objects.requireNonNull(errorSuffix, "errorSuffix"));
    }

    // --- id encoding ---------------------------------------------------------

    /** Percent-encode {@code %} and {@code /} so a value can sit in one path segment. */
    public static String encodeSegment(String value) {
        return value.replace("%", "%25").replace("/", "%2F");
    }

    /** Inverse of {@link #encodeSegment}; any other {@code %xx} sequence is left as-is. */
    public static String decodeSegment(String segment) {
        StringBuilder sb = new StringBuilder(segment.length());
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c == '%' && i + 2 < segment.length()) {
                String hex = segment.substring(i + 1, i + 3).toUpperCase();
                if ("2F".equals(hex) || "25".equals(hex)) {
                    sb.append("2F".equals(hex) ? '/' : '%');
                    i += 2;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    static String fileObjectId(String fileId) {
        return FILES + "/" + encodeSegment(fileId);
    }

    /** The fileId of an id directly under {@code /files/}, or null when not one. */
    private static String parseFileId(String id) {
        if (!id.startsWith(FILES + "/") || id.length() == FILES.length() + 1) {
            return null;
        }
        return decodeSegment(id.substring(FILES.length() + 1));
    }

    private FileRow requireFile(String fileId, String objectId) throws SQLException {
        Optional<FileRow> f = buffer.fileById(fileId);
        if (f.isEmpty()) {
            throw ProducerException.noSuchObject(objectId);
        }
        return f.get();
    }

    // --- objects and children ------------------------------------------------

    /** Object metadata for {@code id}, or throw {@code noSuchObjectError}. */
    public Map<String, Object> object(String id) throws SQLException {
        switch (id) {
            case ROOT:
                return container(ROOT, ROOT);
            case RECEIVER:
                return container(RECEIVER, "ocsf-receiver");
            case FILES:
                return container(FILES, "files");
            case STATS: {
                Map<String, Object> o = base(STATS, "stats", List.of("document"));
                o.put("documentSchema", STATS_SCHEMA);
                return o;
            }
            default:
                if (InboxFiles.owns(id)) {
                    return inbox.object(id);   // a fresh stat; never cached
                }
                String fileId = parseFileId(id);
                if (fileId != null) {
                    return fileNode(requireFile(fileId, id));
                }
                throw ProducerException.noSuchObject(id);
        }
    }

    /** Direct children of {@code id}, or throw {@code noSuchObjectError}. */
    public List<Map<String, Object>> children(String id) throws SQLException {
        switch (id) {
            case ROOT:
                return List.of(object(RECEIVER));
            case RECEIVER:
                return List.of(object(InboxFiles.INBOX), object(FILES), object(STATS));
            case FILES:
                return childPage(FILES, Integer.MAX_VALUE, 0).items();
            default:
                if (InboxFiles.owns(id)) {
                    return inbox.children(id);   // a fresh readdir; never cached
                }
                object(id);   // a leaf has no children; an unknown id is a 404
                return List.of();
        }
    }

    /**
     * One page of {@code id}'s children and the total. {@code /files} is paged in SQL (newest
     * discovery first): it is every files row ever recorded — the audit trail, never evicted —
     * so reading it whole to serve one page would grow without limit. Every other branch is
     * bounded and is paged in memory.
     */
    public ChildPage childPage(String id, int limit, int offset) throws SQLException {
        if (FILES.equals(id)) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (FileRow f : buffer.fileRows(null, limit, offset)) {
                items.add(fileNode(f));
            }
            return new ChildPage(items, buffer.countFilesWhere(null));
        }
        List<Map<String, Object>> all = children(id);
        List<Map<String, Object>> page = offset >= all.size()
            ? List.of()
            : all.subList(offset, (int) Math.min(all.size(), (long) offset + limit));
        return new ChildPage(page, all.size());
    }

    // --- documents and binary ------------------------------------------------

    /** The body of {@code /stats} or of a {@code /files/<fileId>} node (its {@code files} row). */
    public Map<String, Object> documentData(String id) throws SQLException {
        if (STATS.equals(id)) {
            return stats();
        }
        String fileId = parseFileId(id);
        if (fileId != null) {
            return fileDocument(requireFile(fileId, id));
        }
        object(id);
        throw ProducerException.unsupported("Object is not a document: " + id);
    }

    private Map<String, Object> stats() throws SQLException {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("up", poller.up());
        poller.lastScan().ifPresent(t -> out.put("lastScan", t.toString()));
        if (poller.lastConsumed().isPresent()) {
            out.put("lastConsumed", poller.lastConsumed().get().toString());
        } else {
            OptionalLong last = buffer.lastConsumedMillis();
            if (last.isPresent()) {
                out.put("lastConsumed", Instant.ofEpochMilli(last.getAsLong()).toString());
            }
        }
        out.put("backpressure", poller.backpressure());
        out.put("eventCount", buffer.eventCount());
        out.put("classCount", buffer.distinctClassUids().size());
        out.put("fileCount", buffer.fileCount());
        out.put("walBytes", buffer.walBytes());
        out.put("dbSizeBytes", buffer.dbSizeBytes());
        List<Map<String, Object>> sources = new ArrayList<>();
        for (PollerStatus.SourceStatus s : poller.sources()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", s.name());
            m.put("path", s.path());
            m.put("writable", s.writable());
            m.put("pending", s.pending());
            m.put("errored", s.errored());
            sources.add(m);
        }
        out.put("sources", sources);
        return out;
    }

    /** A {@code files} row as a document: status, counts and the rejection reasons. */
    private static Map<String, Object> fileDocument(FileRow f) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("fileId", f.fileId());
        d.put("filePath", f.filePath() == null ? FileRow.pathOf(f.fileId()) : f.filePath());
        d.put("fileName", f.fileName());
        d.put("sourceName", f.sourceName());
        putIfPresent(d, "currentPath", f.currentPath());
        d.put("size", f.sizeBytes());
        d.put("mimeType", BinaryContent.mimeOf(f.fileName()));
        d.put("checksum", f.checksum());
        putIfPresent(d, "modified", f.fileMtime());
        putIfPresent(d, "created", f.discoveredAt());
        putIfPresent(d, "consumedAt", f.consumedAt());
        d.put("status", f.status().wire());
        putIfPresent(d, "eventCount", f.eventCount());
        putIfPresent(d, "rejectedCount", f.rejectedCount());
        putIfPresent(d, "duplicateCount", f.duplicateCount());
        putIfPresent(d, "errorMessage", f.errorMessage());
        d.put("renameFailed", f.renameFailed());
        d.put("redeliveryCount", f.redeliveryCount());
        return d;
    }

    /**
     * The bytes of a {@code /files/<fileId>} node, read from {@code files.current_path} (the
     * post-rename location). 404 {@code gone} when inbox hygiene removed the file; its events remain.
     */
    public BinaryContent downloadBinary(String id) throws SQLException {
        if (InboxFiles.owns(id)) {
            return inbox.downloadBinary(id);   // straight off the volume, ingested or not
        }
        String fileId = parseFileId(id);
        if (fileId == null) {
            object(id);
            throw ProducerException.unsupported("Object is not a binary: " + id);
        }
        FileRow f = requireFile(fileId, id);
        if (f.currentPath() == null) {
            throw ProducerException.fileGone(f.fileId());
        }
        Path current = Path.of(f.currentPath());
        // Stat'ed without following links, like the open: a symlink left at the consumed path is
        // not the file the receiver hashed, so it is "gone" rather than followed.
        BasicFileAttributes attrs;
        try {
            attrs = Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            throw ProducerException.fileGone(f.fileId());
        }
        if (!attrs.isRegularFile()) {
            throw ProducerException.fileGone(f.fileId());
        }
        return new BinaryContent(id, current, attrs.size(), BinaryContent.mimeOf(f.fileName()), f.fileName());
    }

    // --- write surface: the live /inbox branch only ----------------------------

    /** {@code uploadBinaryContent} into a live {@code /inbox} container; {@code /files} is a projection, not writable. */
    public Map<String, Object> uploadBinary(String id, String fileName, byte[] bytes) throws SQLException {
        if (InboxFiles.owns(id)) {
            return inbox.upload(id, fileName, bytes);
        }
        object(id);   // 404 an unknown id before reporting it unsupported
        throw ProducerException.unsupported("Upload targets a directory under " + InboxFiles.INBOX + ", not " + id);
    }

    /** {@code createChildObject}: mkdir under a live {@code /inbox} container. */
    public Map<String, Object> createChildContainer(String id, String name) throws SQLException {
        if (InboxFiles.owns(id)) {
            return inbox.mkdir(id, name);
        }
        object(id);
        throw ProducerException.unsupported("Directories can only be created under " + InboxFiles.INBOX + ", not " + id);
    }

    /** {@code deleteObject}: unlink a live file or remove an empty live directory. */
    public void deleteObject(String id) throws SQLException {
        if (InboxFiles.owns(id)) {
            inbox.delete(id);
            return;
        }
        object(id);
        throw ProducerException.unsupported("Only objects under " + InboxFiles.INBOX + " are deletable: " + id);
    }

    // --- object builders -------------------------------------------------------

    private static Map<String, Object> container(String id, String name) {
        return base(id, name, List.of("container"));
    }

    private static Map<String, Object> fileNode(FileRow f) {
        Map<String, Object> o = base(fileObjectId(f.fileId()), f.fileName(), FILE_CLASSES);
        o.put("documentSchema", FILE_SCHEMA);
        o.put("fileName", f.fileName());
        o.put("size", f.sizeBytes());
        o.put("mimeType", BinaryContent.mimeOf(f.fileName()));
        o.put("checksum", f.checksum());
        putIfPresent(o, "modified", f.fileMtime());
        putIfPresent(o, "created", f.discoveredAt());
        o.put("tags", List.of("source:" + f.sourceName(), "status:" + f.status().wire()));
        return o;
    }

    private static Map<String, Object> base(String id, String name, List<String> objectClass) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", id);
        o.put("name", name);
        o.put("objectClass", objectClass);
        return o;
    }

    private static void putIfPresent(Map<String, Object> m, String key, Object value) {
        if (value != null) {
            m.put(key, value instanceof Instant ? value.toString() : value);
        }
    }
}

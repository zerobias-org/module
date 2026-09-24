package com.zerobias.module.x12.producer;

import com.zerobias.module.x12.SourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The <b>live</b> filesystem view of the mounted inbox volume, and the DataProducer write
 * surface over it (DESIGN §2.9):
 *
 * <pre>
 * /x12-receiver/inbox                      container — one child per configured source
 * └─ /inbox/&lt;source&gt;                       container — that source's directory
 *    ├─ /inbox/&lt;source&gt;/&lt;dir&gt;              container — a real subdirectory
 *    └─ /inbox/&lt;source&gt;/&lt;file&gt;             ["binary"] — a real file
 * </pre>
 *
 * <p><b>Nothing here is cached and nothing comes from the buffer.</b> Every
 * {@code children} call is a fresh readdir and every {@code object} call a fresh
 * {@code stat}, so a file uploaded a millisecond ago is browsable before the poller has
 * looked at it, and a file deleted out from under us disappears on the next call. This is
 * the complement of {@code /files}, which is a projection of the SQLite {@code files}
 * table — i.e. what has been <em>consumed</em>. Use this branch to see what is physically
 * on the volume; use {@code /files} to see what the receiver has ingested.
 *
 * <p>Writes ({@link #upload}, {@link #mkdir}, {@link #delete}) are gated one level up, in
 * {@link X12ProducerFacade}, by {@code config.allowFileManagement}. Dotfiles are hidden
 * from listings exactly as the poller skips them, which also hides the {@code .part-*}
 * temporaries {@link #upload} writes.
 *
 * <p>Ingest is deliberately untouched: the poller still scans each configured source
 * directory <b>flat</b> ({@code InboxPoller} uses a single {@code newDirectoryStream}).
 * A file uploaded into a source root whose name matches the source pattern gets consumed
 * on the next scan; one uploaded into a subdirectory, or under a non-matching name, stays
 * put and is visible here forever. Every file node carries an {@code ingest} field saying
 * which case it is, so a caller never has to guess.
 */
final class InboxFiles {

    private static final Logger LOG = LoggerFactory.getLogger(InboxFiles.class);

    static final String INBOX = ObjectTree.RECEIVER + "/inbox";
    private static final String PREFIX = INBOX + "/";

    /** What the poller will do with a file at this path, derived from config (never cached). */
    private static final String INGEST_WATCHED = "watched";
    private static final String INGEST_PATTERN = "ignored:pattern";
    private static final String INGEST_SUBDIR = "ignored:subdirectory";
    private static final String INGEST_SUFFIX = "ignored:suffix";

    private final List<SourceConfig> sources;
    private final String consumedSuffix;
    private final String errorSuffix;

    InboxFiles(List<SourceConfig> sources, String consumedSuffix, String errorSuffix) {
        this.sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        this.consumedSuffix = Objects.requireNonNull(consumedSuffix, "consumedSuffix");
        this.errorSuffix = Objects.requireNonNull(errorSuffix, "errorSuffix");
    }

    /** Whether {@code id} addresses the inbox branch (its root or anything under it). */
    static boolean owns(String id) {
        return id != null && (INBOX.equals(id) || id.startsWith(PREFIX));
    }

    // --- read surface -------------------------------------------------------

    Map<String, Object> object(String id) {
        if (INBOX.equals(id)) {
            return node(INBOX, "inbox", List.of("container"));
        }
        Node n = resolve(id);
        if (Files.isDirectory(n.path())) {
            return dirNode(id, n);
        }
        if (Files.isRegularFile(n.path())) {
            return fileNode(id, n);
        }
        throw ProducerException.noSuchObject(id);
    }

    List<Map<String, Object>> children(String id) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (INBOX.equals(id)) {
            for (SourceConfig s : sources) {
                out.add(object(INBOX + "/" + ObjectTree.encodeSegment(s.name())));
            }
            return out;
        }
        Node n = resolve(id);
        if (Files.isRegularFile(n.path())) {
            throw ProducerException.unsupported("Object is not a container: " + id);
        }
        if (!Files.isDirectory(n.path())) {
            throw ProducerException.noSuchObject(id);
        }
        List<Path> entries = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(n.path())) {
            for (Path p : ds) {
                if (!p.getFileName().toString().startsWith(".")) {
                    entries.add(p);
                }
            }
        } catch (IOException e) {
            throw ioFailure("list", id, e);
        }
        // Directories first, then files, each alphabetical — a stable order for a live readdir.
        entries.sort(Comparator.comparing((Path p) -> Files.isDirectory(p) ? 0 : 1)
            .thenComparing(p -> p.getFileName().toString()));
        for (Path p : entries) {
            out.add(object(id + "/" + ObjectTree.encodeSegment(p.getFileName().toString())));
        }
        return out;
    }

    /**
     * Where a live file's bytes are, for the HTTP layer to stream (DESIGN §2.8/§2.9) — never
     * the bytes themselves. The final component is stat'ed without following links and
     * {@link BinaryContent#open} opens it the same way, so a symlink planted (or swapped in)
     * at the name is never followed out of the source directory.
     */
    BinaryContent downloadBinary(String id) {
        Node n = resolve(id);
        BasicFileAttributes attrs;
        try {
            attrs = Files.readAttributes(n.path(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            throw ProducerException.noSuchObject(id);
        }
        if (attrs.isDirectory()) {
            throw ProducerException.unsupported("Object is not a binary: " + id);
        }
        if (!attrs.isRegularFile()) {
            throw ProducerException.noSuchObject(id);
        }
        return new BinaryContent(id, n.path(), attrs.size(), BinaryContent.MIME_X12,
            n.path().getFileName().toString());
    }

    // --- write surface (gated by config.allowFileManagement) ----------------

    /**
     * Write {@code bytes} as {@code fileName} inside the container {@code parentId}.
     *
     * <p>The bytes land on a dot-prefixed temporary in the same directory and are then
     * {@code ATOMIC_MOVE}d into place, so the poller — which skips dotfiles — can never
     * observe a partial file regardless of {@code stableForSec}. An existing name is
     * refused rather than replaced: {@code fileId} is {@code <path>@<hash12>}, so
     * overwriting bytes at a consumed path would produce a second identity for one path
     * and desynchronise the {@code .done} bookkeeping (CLAUDE.md, "nothing is skipped by
     * path"). Delete first if you mean to replace.
     */
    Map<String, Object> upload(String parentId, String fileName, byte[] bytes) {
        Node parent = requireDirectory(parentId);
        String name = requireName(fileName, "fileName");
        Path target = parent.path().resolve(name);
        if (Files.exists(target)) {
            throw ProducerException.illegalArgument(
                "Already exists: " + name + " (upload never replaces; delete it first)");
        }
        Path tmp = parent.path().resolve("." + name + ".part-" + UUID.randomUUID());
        try {
            Files.write(tmp, bytes == null ? new byte[0] : bytes);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignore) {
                // best effort; a leftover dotfile is invisible to the poller and to browse
            }
            throw ioFailure("write " + name + " into", parentId, e);
        }
        String id = parentId + "/" + ObjectTree.encodeSegment(name);
        return fileNode(id, new Node(parent.source(), target, false));
    }

    /** Create a subdirectory {@code name} under the container {@code parentId}. */
    Map<String, Object> mkdir(String parentId, String name) {
        Node parent = requireDirectory(parentId);
        String dir = requireName(name, "name");
        Path target = parent.path().resolve(dir);
        try {
            Files.createDirectory(target);
        } catch (FileAlreadyExistsException e) {
            throw ProducerException.illegalArgument("Already exists: " + dir);
        } catch (IOException e) {
            throw ioFailure("create " + dir + " under", parentId, e);
        }
        String id = parentId + "/" + ObjectTree.encodeSegment(dir);
        return dirNode(id, new Node(parent.source(), target, false));
    }

    /**
     * Delete the file or (empty) directory at {@code id}. The branch root and the
     * configured source roots are not deletable — they are mount points the daemon
     * validated at boot, and removing one would take the receiver down. A non-empty
     * directory is refused rather than deleted recursively: a blind recursive delete over
     * a live feed directory is how you lose un-ingested claims.
     */
    void delete(String id) {
        if (INBOX.equals(id)) {
            throw ProducerException.unsupported("The inbox root is not deletable: " + id);
        }
        Node n = resolve(id);
        if (n.sourceRoot()) {
            throw ProducerException.unsupported(
                "A configured source directory is not deletable: " + id);
        }
        if (!Files.exists(n.path())) {
            throw ProducerException.noSuchObject(id);
        }
        try {
            Files.delete(n.path());
        } catch (DirectoryNotEmptyException e) {
            throw ProducerException.illegalArgument(
                "Directory is not empty: " + id + " (delete its children first)");
        } catch (IOException e) {
            throw ioFailure("delete", id, e);
        }
    }

    /** A filesystem failure the caller cannot act on: logged here, answered as a generic 500. */
    private static ProducerException ioFailure(String action, String id, IOException e) {
        LOG.error("inbox: cannot {} {}", action, id, e);
        return ProducerException.unexpected();
    }

    // --- resolution ---------------------------------------------------------

    /** A resolved inbox id: its source, its absolute path, and whether it is the source root. */
    private record Node(SourceConfig source, Path path, boolean sourceRoot) {
    }

    /**
     * Resolve an id under {@code /inbox} to a real path, or throw {@code noSuchObjectError}.
     * Every segment is decoded, {@code .}/{@code ..}/empty segments are rejected outright,
     * and the normalized result must still sit under the source root — an id can never
     * address anything outside its own mounted directory.
     */
    private Node resolve(String id) {
        if (!owns(id) || INBOX.equals(id)) {
            throw ProducerException.noSuchObject(id);
        }
        String rem = id.substring(PREFIX.length());
        if (rem.isBlank()) {
            throw ProducerException.noSuchObject(id);
        }
        String[] segments = rem.split("/");
        SourceConfig source = sourceByName(ObjectTree.decodeSegment(segments[0]));
        if (source == null) {
            throw ProducerException.noSuchObject(id);
        }
        Path root = source.dir().toAbsolutePath().normalize();
        Path path = root;
        for (int i = 1; i < segments.length; i++) {
            String segment = ObjectTree.decodeSegment(segments[i]);
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw ProducerException.noSuchObject(id);
            }
            path = path.resolve(segment);
        }
        path = path.normalize();
        if (!path.equals(root) && !path.startsWith(root)) {
            throw ProducerException.noSuchObject(id);
        }
        return new Node(source, path, segments.length == 1);
    }

    private Node requireDirectory(String parentId) {
        if (INBOX.equals(parentId)) {
            throw ProducerException.unsupported(
                "Pick a source directory (a child of " + INBOX + "); the branch root is not a real directory");
        }
        Node n = resolve(parentId);
        if (!Files.isDirectory(n.path())) {
            throw Files.exists(n.path())
                ? ProducerException.unsupported("Object is not a container: " + parentId)
                : ProducerException.noSuchObject(parentId);
        }
        return n;
    }

    private SourceConfig sourceByName(String name) {
        for (SourceConfig s : sources) {
            if (s.name().equals(name)) {
                return s;
            }
        }
        return null;
    }

    private static String requireName(String name, String field) {
        if (name == null || name.isBlank()) {
            throw ProducerException.illegalArgument(field + " is required");
        }
        String trimmed = name.trim();
        if (trimmed.contains("/") || trimmed.contains("\\")) {
            throw ProducerException.illegalArgument(
                field + " must be a single path segment (no separators): " + name);
        }
        if (trimmed.startsWith(".")) {
            // Dotfiles are invisible to both the poller and this branch, so creating one
            // would be a write nobody can see or clean up through the API.
            throw ProducerException.illegalArgument(field + " must not start with '.': " + name);
        }
        return trimmed;
    }

    // --- node builders ------------------------------------------------------

    private Map<String, Object> dirNode(String id, Node n) {
        Map<String, Object> o = node(id, n.path().getFileName().toString(), List.of("container"));
        if (n.sourceRoot()) {
            o.put("name", n.source().name());
            o.put("pattern", n.source().pattern());
            o.put("pollIntervalSec", n.source().pollIntervalSec());
            o.put("stableForSec", n.source().stableForSec());
        }
        o.put("path", n.path().toString());
        o.put("sourceName", n.source().name());
        o.put("writable", Files.isWritable(n.path()));
        return o;
    }

    private Map<String, Object> fileNode(String id, Node n) {
        Map<String, Object> o = node(id, n.path().getFileName().toString(), List.of("binary"));
        String fileName = n.path().getFileName().toString();
        o.put("fileName", fileName);
        o.put("path", n.path().toString());
        o.put("sourceName", n.source().name());
        o.put("mimeType", BinaryContent.MIME_X12);
        try {
            o.put("size", Files.size(n.path()));
            o.put("modified", Instant.ofEpochMilli(Files.getLastModifiedTime(n.path()).toMillis()).toString());
        } catch (IOException e) {
            // A live view races with the feed and with inbox hygiene; report what we have.
            o.put("size", null);
            o.put("modified", null);
        }
        o.put("ingest", ingestState(n, fileName));
        // No checksum: hashing every entry on every readdir would make browse O(bytes).
        // The consumed view (/files/<fileId>) carries the sha256 the receiver recorded.
        return o;
    }

    /** What the poller will do with this file — config-derived, computed per call. */
    private String ingestState(Node n, String fileName) {
        if (!n.path().getParent().equals(n.source().dir().toAbsolutePath().normalize())) {
            return INGEST_SUBDIR;
        }
        if (endsWith(fileName, consumedSuffix) || endsWith(fileName, errorSuffix)) {
            return INGEST_SUFFIX;
        }
        if (!n.source().matchesFileName(fileName)) {
            return INGEST_PATTERN;
        }
        return INGEST_WATCHED;
    }

    private static boolean endsWith(String name, String suffix) {
        return suffix != null && !suffix.isBlank() && name.endsWith(suffix);
    }

    private static Map<String, Object> node(String id, String name, List<String> classes) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", id);
        o.put("name", name);
        o.put("objectClass", classes);
        return o;
    }
}

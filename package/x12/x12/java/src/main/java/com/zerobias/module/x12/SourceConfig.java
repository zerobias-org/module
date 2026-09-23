package com.zerobias.module.x12;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * One watched inbox directory ({@code config.sources[]}, DESIGN §4.1): {@code name} is
 * the provenance label ({@code /by-source/<name>}, {@code sourceName} column);
 * {@code path} is the directory INSIDE the container; {@code pattern} is a glob against
 * the file name, matched case-insensitively; {@code pollIntervalSec} is the scan cadence;
 * {@code stableForSec} is how long size+mtime must be unchanged before a file is read.
 */
public record SourceConfig(String name, String path, String pattern, int pollIntervalSec, int stableForSec) {

    public static final String DEFAULT_PATTERN = "*";
    public static final int DEFAULT_POLL_INTERVAL_SEC = 30;
    public static final int DEFAULT_STABLE_FOR_SEC = 60;

    public SourceConfig {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("source name is required");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("source '" + name + "': path is required");
        }
        if (pattern == null || pattern.isBlank()) {
            pattern = DEFAULT_PATTERN;
        }
        if (pollIntervalSec <= 0) {
            pollIntervalSec = DEFAULT_POLL_INTERVAL_SEC;
        }
        if (stableForSec < 0) {
            stableForSec = DEFAULT_STABLE_FOR_SEC;
        }
    }

    public Path dir() {
        return Path.of(path);
    }

    /** Case-insensitive glob match of a bare file name (no directory) against {@code pattern}. */
    public boolean matchesFileName(String fileName) {
        PathMatcher m = FileSystems.getDefault().getPathMatcher("glob:" + pattern.toLowerCase());
        return m.matches(Path.of(fileName.toLowerCase()));
    }

    /**
     * Boot validation (DESIGN §3): the directory must exist, be a directory, and be
     * writable — proven by a real rename (create a dot-prefixed temp file, rename it,
     * delete it; dotfiles are skipped by the poller). Returns a problem description or
     * null when the source is usable.
     */
    public String validate() {
        Path dir = dir();
        if (!Files.exists(dir)) {
            return "source '" + name + "': path does not exist: " + path;
        }
        if (!Files.isDirectory(dir)) {
            return "source '" + name + "': path is not a directory: " + path;
        }
        Path probe = dir.resolve(".zb-probe-" + UUID.randomUUID() + ".tmp");
        Path renamed = Path.of(probe + ".done");
        try {
            Files.writeString(probe, "probe");
            Files.move(probe, renamed, StandardCopyOption.ATOMIC_MOVE);
            Files.deleteIfExists(renamed);
            return null;
        } catch (Exception e) {
            try {
                Files.deleteIfExists(probe);
                Files.deleteIfExists(renamed);
            } catch (Exception ignore) {
                // best effort
            }
            return "source '" + name + "': path is not writable/renameable: " + path + " (" + e + ")";
        }
    }
}

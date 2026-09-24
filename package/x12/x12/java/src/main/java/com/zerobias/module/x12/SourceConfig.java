package com.zerobias.module.x12;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One watched inbox directory ({@code config.sources[]}, DESIGN §4.1): {@code name} is
 * the provenance label ({@code /by-source/<name>}, {@code sourceName} column);
 * {@code path} is the directory INSIDE the container; {@code pattern} is a glob against
 * the file name, matched case-insensitively; {@code pollIntervalSec} is the scan cadence;
 * {@code stableForSec} is how long size+mtime must be unchanged before a file is read.
 * Out-of-range values are rejected, not corrected: a typo must stop the boot rather than
 * silently change how often (or how early) PHI files are picked up.
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
        if (pattern == null) {
            pattern = DEFAULT_PATTERN;
        } else if (pattern.isBlank()) {
            throw new IllegalArgumentException("source '" + name + "': pattern must not be blank");
        }
        if (pollIntervalSec <= 0) {
            throw new IllegalArgumentException("source '" + name + "': pollIntervalSec must be > 0, got " + pollIntervalSec);
        }
        if (stableForSec < 0) {
            throw new IllegalArgumentException("source '" + name + "': stableForSec must be >= 0, got " + stableForSec);
        }
        try {
            FileSystems.getDefault().getPathMatcher("glob:" + pattern.toLowerCase());
        } catch (IllegalArgumentException badGlob) {
            throw new IllegalArgumentException("source '" + name + "': pattern is not a valid glob: " + pattern, badGlob);
        }
    }

    public Path dir() {
        return Path.of(path);
    }

    /** The absolute, normalized directory; what overlap checks and containment compare. */
    public Path normalizedDir() {
        return dir().toAbsolutePath().normalize();
    }

    /** Case-insensitive glob match of a bare file name (no directory) against {@code pattern}. */
    public boolean matchesFileName(String fileName) {
        PathMatcher m = FileSystems.getDefault().getPathMatcher("glob:" + pattern.toLowerCase());
        return m.matches(Path.of(fileName.toLowerCase()));
    }

    /** True when {@code file} is an entry of this source's directory (see {@link #isEntryOf}). */
    public boolean contains(Path file) {
        return isEntryOf(dir(), file);
    }

    /**
     * True when {@code file}, lexically normalized, sits directly in {@code dir}. Pollers
     * never descend, and every rename target is a sibling, so each path the buffer records
     * ({@code file_path}, {@code current_path}) passes this against its source directory; a
     * path that fails it (a {@code ..} climb, another directory) must not be read or served.
     * Lexical only: pair it with {@code LinkOption.NOFOLLOW_LINKS} when opening, so a
     * symlink planted at a legitimate name is not followed out of the directory.
     */
    public static boolean isEntryOf(Path dir, Path file) {
        if (dir == null || file == null) {
            return false;
        }
        Path parent = file.toAbsolutePath().normalize().getParent();
        return parent != null && parent.equals(dir.toAbsolutePath().normalize());
    }

    /**
     * Boot validation (DESIGN §3): the directory must exist, be a directory, and take the
     * renames the poller will make — proven for real: a dot-prefixed temp file (dotfiles are
     * skipped by the poller) is created, renamed with {@code consumedSuffix}, then with
     * {@code errorSuffix}, and deleted. A suffix the filesystem refuses (a name past its
     * length limit, a character it does not allow) fails here instead of on every file. A
     * blank suffix is not probed (the config check reports it). Returns a problem
     * description or null when the source is usable.
     */
    public String validate(String consumedSuffix, String errorSuffix) {
        Path dir = dir();
        if (!Files.exists(dir)) {
            return "source '" + name + "': path does not exist: " + path;
        }
        if (!Files.isDirectory(dir)) {
            return "source '" + name + "': path is not a directory: " + path;
        }
        Path probe = dir.resolve(".zb-probe-" + UUID.randomUUID() + ".tmp");
        List<Path> made = new ArrayList<>(List.of(probe));
        String step = "create a file";
        try {
            Files.writeString(probe, "probe");
            Path current = probe;
            for (String suffix : new String[] {consumedSuffix, errorSuffix}) {
                if (suffix == null || suffix.isBlank()) {
                    continue;
                }
                Path next = Path.of(probe + suffix);
                made.add(next);
                step = "rename a file to its '" + suffix + "' name";
                Files.move(current, next, StandardCopyOption.ATOMIC_MOVE);
                current = next;
            }
            Files.deleteIfExists(current);
            return null;
        } catch (Exception e) {
            for (Path p : made) {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignore) {
                    // best effort
                }
            }
            return "source '" + name + "': path is not writable/renameable: " + path
                + " (cannot " + step + ": " + e + ")";
        }
    }
}

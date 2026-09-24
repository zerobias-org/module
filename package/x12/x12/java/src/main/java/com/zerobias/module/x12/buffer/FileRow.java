package com.zerobias.module.x12.buffer;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One row of the {@code files} table (DESIGN §8): one interchange file discovered in
 * an inbox. {@code fileId} is {@code <absolute path at discovery>@<first 12 hex of the
 * sha256>} — the same bytes re-landing at the same path share an id (a redelivery,
 * counted in {@code redeliveryCount}); new bytes at a reused name get a new id. It
 * doubles as the {@code /files/<fileId>} object id. {@code filePath} is the raw
 * absolute path at discovery (before the {@code .done}/{@code .error} rename) and
 * {@code currentPath} is where the bytes live now (resolved for {@code downloadBinary},
 * DESIGN §2.8). {@code checksum} (full sha256 hex) is the duplicate-detection key
 * (DESIGN §4.2 step 3a). Files rows are never evicted by retention.
 */
public record FileRow(
    long id,
    String fileId,
    String filePath,
    String fileName,
    String sourceName,
    String currentPath,
    long sizeBytes,
    String checksum,
    Instant fileMtime,
    Instant discoveredAt,
    Instant consumedAt,
    FileStatus status,
    Integer isaCount,
    Integer transactionCount,
    String errorMessage,
    boolean renameFailed,
    int redeliveryCount) {

    /** Hex digits of the sha256 that go into the id. */
    public static final int ID_HASH_CHARS = 12;

    /** Id suffix of the error row for a file that could never be read, so has no hash. */
    public static final String UNREADABLE = "unreadable";

    private static final Pattern ID_SUFFIX = Pattern.compile("@([0-9a-f]{" + ID_HASH_CHARS + "}|" + UNREADABLE + ")$");

    /** {@code <absolutePath>@<first 12 hex of checksum>} (DESIGN §2.1). */
    public static String fileId(String absolutePath, String checksum) {
        String sum = checksum == null ? "" : checksum.toLowerCase();
        return absolutePath + "@" + sum.substring(0, Math.min(ID_HASH_CHARS, sum.length()));
    }

    /**
     * {@code <absolutePath>@unreadable}: the id of the error row recorded for a file that
     * kept failing to read. Its bytes were never seen, so there is no hash; one row per
     * path, removed once the file is read.
     */
    public static String unreadableId(String absolutePath) {
        return absolutePath + "@" + UNREADABLE;
    }

    /** The discovery path embedded in a {@code fileId}; an id without the hash suffix is returned as-is. */
    public static String pathOf(String fileId) {
        if (fileId == null) {
            return null;
        }
        Matcher m = ID_SUFFIX.matcher(fileId);
        return m.find() ? fileId.substring(0, m.start()) : fileId;
    }

    /** The file name (last path segment) embedded in a {@code fileId}. */
    public static String fileNameOf(String fileId) {
        String path = pathOf(fileId);
        if (path == null) {
            return null;
        }
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }
}

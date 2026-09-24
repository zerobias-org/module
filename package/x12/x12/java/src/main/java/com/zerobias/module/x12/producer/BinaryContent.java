package com.zerobias.module.x12.producer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * What {@code downloadBinary} serves for a {@code /files/<fileId>} node (DESIGN §2.8): where
 * the raw EDI lives and how big it is, never the bytes themselves — an 835 batch can run to
 * the {@code maxFileBytes} ceiling, and the HTTP layer streams it with a {@code Content-Length}
 * instead of holding it on the heap. v1 always serves full content.
 *
 * @param fileId   the file's id, for the 404 when the bytes vanish between resolve and open
 * @param path     the file's current location, already checked to be a regular file in its
 *                 source directory
 * @param size     bytes on disk at resolve time
 * @param mimeType always {@link #MIME_X12}
 * @param fileName the name for {@code Content-Disposition}
 */
public record BinaryContent(String fileId, Path path, long size, String mimeType, String fileName) {

    public static final String MIME_X12 = "application/EDI-X12";

    /**
     * Open the bytes for streaming. {@code NOFOLLOW_LINKS} (O_NOFOLLOW) closes the window
     * between the resolve-time check and the open: a symlink swapped in at the path fails
     * the open instead of being followed out of the source directory.
     */
    public InputStream open() throws IOException {
        return Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
    }
}

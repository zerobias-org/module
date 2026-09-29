package com.zerobias.module.x12.inbox;

import com.zerobias.module.x12.SourceConfig;
import com.zerobias.module.x12.buffer.FileRow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HexFormat;

/** Driving a {@link FileConsumer} directly, without a poller, and the ids it assigns. */
public final class InboxFixture {

    private InboxFixture() {
    }

    /**
     * Consume {@code path} as if the stability window had just passed it: the sighting is the
     * file's current {@code (size, mtime)}, first seen at {@code discoveredAt}.
     */
    public static FileConsumer.Result consume(FileConsumer consumer, SourceConfig source, Path path,
            Instant discoveredAt) throws SQLException, IOException {
        BasicFileAttributes a = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return consumer.consume(source, path, new FileStability.Sighting(a.size(),
            a.lastModifiedTime().toInstant(), discoveredAt, discoveredAt));
    }

    /** The {@code <path>@<hash12>} identity a file at {@code path} with these bytes gets. */
    public static String fileId(Path path, byte[] bytes) {
        return FileRow.fileId(path.toAbsolutePath().normalize().toString(), sha256(bytes));
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

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

/** Shared fixtures for driving the consumer directly and predicting the identities it assigns. */
public final class InboxFixtures {

    private InboxFixtures() {
    }

    /**
     * Consume {@code path} without a poller: its current {@code (size, mtime)} stands in for the
     * pair a stability window would have seen, first seen at {@code discoveredAt}.
     */
    public static FileConsumer.Result consume(FileConsumer consumer, SourceConfig source, Path path,
                                              Instant discoveredAt) throws IOException, SQLException {
        BasicFileAttributes a = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return consumer.consume(source, path,
            new FileStability.Sighting(a.size(), a.lastModifiedTime().toInstant(), discoveredAt, discoveredAt));
    }

    /** The {@code <path>@<hash12>} identity the consumer gives a file at {@code path} holding {@code bytes}. */
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

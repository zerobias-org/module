package com.zerobias.module.x12.buffer;

import java.sql.SQLException;

/**
 * {@link BufferStore#consumeFile} found a transaction set whose element key is already
 * taken. A file's id is new when it is consumed (the consumer resolves redelivery and
 * duplicates by checksum first), so the only way to collide is two transaction sets inside
 * the same file sharing {@code ISA13/GS06/ST02} — a malformed interchange, not a
 * redelivery. The whole file is rolled back and the consumer sends it to {@code .error}
 * rather than acknowledging it with a transaction set silently missing.
 *
 * <p>An {@link SQLException} so it propagates through the store's SQL signatures, but it
 * says nothing about the store's health: callers must treat it as this file's error.
 */
public final class DuplicateElementKeyException extends SQLException {

    public static final String KIND = "duplicate-element-key";

    public DuplicateElementKeyException(String elementKey) {
        super(KIND + ": two transaction sets in one file share the element key " + elementKey);
    }
}

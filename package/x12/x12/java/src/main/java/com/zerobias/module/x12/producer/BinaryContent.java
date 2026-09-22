package com.zerobias.module.x12.producer;

/**
 * The bytes served by {@code downloadBinary} for a {@code /files/<fileId>} node
 * (DESIGN §2.8): the raw EDI verbatim, {@code mimeType} {@code application/EDI-X12},
 * {@code fileName} for the {@code Content-Disposition}. v1 always serves full content.
 */
public record BinaryContent(byte[] bytes, String mimeType, String fileName) {

    public static final String MIME_X12 = "application/EDI-X12";
}

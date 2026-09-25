package com.zerobias.module.x12.producer;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A DataProducer error, serialized to the platform error envelope defined by the
 * interface OpenAPI schema ({@code errorModelBase} and its subtypes in
 * {@code module-interface-dataproducer.yml}) — NOT the looser {@code {code,message,details}}
 * shape shown in {@code Errors.md} prose (DESIGN §2.7; the machine schema wins).
 *
 * <p>Every body carries the {@code errorModelBase} required fields
 * {@code {key, template, timestamp, statusCode}}, plus the subtype's own required fields:
 * <ul>
 *   <li>{@code noSuchObjectError} (404) — adds {@code {type, id}}: unknown object /
 *       schema / lease / file.</li>
 *   <li>{@code illegalArgumentError} (400) — adds {@code {msg}}; backs <em>both</em> the
 *       {@code UnsupportedOperationError} response (every write op) and the
 *       {@code illegalArgumentError} response (malformed filter, page size out of range).</li>
 *   <li>{@code unexpectedError} (500) — adds {@code {msg}}; always the same generic text
 *       ({@link #unexpected}), because the cause can name paths inside the container.</li>
 * </ul>
 */
public final class ProducerException extends RuntimeException {

    private static final String UNEXPECTED_MESSAGE = "Unexpected error";

    private final String key;
    private final int httpStatus;
    private final Map<String, Object> extras;

    private ProducerException(String key, int httpStatus, String message, Map<String, Object> extras) {
        super(message == null ? key : message);
        this.key = key;
        this.httpStatus = httpStatus;
        this.extras = extras;
    }

    /** The i18n/discrimination key (errorModelBase {@code key}). */
    public String key() {
        return key;
    }

    public int httpStatus() {
        return httpStatus;
    }

    /** The platform error body: errorModelBase fields + the subtype's required fields. */
    public Map<String, Object> toBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("key", key);
        body.put("template", getMessage());
        body.put("timestamp", Instant.now().toString());
        body.put("statusCode", httpStatus);
        body.putAll(extras);
        return body;
    }

    /** {@code noSuchObjectError} body — errorModelBase + {@code {type, id}}. */
    public static ProducerException noSuchObject(String objectId) {
        return new ProducerException("err.no.such.object", 404,
            "Object not found: " + objectId, typeId("object", objectId));
    }

    public static ProducerException noSuchSchema(String schemaId) {
        return new ProducerException("err.no.such.object", 404,
            "Schema not found: " + schemaId, typeId("schema", schemaId));
    }

    public static ProducerException noSuchLease(String leaseId) {
        return new ProducerException("err.no.such.object", 404,
            "Lease not found: " + leaseId, typeId("lease", leaseId));
    }

    /**
     * {@code noSuchObjectError} for a file whose bytes are gone (removed by inbox
     * hygiene): DESIGN §2.8 — 404 with {@code reason: gone}; the transactions remain.
     */
    public static ProducerException fileGone(String fileId) {
        Map<String, Object> m = typeId("file", fileId);
        m.put("reason", "gone");
        return new ProducerException("err.no.such.object", 404,
            "File bytes no longer available: " + fileId, m);
    }

    /** {@code illegalArgumentError} body via the {@code UnsupportedOperationError} response. */
    public static ProducerException unsupported(String message) {
        return new ProducerException("err.unsupported.operation", 400, message, msg(message));
    }

    /** {@code illegalArgumentError} body via the {@code illegalArgumentError} response. */
    public static ProducerException illegalArgument(String message) {
        return new ProducerException("err.illegal.argument", 400, message, msg(message));
    }

    /**
     * 413 for a request body over the server's limit, in the {@code illegalArgumentError}
     * shape (errorModelBase + {@code msg}) so a caller gets the platform envelope rather than
     * the HTTP server's own error page.
     */
    public static ProducerException payloadTooLarge(long maxBytes) {
        String message = "Request body exceeds the " + maxBytes + "-byte limit";
        return new ProducerException("err.illegal.argument", 413, message, msg(message));
    }

    /**
     * {@code unexpectedError} (500) for a failure the caller cannot act on. Always the same
     * generic text: the cause (an SQLite or IO message) can name buffer or inbox paths inside
     * the container, so it is logged server-side and never echoed.
     */
    public static ProducerException unexpected() {
        return new ProducerException("err.unexpected", 500, UNEXPECTED_MESSAGE, msg(UNEXPECTED_MESSAGE));
    }

    private static Map<String, Object> typeId(String type, String id) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("id", id == null ? "" : id);
        return m;
    }

    private static Map<String, Object> msg(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("msg", message == null ? "" : message);
        return m;
    }
}

package com.zerobias.module.hl7.producer;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A DataProducer error, serialized to the platform error envelope defined by the
 * interface OpenAPI schema (`errorModelBase` and its subtypes in
 * `module-interface-dataproducer.yml`) — NOT the looser `{code,message,details}`
 * shape shown in `Errors.md` prose. Where those two interface docs disagree the
 * machine schema wins, and the reference SQL module emits this same envelope.
 *
 * <p>Every body carries the `errorModelBase` required fields
 * {@code {key, template, timestamp, statusCode}}, plus the subtype's own
 * required fields:
 * <ul>
 *   <li>{@code noSuchObjectError} (404, response {@code NoSuchObjectError}) —
 *       adds {@code {type, id}} (DESIGN §2.7: unknown object/schema).</li>
 *   <li>{@code illegalArgumentError} (400) — adds {@code {msg}}; this body backs
 *       <em>both</em> the {@code UnsupportedOperationError} response (op invalid
 *       for the object's class) and the {@code illegalArgumentError} response
 *       (malformed filter, page size out of range).</li>
 * </ul>
 *
 * <p>{@code key} is the i18n/discrimination key (dotted, mirroring the SQL
 * module's {@code err.no.such.object} / {@code err.illegal.argument}), not the
 * HTTP-status name.
 */
public final class ProducerException extends RuntimeException {

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

    /** {@code illegalArgumentError} body via the {@code UnsupportedOperationError} response. */
    public static ProducerException unsupported(String message) {
        return new ProducerException("err.unsupported.operation", 400, message, msg(message));
    }

    /** {@code illegalArgumentError} body via the {@code illegalArgumentError} response. */
    public static ProducerException illegalArgument(String message) {
        return new ProducerException("err.illegal.argument", 400, message, msg(message));
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

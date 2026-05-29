package com.zerobias.module.hl7.producer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A DataProducer error mapped to the platform envelope from
 * [`Errors.md`](../../interface/dataproducer/documentation/Errors.md):
 * {@code {code, message, details}} with an associated HTTP status.
 *
 * <p>Factories cover the standard cases this read-only producer raises
 * (DESIGN §2.7): unknown object/schema → 404 {@code noSuchObjectError};
 * an op the object's class doesn't support → 400 {@code UnsupportedOperationError};
 * bad input (malformed filter, page size out of range) → 400
 * {@code illegalArgumentError}.
 */
public final class ProducerException extends RuntimeException {

    private final String code;
    private final int httpStatus;
    private final Map<String, Object> details;

    private ProducerException(String code, int httpStatus, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.details = details;
    }

    public String code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public Map<String, Object> details() {
        return details;
    }

    /** The platform error body: {@code {code, message, details}}. */
    public Map<String, Object> toBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", getMessage());
        if (details != null && !details.isEmpty()) {
            body.put("details", details);
        }
        return body;
    }

    public static ProducerException noSuchObject(String objectId) {
        return new ProducerException("noSuchObjectError", 404,
            "Object not found: " + objectId, Map.of("objectId", objectId));
    }

    public static ProducerException noSuchSchema(String schemaId) {
        return new ProducerException("noSuchObjectError", 404,
            "Schema not found: " + schemaId, Map.of("schemaId", schemaId));
    }

    public static ProducerException unsupported(String message) {
        return new ProducerException("UnsupportedOperationError", 400, message, Map.of());
    }

    public static ProducerException illegalArgument(String message) {
        return new ProducerException("illegalArgumentError", 400, message, Map.of());
    }
}

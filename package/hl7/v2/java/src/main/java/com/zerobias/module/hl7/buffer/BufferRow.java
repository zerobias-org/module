package com.zerobias.module.hl7.buffer;

import java.time.Instant;

/**
 * One row of the durable message buffer (DESIGN §8 {@code messages} table).
 *
 * <p>{@code rawEr7} is the canonical wire bytes (audit/replay); {@code mappedJson}
 * is the post-normalized typed JSON (DESIGN §5) the DataProducer surface serves.
 * {@code controlId} (MSH-10) is the natural key — duplicate inserts are dropped
 * (HL7 re-sends are routine, DESIGN §4.2). {@code leaseId}/{@code inFlightUntil}
 * are set while {@code status == IN_FLIGHT}; {@code ackedAt} when {@code ACKED}.
 *
 * <p>{@code sourcePort} is the name of the MLLP listener the message arrived on
 * (provenance for the many-named-ports model, DESIGN §11.4); null for rows received
 * before per-port provenance existed (the durable buffer's pre-migration rows).
 */
public record BufferRow(
    long id,
    Instant receivedAt,
    String controlId,
    String messageStructure,
    String messageCode,
    String triggerEvent,
    String sendingApp,
    String sendingFacility,
    String hl7Version,
    String schemaId,
    byte[] rawEr7,
    String mappedJson,
    MessageStatus status,
    String leaseId,
    Instant inFlightUntil,
    Instant ackedAt,
    String sourcePort) {

    /**
     * Back-compat constructor for callers (and pre-provenance tests) that don't supply
     * a source port — equivalent to {@code sourcePort == null}.
     */
    public BufferRow(
            long id,
            Instant receivedAt,
            String controlId,
            String messageStructure,
            String messageCode,
            String triggerEvent,
            String sendingApp,
            String sendingFacility,
            String hl7Version,
            String schemaId,
            byte[] rawEr7,
            String mappedJson,
            MessageStatus status,
            String leaseId,
            Instant inFlightUntil,
            Instant ackedAt) {
        this(id, receivedAt, controlId, messageStructure, messageCode, triggerEvent,
            sendingApp, sendingFacility, hl7Version, schemaId, rawEr7, mappedJson,
            status, leaseId, inFlightUntil, ackedAt, null);
    }

    /**
     * A copy with a re-derived mapping ({@code schemaId} + {@code mappedJson}), every
     * other field (raw ER7, envelope, lease) unchanged. Lets {@code ops/validate}
     * element-ize a re-materialized rep through the same mapper as the stored rep.
     */
    public BufferRow withMapping(String newSchemaId, String newMappedJson) {
        return new BufferRow(id, receivedAt, controlId, messageStructure, messageCode, triggerEvent,
            sendingApp, sendingFacility, hl7Version, newSchemaId, rawEr7, newMappedJson,
            status, leaseId, inFlightUntil, ackedAt, sourcePort);
    }
}

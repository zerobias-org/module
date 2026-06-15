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
    Instant ackedAt) {
}

package com.zerobias.module.x12.buffer;

import java.time.Instant;

/**
 * One row of the {@code transactions} table (DESIGN §8): one transaction set (ST..SE),
 * the collection element / drain atom.
 *
 * <p>{@code rawX12} is the ST..SE segments verbatim plus the ISA/GS context lines
 * (audit / {@code ops/raw} / re-materialization); {@code mappedJson} is the typed JSON
 * (DESIGN §5). {@code elementKey} ({@code <fileId>:<ISA13>:<GS06>:<ST02>}) is the natural key;
 * a collision rolls back the whole file ({@link DuplicateElementKeyException}).
 * {@code leaseId}/{@code inFlightUntil} are set while
 * {@code status == IN_FLIGHT}; {@code ackedAt} when {@code ACKED}. {@code envelope} is
 * {@code file} or {@code synthetic} (DESIGN §4.3).
 */
public record TransactionRow(
    long id,
    String elementKey,
    String fileId,
    String sourceName,
    Instant receivedAt,
    String isaControl,
    String gsControl,
    String stControl,
    String gs08,
    String transactionType,
    String senderId,
    String receiverId,
    Instant interchangeAt,
    String schemaId,
    byte[] rawX12,
    String mappedJson,
    int parserErrorCount,
    String envelope,
    Status status,
    String leaseId,
    Instant inFlightUntil,
    Instant ackedAt) {

    public static final String ENVELOPE_FILE = "file";
    public static final String ENVELOPE_SYNTHETIC = "synthetic";

    /**
     * A copy with a re-derived mapping ({@code schemaId} + {@code mappedJson}), every
     * other field unchanged ({@code ops/recast} / {@code ops/validate}).
     */
    public TransactionRow withMapping(String newSchemaId, String newMappedJson) {
        return new TransactionRow(id, elementKey, fileId, sourceName, receivedAt, isaControl, gsControl,
            stControl, gs08, transactionType, senderId, receiverId, interchangeAt, newSchemaId, rawX12,
            newMappedJson, parserErrorCount, envelope, status, leaseId, inFlightUntil, ackedAt);
    }

    /** Builder for the consume path (the poller fills the envelope field by field). */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String elementKey;
        private String fileId;
        private String sourceName;
        private Instant receivedAt;
        private String isaControl;
        private String gsControl;
        private String stControl;
        private String gs08;
        private String transactionType;
        private String senderId;
        private String receiverId;
        private Instant interchangeAt;
        private String schemaId;
        private byte[] rawX12;
        private String mappedJson;
        private int parserErrorCount;
        private String envelope = ENVELOPE_FILE;

        private Builder() {
        }

        public Builder elementKey(String v) { this.elementKey = v; return this; }
        public Builder fileId(String v) { this.fileId = v; return this; }
        public Builder sourceName(String v) { this.sourceName = v; return this; }
        public Builder receivedAt(Instant v) { this.receivedAt = v; return this; }
        public Builder isaControl(String v) { this.isaControl = v; return this; }
        public Builder gsControl(String v) { this.gsControl = v; return this; }
        public Builder stControl(String v) { this.stControl = v; return this; }
        public Builder gs08(String v) { this.gs08 = v; return this; }
        public Builder transactionType(String v) { this.transactionType = v; return this; }
        public Builder senderId(String v) { this.senderId = v; return this; }
        public Builder receiverId(String v) { this.receiverId = v; return this; }
        public Builder interchangeAt(Instant v) { this.interchangeAt = v; return this; }
        public Builder schemaId(String v) { this.schemaId = v; return this; }
        public Builder rawX12(byte[] v) { this.rawX12 = v; return this; }
        public Builder mappedJson(String v) { this.mappedJson = v; return this; }
        public Builder parserErrorCount(int v) { this.parserErrorCount = v; return this; }
        public Builder envelope(String v) { this.envelope = v; return this; }

        /** A {@code new} (unleased) row with id 0; the store assigns the real id. */
        public TransactionRow build() {
            return new TransactionRow(0, elementKey, fileId, sourceName, receivedAt, isaControl, gsControl,
                stControl, gs08, transactionType, senderId, receiverId, interchangeAt, schemaId, rawX12,
                mappedJson, parserErrorCount, envelope == null ? ENVELOPE_FILE : envelope,
                Status.NEW, null, null, null);
        }
    }
}

package com.zerobias.module.x12.buffer;

import java.time.Instant;

/**
 * One row of the {@code transactions} table (DESIGN §8): one transaction set (ST..SE),
 * the collection element / drain atom.
 *
 * <p>{@code rawX12} is the ST..SE segments verbatim plus the ISA/GS context lines
 * (audit / {@code ops/raw} / re-materialization). The typed document is NOT a column: it
 * lives as the object graph (DESIGN §8.4) and is reassembled on demand, so there is exactly
 * one representation of a transaction's content and nothing to keep in sync. The typed JSON
 * (DESIGN §5). {@code elementKey} ({@code <fileId>:<GS06>:<ST02>}) is the natural key —
 * duplicate inserts are dropped. {@code leaseId}/{@code inFlightUntil} are set while
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
    int parserErrorCount,
    String envelope,
    Status status,
    String leaseId,
    Instant inFlightUntil,
    Instant ackedAt) {

    public static final String ENVELOPE_FILE = "file";
    public static final String ENVELOPE_SYNTHETIC = "synthetic";

    /**
     * A copy bound to a re-derived {@code schemaId} (the graph itself is replaced separately
     * by {@code BufferStore.replaceGraph}), every
     * other field unchanged ({@code ops/recast} / {@code ops/validate}).
     */
    public TransactionRow withSchemaId(String newSchemaId) {
        return new TransactionRow(id, elementKey, fileId, sourceName, receivedAt, isaControl, gsControl,
            stControl, gs08, transactionType, senderId, receiverId, interchangeAt, newSchemaId, rawX12,
            parserErrorCount, envelope, status, leaseId, inFlightUntil, ackedAt);
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
        public Builder parserErrorCount(int v) { this.parserErrorCount = v; return this; }
        public Builder envelope(String v) { this.envelope = v; return this; }

        /**
         * Derive {@code elementKey} as {@code <fileId>:<GS06>:<ST02>} (DESIGN §2.1) from
         * the fields already set. Requires fileId, gsControl and stControl.
         */
        public Builder deriveElementKey() {
            if (fileId == null || gsControl == null || stControl == null) {
                throw new IllegalStateException("deriveElementKey needs fileId, gsControl, stControl");
            }
            this.elementKey = fileId + ":" + gsControl + ":" + stControl;
            return this;
        }

        /** A {@code new} (unleased) row with id 0; the store assigns the real id. */
        public TransactionRow build() {
            return new TransactionRow(0, elementKey, fileId, sourceName, receivedAt, isaControl, gsControl,
                stControl, gs08, transactionType, senderId, receiverId, interchangeAt, schemaId, rawX12,
                parserErrorCount, envelope == null ? ENVELOPE_FILE : envelope,
                Status.NEW, null, null, null);
        }
    }
}

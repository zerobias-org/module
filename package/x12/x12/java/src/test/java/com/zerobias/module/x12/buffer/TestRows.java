package com.zerobias.module.x12.buffer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Shared fixtures for buffer tests: row builders and a deterministic clock. */
public final class TestRows {

    public static final Instant BASE = Instant.parse("2026-09-22T00:00:00Z");
    public static final String SCHEMA_835 = "schema:table:x12.005010X221A1.835";
    public static final String SCHEMA_837P = "schema:table:x12.005010X222A1.837P";
    /** {@code <path>@<hash12>} ids (DESIGN §2.1); the raw paths are {@link FileRow#pathOf}. */
    public static final String FILE_A = "/var/lib/x12/inbox/remit-a.835@0a1b2c3d4e5f";
    public static final String FILE_B = "/var/lib/x12/inbox/claims-b.837@f5e4d3c2b1a0";

    private TestRows() {
    }

    /** An 835 transaction row from FILE_A; element key {@code <fileId>:<gs>:<st>}. */
    public static TransactionRow tx(String gsControl, String stControl, long offsetSec) {
        return tx(FILE_A, "inbox", gsControl, stControl, offsetSec, "005010X221A1", "835", SCHEMA_835, "PAYERA");
    }

    public static TransactionRow tx(String fileId, String source, String gsControl, String stControl,
            long offsetSec, String gs08, String type, String schemaId, String sender) {
        return TransactionRow.builder()
            .fileId(fileId).sourceName(source).gsControl(gsControl).stControl(stControl)
            .deriveElementKey()
            .receivedAt(BASE.plusSeconds(offsetSec))
            .isaControl("000000001").gs08(gs08).transactionType(type)
            .senderId(sender).receiverId("PROVIDER1")
            .interchangeAt(BASE.minusSeconds(3600))
            .schemaId(schemaId)
            .rawX12(("ST*" + type + "*" + stControl + "~SE*2*" + stControl + "~").getBytes())
            .mappedJson("{\"header\":{\"st\":{\"st02\":\"" + stControl + "\"}}}")
            .build();
    }

    public static FileRow file(String fileId, String source, String checksum, FileStatus status, int txCount) {
        String path = FileRow.pathOf(fileId);
        return new FileRow(0, fileId, path, FileRow.fileNameOf(fileId), source, path + ".done", 1234L, checksum,
            BASE.minusSeconds(60), BASE, status == FileStatus.CONSUMED ? BASE : null, status, 1, txCount,
            status == FileStatus.ERROR ? "boom" : null, false, 0);
    }

    /** Test clock whose instant can be advanced to exercise TTL/retention deterministically. */
    public static final class MutableClock extends Clock {
        private Instant instant;

        public MutableClock(Instant start) {
            this.instant = start;
        }

        public void advance(Duration d) {
            this.instant = instant.plus(d);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}

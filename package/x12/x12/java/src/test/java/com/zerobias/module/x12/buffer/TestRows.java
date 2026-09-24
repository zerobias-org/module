package com.zerobias.module.x12.buffer;

import com.zerobias.module.x12.materializer.TransactionJson;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Shared fixtures for buffer tests: row builders and a deterministic clock. */
public final class TestRows {

    public static final Instant BASE = Instant.parse("2026-09-22T00:00:00Z");
    public static final String SCHEMA_835 = "schema:table:x12.005010X221A1.835";
    public static final String SCHEMA_837P = "schema:table:x12.005010X222A1.837P";
    /** {@code <path>@<hash12>} ids (DESIGN §2.1); the raw paths are {@link FileRow#pathOf}. */
    public static final String FILE_A = "/var/lib/x12/inbox/remit-a.835@0a1b2c3d4e5f";
    public static final String FILE_B = "/var/lib/x12/inbox/claims-b.837@f5e4d3c2b1a0";
    /** The ISA13 every fixture row carries. */
    public static final String ISA13 = "000000001";

    private TestRows() {
    }

    /** The ingest element key ({@link TransactionJson#elementKey}) of a fixture row: ISA13 is {@link #ISA13}. */
    public static String key(String fileId, String gsControl, String stControl) {
        return TransactionJson.elementKey(fileId, ISA13, gsControl, stControl);
    }

    /**
     * Insert one transaction row outside the consume path; false when its element key is
     * taken. Production only inserts through {@link BufferStore#consumeFile} (a whole file,
     * all-or-nothing); tests seed rows one at a time.
     */
    public static boolean insert(BufferStore store, TransactionRow row) throws SQLException {
        synchronized (store) {
            return store.insertTransactionUnsynchronized(row);
        }
    }

    /** An 835 transaction row from FILE_A; element key {@link #key}{@code (FILE_A, gs, st)}. */
    public static TransactionRow tx(String gsControl, String stControl, long offsetSec) {
        return tx(FILE_A, "inbox", gsControl, stControl, offsetSec, "005010X221A1", "835", SCHEMA_835, "PAYERA");
    }

    public static TransactionRow tx(String fileId, String source, String gsControl, String stControl,
            long offsetSec, String gs08, String type, String schemaId, String sender) {
        return TransactionRow.builder()
            .fileId(fileId).sourceName(source).gsControl(gsControl).stControl(stControl)
            .elementKey(key(fileId, gsControl, stControl))
            .receivedAt(BASE.plusSeconds(offsetSec))
            .isaControl(ISA13).gs08(gs08).transactionType(type)
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

    /**
     * Consume {@code n} transactions of {@code rawBytes} each under {@code fileId} and ack
     * them all: a buffer spanning many pages of data retention may evict.
     */
    public static void seedAcked(BufferStore s, String fileId, int n, int rawBytes) throws SQLException {
        List<TransactionRow> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String st = String.format("%04d", i);
            rows.add(TransactionRow.builder()
                .fileId(fileId).sourceName("inbox").gsControl("1").stControl(st)
                .elementKey(key(fileId, "1", st))
                .receivedAt(BASE.plusSeconds(i))
                .isaControl(ISA13).gs08("005010X221A1").transactionType("835").schemaId(SCHEMA_835)
                .rawX12(new byte[rawBytes]).mappedJson("{}")
                .build());
        }
        s.consumeFile(file(fileId, "inbox", "seed-" + fileId, FileStatus.CONSUMED, n), rows);
        s.ack(s.takeWhere(null, n, Duration.ofHours(1)).leaseId(), null);
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

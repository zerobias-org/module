package com.zerobias.module.x12.buffer;

import com.zerobias.module.x12.materializer.EntityGraph;

import java.util.List;
import java.util.Map;

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
            .build();
    }

    /**
     * The minimal object graph a seeded row needs so its content is readable: a transaction
     * root whose {@code st} child carries {@code st02}. The document is no longer a column
     * (DESIGN §8.4), so a row without a graph has no content — which is what
     * {@code validate} reports.
     */
    public static List<EntityGraph.Entity> graph(String schemaId, String stControl) {
        return graphFromMap(schemaId, Map.of("header", Map.of("st", Map.of("st02", stControl))));
    }

    /** A one-instance graph whose root carries these string scalars, in order. */
    public static List<EntityGraph.Entity> graphOf(String schemaId, Map<String, String> scalars) {
        EntityGraph.Entity root = EntityGraph.Entity.of(0, null, schemaId, "ST_LOOP", "loop", null, "", 0);
        for (Map.Entry<String, String> e : scalars.entrySet()) {
            root.propertyOrder.add(e.getKey());
            root.values.add(new EntityGraph.Value(e.getKey(), "string", e.getValue(), null, null));
        }
        return List.of(root);
    }

    /**
     * A graph from a nested map, for tests that used to hand-write a {@code mapped_json} body:
     * a nested map becomes a child instance (its key is the property), a scalar becomes a
     * value on the current instance. Types are inferred so numeric filters see numbers.
     */
    public static List<EntityGraph.Entity> graphFromMap(String schemaId, Map<String, Object> body) {
        List<EntityGraph.Entity> out = new java.util.ArrayList<>();
        EntityGraph.Entity root = EntityGraph.Entity.of(0, null, schemaId, "ST_LOOP", "loop", null, "", 0);
        out.add(root);
        walk(out, root, body, "");
        return out;
    }

    private static void walk(List<EntityGraph.Entity> out, EntityGraph.Entity parent,
            Map<String, Object> node, String path) {
        for (Map.Entry<String, Object> e : node.entrySet()) {
            parent.propertyOrder.add(e.getKey());
            if (e.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> child = (Map<String, Object>) e.getValue();
                String childPath = path.isEmpty() ? e.getKey() : path + "." + e.getKey();
                EntityGraph.Entity entity = EntityGraph.Entity.of(out.size(), parent.localId,
                    "schema:type:x12.test." + e.getKey().toUpperCase(java.util.Locale.ROOT),
                    e.getKey().toUpperCase(java.util.Locale.ROOT), "segment", e.getKey(), childPath, 0);
                out.add(entity);
                walk(out, entity, child, childPath);
            } else if (e.getValue() != null) {
                final Object v = e.getValue();
                final String type = v instanceof Number ? "decimal" : "string";
                parent.values.add(new EntityGraph.Value(e.getKey(), type, String.valueOf(v),
                    v instanceof Number ? new java.math.BigDecimal(v.toString()) : null, null));
            }
        }
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

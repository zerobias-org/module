package com.zerobias.module.x12.materializer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imsweb.x12.Loop;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The stored {@code mapped_json} of one transaction set (DESIGN §5): the envelope overlay
 * (authoritative, top level, fixed key order) followed by the materialized body from the
 * guide's {@link Materializer} (nothing when the guide has no index — the envelope-only
 * degrade). Both the ingest path ({@code FileConsumer}) and re-materialization
 * ({@code ops/recast} / {@code ops/validate}) build the JSON here, so a fresh row
 * reproduces byte-for-byte and {@code repsAgree} means what it says.
 */
public final class TransactionJson {

    private static final Gson GSON = new GsonBuilder().create();

    private TransactionJson() {
    }

    /** The envelope values that overlay the body; all are envelope columns of the row. */
    public record Envelope(
            String elementKey,
            String fileId,
            String fileName,
            String sourceName,
            String isaControlNumber,
            String gsControlNumber,
            String stControlNumber,
            String gs08,
            String transactionType,
            String senderId,
            String receiverId,
            Instant interchangeAt,
            Instant receivedAt,
            String envelope,
            int parserErrorCount) {
    }

    /** {@code <fileId>:<GS06>:<ST02>} — the collection element key (DESIGN §2.1). */
    public static String elementKey(String fileId, String gsControl, String stControl) {
        return fileId + ":" + gsControl + ":" + stControl;
    }

    /** Envelope overlay + materialized body, in the stored key order. */
    public static Map<String, Object> build(Envelope e, Optional<Materializer> materializer, Loop stLoop) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("elementKey", e.elementKey());
        json.put("fileId", e.fileId());
        json.put("fileName", e.fileName());
        json.put("sourceName", e.sourceName());
        json.put("isaControlNumber", e.isaControlNumber());
        json.put("gsControlNumber", e.gsControlNumber());
        json.put("stControlNumber", e.stControlNumber());
        json.put("gs08", e.gs08());
        json.put("transactionType", e.transactionType());
        json.put("senderId", e.senderId());
        json.put("receiverId", e.receiverId());
        if (e.interchangeAt() != null) {
            json.put("interchangeDate", e.interchangeAt().toString());
        }
        json.put("receivedAt", e.receivedAt() == null ? null : e.receivedAt().toString());
        json.put("envelope", e.envelope());
        json.put("parserErrorCount", e.parserErrorCount());
        if (materializer.isPresent() && stLoop != null) {
            json.putAll(materializer.get().materializeTransaction(stLoop));
        }
        return json;
    }

    public static String toJson(Map<String, Object> tree) {
        return GSON.toJson(tree);
    }
}

package com.zerobias.module.ocsf.inbox;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.zerobias.module.ocsf.OcsfClasses;
import com.zerobias.module.ocsf.buffer.EventRow;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

/**
 * Splits one event file into OCSF events (DESIGN §4 steps 2–3). By extension: {@code .ndjson} /
 * {@code .jsonl} → one event per non-empty line; {@code .json} → one object or an array of
 * objects; a trailing {@code .gz} is gunzipped first, never past {@code maxFileBytes}.
 *
 * <p>An event is <b>accepted</b> when it is a JSON object with an integer {@code class_uid} the
 * interface declares and is at most {@code maxEventBytes}; anything else is <b>rejected</b> with
 * a reason and the file goes on. Bodies are kept as written: an NDJSON line verbatim, a
 * {@code .json} object verbatim, an array element re-serialized compactly (Gson keeps the
 * number text, so values are unchanged). There is no OCSF schema validation: real events miss
 * required attributes, and dropping them would drop the evidence (interface Implementing.md).
 */
public final class EventSplitter {

    /** A file this splitter cannot read at all: it goes to {@code .error}. */
    public static final class UnsupportedFileException extends Exception {
        public UnsupportedFileException(String message) {
            super(message);
        }
    }

    /** {@code events} accepted, {@code rejected} counted, the first {@link #MAX_REASONS} reasons kept. */
    public record Split(List<EventRow> events, int rejected, List<String> reasons) {
    }

    static final int MAX_REASONS = 20;
    private static final Gson GSON = new Gson();

    private final OcsfClasses classes;
    private final long maxFileBytes;
    private final long maxEventBytes;

    public EventSplitter(OcsfClasses classes, long maxFileBytes, long maxEventBytes) {
        this.classes = Objects.requireNonNull(classes, "classes");
        this.maxFileBytes = maxFileBytes;
        this.maxEventBytes = maxEventBytes;
    }

    public Split split(byte[] bytes, String fileName, String fileId, String sourceName, Instant receivedAt)
            throws UnsupportedFileException {
        String name = fileName.toLowerCase(Locale.ROOT);
        byte[] content = bytes;
        if (name.endsWith(".gz")) {
            content = gunzip(bytes);
            name = name.substring(0, name.length() - 3);
        }
        String text = new String(content, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1);   // a UTF-8 BOM is not part of the first event
        }
        Collector out = new Collector(fileId, sourceName, receivedAt);
        if (name.endsWith(".ndjson") || name.endsWith(".jsonl")) {
            String[] lines = text.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].endsWith("\r") ? lines[i].substring(0, lines[i].length() - 1) : lines[i];
                if (!line.isBlank()) {
                    out.event(line, i + 1);
                }
            }
        } else if (name.endsWith(".json")) {
            JsonElement doc;
            try {
                doc = strictParse(text);
            } catch (JsonParseException | IOException e) {
                throw new UnsupportedFileException("invalid-json: " + e.getMessage());
            }
            if (doc.isJsonObject()) {
                out.event(text.strip(), 1);
            } else if (doc.isJsonArray()) {
                int i = 0;
                for (JsonElement e : doc.getAsJsonArray()) {
                    i++;
                    if (e.isJsonObject()) {
                        out.event(GSON.toJson(e), i);
                    } else {
                        out.reject(i, "not-an-object");
                    }
                }
            } else {
                throw new UnsupportedFileException("not-an-event: a .json file holds an object or an array of objects");
            }
        } else {
            throw new UnsupportedFileException("unsupported-format: expected .ndjson, .jsonl or .json (optionally .gz)");
        }
        return new Split(out.events, out.rejected, out.reasons);
    }

    /** One accumulating pass over a file's candidate events. */
    private final class Collector {
        final List<EventRow> events = new ArrayList<>();
        final List<String> reasons = new ArrayList<>();
        int rejected;
        final String fileId;
        final String sourceName;
        final Instant receivedAt;

        Collector(String fileId, String sourceName, Instant receivedAt) {
            this.fileId = fileId;
            this.sourceName = sourceName;
            this.receivedAt = receivedAt;
        }

        void reject(int line, String reason) {
            rejected++;
            if (reasons.size() < MAX_REASONS) {
                reasons.add("line " + line + ": " + reason);
            }
        }

        void event(String body, int line) {
            if (body.getBytes(StandardCharsets.UTF_8).length > maxEventBytes) {
                reject(line, "too-large: over maxEventBytes " + maxEventBytes);
                return;
            }
            JsonElement parsed;
            try {
                parsed = strictParse(body);
            } catch (JsonParseException | IOException e) {
                reject(line, "invalid-json");
                return;
            }
            if (!parsed.isJsonObject()) {
                reject(line, "not-an-object");
                return;
            }
            JsonObject o = parsed.getAsJsonObject();
            Long classUid = integral(o, "class_uid");
            if (classUid == null) {
                reject(line, "no-class_uid: missing or not an integer");
                return;
            }
            if (classUid > Integer.MAX_VALUE || classes.byUid(classUid.intValue()).isEmpty()) {
                reject(line, "unknown-class: class_uid " + classUid + " is not a core OCSF " + classes.ocsfVersion() + " class");
                return;
            }
            JsonObject metadata = o.has("metadata") && o.get("metadata").isJsonObject() ? o.getAsJsonObject("metadata") : null;
            String uid = metadata == null ? null : string(metadata, "uid");
            String key = uid == null || uid.isBlank() ? fileId + "#" + line : uid;
            events.add(new EventRow(0, key, classUid.intValue(), intOrNull(integral(o, "category_uid")),
                integral(o, "time"), intOrNull(integral(o, "severity_id")),
                metadata == null ? null : string(metadata, "version"), fileId, sourceName, line, receivedAt, body));
        }
    }

    /** RFC 8259 JSON only: no comments, single quotes, NaN, or trailing data (Gson's default parser is lenient). */
    static JsonElement strictParse(String text) throws IOException {
        JsonReader reader = new JsonReader(new StringReader(text));
        reader.setLenient(false);
        // JsonParser.parseReader would force the reader lenient; the adapter keeps it strict.
        JsonElement e = GSON.getAdapter(JsonElement.class).read(reader);
        if (reader.peek() != JsonToken.END_DOCUMENT) {
            throw new JsonParseException("trailing data after the JSON value");
        }
        return e;
    }

    /** The value as a long when it is an integral JSON number, else null (a string "3002" is not an integer). */
    private static Long integral(JsonObject o, String field) {
        JsonElement e = o.get(field);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        try {
            return e.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException | NumberFormatException notIntegral) {
            return null;
        }
    }

    private static Integer intOrNull(Long v) {
        return v == null || v > Integer.MAX_VALUE || v < Integer.MIN_VALUE ? null : v.intValue();
    }

    private static String string(JsonObject o, String field) {
        JsonElement e = o.get(field);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }

    /** Gunzip, refusing to inflate past {@code maxFileBytes} (a small .gz can expand without bound). */
    private byte[] gunzip(byte[] gz) throws UnsupportedFileException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[64 * 1024];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > maxFileBytes) {
                    throw new UnsupportedFileException("too-large: decompresses past maxFileBytes " + maxFileBytes);
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UnsupportedFileException("invalid-gzip: " + e.getMessage());
        }
    }
}

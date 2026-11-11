package com.zerobias.module.ocsf.buffer;

import java.time.Instant;

/**
 * One row of the {@code events} table (DESIGN §5): one OCSF event, {@code body} exactly as
 * the source wrote it. The other fields are copies of top-level event fields, extracted at
 * ingest so the OCSF interface operations can filter and sort without parsing bodies.
 *
 * @param seq       row id, 0 before insert
 * @param eventKey  {@code metadata.uid}, else {@code <fileId>#<line>}
 * @param time      OCSF {@code time} (epoch-millis), null when the event has none
 */
public record EventRow(
    long seq,
    String eventKey,
    int classUid,
    Integer categoryUid,
    Long time,
    Integer severityId,
    String metadataVersion,
    String fileId,
    String sourceName,
    int line,
    Instant receivedAt,
    String body) {

    /** The same event under another key: the conflict copy of DESIGN §2. */
    public EventRow withKey(String key) {
        return new EventRow(seq, key, classUid, categoryUid, time, severityId, metadataVersion,
            fileId, sourceName, line, receivedAt, body);
    }
}

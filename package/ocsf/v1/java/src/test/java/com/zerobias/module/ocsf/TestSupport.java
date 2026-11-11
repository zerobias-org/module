package com.zerobias.module.ocsf;

import com.zerobias.module.ocsf.buffer.EventRow;
import com.zerobias.module.ocsf.buffer.FileRow;
import com.zerobias.module.ocsf.buffer.FileStatus;
import com.zerobias.module.ocsf.health.PollerStatus;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** Shared test fixtures: a settable clock, a poller stub, OCSF events and file rows. */
public final class TestSupport {

    public static final Instant BASE = Instant.parse("2026-10-20T12:00:00Z");
    public static final String FILE_A = "/var/lib/ocsf/inbox/auth-a.ndjson@0a1b2c3d4e5f";

    /** Authentication (class 3002, category 3) in the interface the jar bundles. */
    public static final int AUTHENTICATION = 3002;

    private TestSupport() {
    }

    /** The class table from the bundled interface spec (target/classes/ocsf/api.yml). */
    public static OcsfClasses classes() {
        return OcsfClasses.fromClasspath();
    }

    /** A one-line OCSF event of {@code classUid} with this {@code metadata.uid} and {@code time}. */
    public static String event(int classUid, String uid, long time) {
        return "{\"class_uid\":" + classUid + ",\"category_uid\":" + (classUid / 1000) + ",\"time\":" + time
            + ",\"severity_id\":1,\"metadata\":{\"uid\":\"" + uid + "\",\"version\":\"1.1.0\"}}";
    }

    public static EventRow eventRow(String fileId, String uid, long time) {
        return new EventRow(0, uid, AUTHENTICATION, 3, time, 1, "1.1.0", fileId, "inbox", 1, BASE,
            event(AUTHENTICATION, uid, time));
    }

    public static FileRow file(String fileId, String checksum, FileStatus status) {
        return new FileRow(0, fileId, FileRow.pathOf(fileId), FileRow.fileNameOf(fileId), "inbox",
            FileRow.pathOf(fileId) + ".done", 100, checksum, BASE.minusSeconds(60), BASE,
            status == FileStatus.ERROR ? null : BASE, status, null, 0, null,
            status == FileStatus.ERROR ? "boom" : null, false, 0);
    }

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

    /** A poller stub: one writable source at {@code inboxDir}. */
    public static final class StubPoller implements PollerHandle {
        private final Path inboxDir;

        public StubPoller(Path inboxDir) {
            this.inboxDir = inboxDir;
        }

        @Override
        public RescanResult rescan(String source) {
            return new RescanResult(0, 0, 0, 0);
        }

        @Override
        public void close() {
        }

        @Override
        public boolean up() {
            return true;
        }

        @Override
        public Optional<Instant> lastScan() {
            return Optional.of(BASE.plusSeconds(100));
        }

        @Override
        public Optional<Instant> lastConsumed() {
            return Optional.empty();
        }

        @Override
        public boolean backpressure() {
            return false;
        }

        @Override
        public List<PollerStatus.SourceStatus> sources() {
            return List.of(new PollerStatus.SourceStatus("inbox", inboxDir.toString(), true, 0, 0));
        }
    }
}

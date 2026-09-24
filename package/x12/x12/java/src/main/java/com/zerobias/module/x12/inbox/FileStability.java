package com.zerobias.module.x12.inbox;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The stability window (DESIGN §4.2 step 2, CLAUDE.md "stability window before reading"):
 * a file is read only once its {@code (size, mtime)} pair has been unchanged for at least
 * {@code stableFor} across polls. The first sighting starts the clock; any change restarts
 * it. Daily drops arrive as partial writes — never parse a file that is still growing.
 * One instance per poller; not thread-safe (the poller's scan is serialized).
 */
public final class FileStability {

    /** What was seen for a path and since when. */
    public record Sighting(long size, Instant mtime, Instant unchangedSince, Instant firstSeen) {
    }

    private final Duration stableFor;
    private final Map<Path, Sighting> sightings = new HashMap<>();

    public FileStability(Duration stableFor) {
        this.stableFor = stableFor == null || stableFor.isNegative() ? Duration.ZERO : stableFor;
    }

    /**
     * Record the current {@code (size, mtime)} of a path at {@code now}; returns true when
     * the file has been unchanged for at least the window (a zero window means "stable on
     * first sighting").
     */
    public boolean observe(Path path, long size, Instant mtime, Instant now) {
        Sighting prev = sightings.get(path);
        Sighting cur;
        if (prev == null) {
            cur = new Sighting(size, mtime, now, now);
        } else if (prev.size() != size || !prev.mtime().equals(mtime)) {
            cur = new Sighting(size, mtime, now, prev.firstSeen());
        } else {
            cur = prev;
        }
        sightings.put(path, cur);
        return !Duration.between(cur.unchangedSince(), now).minus(stableFor).isNegative();
    }

    /** True when this scan is the first time the path was seen. */
    public boolean isNew(Path path) {
        return !sightings.containsKey(path);
    }

    public Optional<Sighting> sighting(Path path) {
        return Optional.ofNullable(sightings.get(path));
    }

    /** Stop tracking a path (consumed, errored, or gone). */
    public void forget(Path path) {
        sightings.remove(path);
    }

    /** Drop every tracked path not in {@code present} (files that disappeared between scans). */
    public void retainOnly(Set<Path> present) {
        Iterator<Path> it = sightings.keySet().iterator();
        while (it.hasNext()) {
            if (!present.contains(it.next())) {
                it.remove();
            }
        }
    }
}

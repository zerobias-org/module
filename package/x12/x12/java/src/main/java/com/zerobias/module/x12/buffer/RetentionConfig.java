package com.zerobias.module.x12.buffer;

import java.time.Duration;

/**
 * Retention policy for acked transactions (DESIGN §8, {@code config.retention}).
 * Both bounds are optional; when both are set, whichever fires first wins. Only
 * {@code acked} transaction rows are ever swept — un-acked rows and {@code files}
 * rows are never evicted by retention.
 *
 * @param maxAge   delete acked rows older than this, or null to disable
 * @param maxBytes evict oldest acked rows while the db exceeds this size, or null
 */
public record RetentionConfig(Duration maxAge, Long maxBytes) {

    public static RetentionConfig none() {
        return new RetentionConfig(null, null);
    }

    public boolean isBounded() {
        return maxAge != null || maxBytes != null;
    }
}

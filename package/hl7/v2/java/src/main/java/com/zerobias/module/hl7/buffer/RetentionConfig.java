package com.zerobias.module.hl7.buffer;

import java.time.Duration;

/**
 * Retention policy for acked messages (DESIGN §8.3), sourced from
 * {@code runtimeConfig.durability.retention}. Both bounds are optional; when
 * both are set, whichever fires first wins. Only {@code acked} rows are ever
 * swept — un-acked messages are never evicted by retention.
 *
 * @param maxAge   delete acked rows older than this, or null to disable
 * @param maxBytes evict oldest acked rows while the db exceeds this size, or null
 */
public record RetentionConfig(Duration maxAge, Long maxBytes) {

    public static RetentionConfig none() {
        return new RetentionConfig(null, null);
    }

    public static RetentionConfig maxAge(Duration maxAge) {
        return new RetentionConfig(maxAge, null);
    }
}

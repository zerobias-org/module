package com.zerobias.module.hl7.buffer;

import java.util.List;

/**
 * The result of a {@code take} (DESIGN §2.5): a lease over a batch of messages
 * marked {@code in_flight}. {@code leaseId} is null when nothing was available.
 * {@code remaining} is the approximate drainable backlog after this lease — the
 * consumer uses it to pace.
 */
public record Lease(String leaseId, List<BufferRow> messages, long remaining) {

    public static Lease empty(long remaining) {
        return new Lease(null, List.of(), remaining);
    }

    public boolean isEmpty() {
        return leaseId == null || messages.isEmpty();
    }
}

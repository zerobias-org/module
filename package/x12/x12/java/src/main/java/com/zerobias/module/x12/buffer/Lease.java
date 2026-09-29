package com.zerobias.module.x12.buffer;

import java.util.List;

/**
 * The result of a {@code take} (DESIGN §2.5): a lease over a batch of transaction
 * sets marked {@code in_flight}. {@code leaseId} is null when nothing was available.
 * {@code remaining} is the approximate drainable backlog after this lease — the
 * consumer uses it to pace.
 */
public record Lease(String leaseId, List<TransactionRow> transactions, long remaining) {

    public static Lease empty(long remaining) {
        return new Lease(null, List.of(), remaining);
    }

    public boolean isEmpty() {
        return leaseId == null || transactions.isEmpty();
    }
}

package com.metro.domain;

import java.time.Instant;

/**
 * One immutable line in a card's wallet ledger. Balance is the sum of amounts; nothing
 * ever edits a balance directly. Debits are negative.
 */
public record LedgerEntry(
        String id,
        Type type,
        long amountMinor,
        String idempotencyKey,
        Instant at,
        String note) {

    public enum Type { TOP_UP, FARE, PENALTY }
}

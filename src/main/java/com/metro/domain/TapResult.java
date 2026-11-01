package com.metro.domain;

/** Outcome of a gate tap. Stored per idempotency key so retries return the same answer. */
public record TapResult(
        Type type,
        String stationId,
        long chargedMinor,
        boolean penaltyApplied,
        long balanceMinor,
        String message) {

    public enum Type { IN, OUT }
}

package com.metro.domain;

import java.time.Instant;

/**
 * A journey on a card. entryStationId / exitStationId / tapIn / tapOut are null when the
 * matching tap is missing (INCOMPLETE trips).
 */
public record Trip(
        String id,
        String entryStationId,
        String exitStationId,
        Instant tapIn,
        Instant tapOut,
        long fareMinor,
        Status status) {

    public enum Status { OPEN, COMPLETED, INCOMPLETE }
}

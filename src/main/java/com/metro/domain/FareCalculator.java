package com.metro.domain;

import java.time.Instant;

/** Prices a single journey. The daily cap is applied later, against the card's history. */
public final class FareCalculator {

    private final FarePolicy policy;

    public FareCalculator(FarePolicy policy) {
        this.policy = policy;
    }

    /**
     * @param tapIn when the journey started; peak status is decided by entry time
     * @return fare in minor units
     */
    public long fare(Route route, Instant tapIn) {
        long fare = policy.slabs().stream()
                .filter(s -> route.distanceMeters() <= s.upToMeters())
                .findFirst()
                .orElseThrow()
                .fareMinor();
        if (!policy.isPeak(tapIn)) {
            fare -= Math.floorDiv(fare * policy.offPeakDiscountBps(), 10_000L);
        }
        return fare;
    }
}

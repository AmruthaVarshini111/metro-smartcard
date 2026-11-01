package com.metro.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class FareCalculatorTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final FareCalculator calculator = new FareCalculator(FarePolicy.defaults());

    private static Route route(int meters) {
        return new Route(List.of(new Station("X", "X", "X")), meters, 0, 0);
    }

    /** 2026-11-02 is a Monday; 2026-11-01 is a Sunday. */
    private static Instant at(int day, int hour, int minute) {
        return ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, IST).toInstant();
    }

    private static final Instant MONDAY_PEAK = at(2, 9, 0);
    private static final Instant MONDAY_MIDDAY = at(2, 12, 0);

    @Test
    void slabBoundariesAreInclusiveOnTheUpperEnd() {
        assertEquals(1000, calculator.fare(route(0), MONDAY_PEAK));
        assertEquals(1000, calculator.fare(route(2000), MONDAY_PEAK));
        assertEquals(2000, calculator.fare(route(2001), MONDAY_PEAK));
        assertEquals(2000, calculator.fare(route(5000), MONDAY_PEAK));
        assertEquals(3000, calculator.fare(route(5001), MONDAY_PEAK));
        assertEquals(4000, calculator.fare(route(21000), MONDAY_PEAK));
        assertEquals(6000, calculator.fare(route(500_000), MONDAY_PEAK));
    }

    @Test
    void offPeakGetsTenPercentOff() {
        assertEquals(1800, calculator.fare(route(3000), MONDAY_MIDDAY));
    }

    @Test
    void peakWindowStartsInclusiveAndEndsExclusive() {
        assertEquals(2000, calculator.fare(route(3000), at(2, 8, 0)));    // first peak minute
        assertEquals(1800, calculator.fare(route(3000), at(2, 7, 59)));   // just before
        assertEquals(2000, calculator.fare(route(3000), at(2, 18, 59)));  // last peak minute
        assertEquals(1800, calculator.fare(route(3000), at(2, 19, 0)));   // window is over
    }

    @Test
    void weekendsAreAlwaysOffPeak() {
        assertEquals(1800, calculator.fare(route(3000), at(1, 9, 0))); // Sunday, rush hour
    }

    @Test
    void policyRejectsSlabsThatDoNotCoverEverything() {
        List<FarePolicy.Slab> open = List.of(new FarePolicy.Slab(2000, 1000));

        assertThrows(IllegalArgumentException.class,
                () -> new FarePolicy(open, 0, List.of(), 10_000, 5_000, 2_000, IST));
    }

    @Test
    void policyRejectsUnorderedSlabs() {
        List<FarePolicy.Slab> bad = List.of(
                new FarePolicy.Slab(5000, 2000),
                new FarePolicy.Slab(2000, 1000),
                new FarePolicy.Slab(Integer.MAX_VALUE, 3000));

        assertThrows(IllegalArgumentException.class,
                () -> new FarePolicy(bad, 0, List.of(), 10_000, 5_000, 2_000, IST));
    }

    @Test
    void peakWindowEndpointsAreWallClockTimes() {
        FarePolicy.PeakWindow w = new FarePolicy.PeakWindow(LocalTime.of(8, 0), LocalTime.of(10, 0));

        assertEquals(true, w.contains(LocalTime.of(8, 0)));
        assertEquals(false, w.contains(LocalTime.of(10, 0)));
    }
}

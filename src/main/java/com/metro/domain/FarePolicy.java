package com.metro.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * All pricing rules in one immutable value. Money is always an integer number of minor
 * units (paise), never a double, so fares and balances add up exactly.
 *
 * @param slabs                    ascending by distance; the last slab must cover everything
 * @param offPeakDiscountBps       discount in basis points (1000 = 10%) outside peak hours
 * @param peakWindows              weekday windows, start inclusive, end exclusive
 * @param dailyCapMinor            maximum total fares charged per calendar day
 * @param penaltyMinor             charged for a missing tap-in or tap-out
 * @param minimumEntryBalanceMinor balance required to tap in
 */
public record FarePolicy(
        List<Slab> slabs,
        int offPeakDiscountBps,
        List<PeakWindow> peakWindows,
        long dailyCapMinor,
        long penaltyMinor,
        long minimumEntryBalanceMinor,
        ZoneId zone) {

    public record Slab(int upToMeters, long fareMinor) {
    }

    public record PeakWindow(LocalTime start, LocalTime end) {
        boolean contains(LocalTime t) {
            return !t.isBefore(start) && t.isBefore(end);
        }
    }

    public FarePolicy {
        slabs = List.copyOf(slabs);
        peakWindows = List.copyOf(peakWindows);
        if (slabs.isEmpty() || slabs.get(slabs.size() - 1).upToMeters() != Integer.MAX_VALUE) {
            throw new IllegalArgumentException("last slab must have upToMeters = Integer.MAX_VALUE");
        }
        for (int i = 1; i < slabs.size(); i++) {
            if (slabs.get(i).upToMeters() <= slabs.get(i - 1).upToMeters()) {
                throw new IllegalArgumentException("slabs must be strictly ascending");
            }
        }
        if (offPeakDiscountBps < 0 || offPeakDiscountBps > 10_000) {
            throw new IllegalArgumentException("discount must be between 0 and 10000 bps");
        }
    }

    /** Rupee-style defaults: slabs of Rs 10 to Rs 60, 10% off-peak discount, Rs 100 daily cap. */
    public static FarePolicy defaults() {
        return new FarePolicy(
                List.of(
                        new Slab(2_000, 1_000),
                        new Slab(5_000, 2_000),
                        new Slab(12_000, 3_000),
                        new Slab(21_000, 4_000),
                        new Slab(32_000, 5_000),
                        new Slab(Integer.MAX_VALUE, 6_000)),
                1_000,
                List.of(
                        new PeakWindow(LocalTime.of(8, 0), LocalTime.of(10, 0)),
                        new PeakWindow(LocalTime.of(17, 0), LocalTime.of(19, 0))),
                10_000,
                5_000,
                2_000,
                ZoneId.of("Asia/Kolkata"));
    }

    /** Peak pricing applies on weekdays inside a peak window; weekends are always off-peak. */
    public boolean isPeak(Instant at) {
        ZonedDateTime local = at.atZone(zone);
        DayOfWeek day = local.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = local.toLocalTime();
        return peakWindows.stream().anyMatch(w -> w.contains(time));
    }
}

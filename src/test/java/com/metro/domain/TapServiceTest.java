package com.metro.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class TapServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final MetroNetwork network = SampleNetwork.build();
    private final FarePolicy policy = FarePolicy.defaults();
    private final InMemoryCardRepository cards = new InMemoryCardRepository();
    private final TapService service = new TapService(
            network, new RoutePlanner(network), new FareCalculator(policy), policy, cards);

    /** 2026-11-02 is a Monday; 2026-11-01 is a Sunday. */
    private static Instant at(int day, int hour, int minute) {
        return ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, IST).toInstant();
    }

    private SmartCard cardWith(long balanceMinor) {
        SmartCard card = cards.create("C1");
        if (balanceMinor > 0) {
            service.topUp("C1", balanceMinor, "topup-initial", at(2, 8, 0));
        }
        return card;
    }

    @Test
    void completedTripDebitsTheFareAndWritesLedgerEntries() {
        SmartCard card = cardWith(10_000);

        service.tapIn("C1", "B01", at(2, 12, 0), "in-1");
        TapResult out = service.tapOut("C1", "B03", at(2, 12, 20), "out-1");

        // B01 -> B03 is 2700 m: Rs 20 slab, minus 10% off-peak = Rs 18.
        assertEquals(1800, out.chargedMinor());
        assertEquals(8200, card.balanceMinor());
        assertEquals(2, card.ledger().size());
        assertNull(card.openTrip());
        assertEquals(Trip.Status.COMPLETED, card.trips().get(0).status());
    }

    @Test
    void peakEntryPaysFullFare() {
        SmartCard card = cardWith(10_000);

        service.tapIn("C1", "B01", at(2, 9, 0), "in-1");
        TapResult out = service.tapOut("C1", "B03", at(2, 9, 20), "out-1");

        assertEquals(2000, out.chargedMinor());
        assertEquals(8000, card.balanceMinor());
    }

    @Test
    void sundayRushHourIsStillOffPeak() {
        cardWith(10_000);

        service.tapIn("C1", "B01", at(1, 9, 0), "in-1");
        TapResult out = service.tapOut("C1", "B03", at(1, 9, 20), "out-1");

        assertEquals(1800, out.chargedMinor());
    }

    @Test
    void tapInIsRejectedBelowMinimumBalanceAndLeavesNoState() {
        SmartCard card = cardWith(1_999);

        DomainException ex = assertThrows(DomainException.class,
                () -> service.tapIn("C1", "B01", at(2, 12, 0), "in-1"));

        assertEquals("INSUFFICIENT_BALANCE", ex.code());
        assertNull(card.openTrip());
        assertEquals(1_999, card.balanceMinor());
    }

    @Test
    void missedTapOutIsPenalisedOnTheNextTapIn() {
        SmartCard card = cardWith(10_000);
        service.tapIn("C1", "B01", at(2, 12, 0), "in-1");

        TapResult second = service.tapIn("C1", "B02", at(2, 15, 0), "in-2");

        assertTrue(second.penaltyApplied());
        assertEquals(5_000, second.chargedMinor());
        assertEquals(5_000, card.balanceMinor());
        assertEquals(Trip.Status.INCOMPLETE, card.trips().get(0).status());
        assertNotNull(card.openTrip());
        assertEquals("B02", card.openTrip().entryStationId());
    }

    @Test
    void penaltyCannotPushTheCardBelowTheEntryMinimum() {
        SmartCard card = cardWith(6_000);
        service.tapIn("C1", "B01", at(2, 12, 0), "in-1");

        // 6000 - 5000 penalty = 1000, below the 2000 minimum: reject without charging.
        DomainException ex = assertThrows(DomainException.class,
                () -> service.tapIn("C1", "B02", at(2, 15, 0), "in-2"));

        assertEquals("INSUFFICIENT_BALANCE", ex.code());
        assertEquals(6_000, card.balanceMinor());
        assertEquals("B01", card.openTrip().entryStationId());
    }

    @Test
    void tapOutWithoutTapInIsPenalised() {
        SmartCard card = cardWith(10_000);

        TapResult out = service.tapOut("C1", "B03", at(2, 12, 0), "out-1");

        assertTrue(out.penaltyApplied());
        assertEquals(5_000, out.chargedMinor());
        assertEquals(5_000, card.balanceMinor());
        assertEquals(Trip.Status.INCOMPLETE, card.trips().get(0).status());
    }

    @Test
    void tapOutBeforeTapInIsRejected() {
        cardWith(10_000);
        service.tapIn("C1", "B01", at(2, 12, 0), "in-1");

        DomainException ex = assertThrows(DomainException.class,
                () -> service.tapOut("C1", "B03", at(2, 11, 0), "out-1"));

        assertEquals("INVALID_TIME", ex.code());
    }

    @Test
    void dailyCapLimitsTotalFaresForTheDay() {
        SmartCard card = cardWith(50_000);
        List<Long> charges = new ArrayList<>();

        // B01 <-> B08 is 13400 m: Rs 40 slab, Rs 36 off-peak. Cap is Rs 100.
        for (int i = 0; i < 4; i++) {
            String from = i % 2 == 0 ? "B01" : "B08";
            String to = i % 2 == 0 ? "B08" : "B01";
            service.tapIn("C1", from, at(2, 11 + i, 0), "in-" + i);
            charges.add(service.tapOut("C1", to, at(2, 11 + i, 30), "out-" + i).chargedMinor());
        }

        assertEquals(List.of(3600L, 3600L, 2800L, 0L), charges);
        assertEquals(10_000, card.fareSpentOn(java.time.LocalDate.of(2026, 11, 2), IST));
        assertEquals(40_000, card.balanceMinor());
    }

    @Test
    void capResetsOnTheNextDay() {
        cardWith(50_000);
        for (int i = 0; i < 3; i++) {
            service.tapIn("C1", "B01", at(2, 11 + i, 0), "in-" + i);
            service.tapOut("C1", "B08", at(2, 11 + i, 30), "out-" + i);
        }

        service.tapIn("C1", "B01", at(3, 11, 0), "in-next");
        TapResult out = service.tapOut("C1", "B08", at(3, 11, 30), "out-next");

        assertEquals(3600, out.chargedMinor());
    }

    @Test
    void retriedTapsReturnTheOriginalResultAndNeverChargeTwice() {
        SmartCard card = cardWith(10_000);

        TapResult in1 = service.tapIn("C1", "B01", at(2, 12, 0), "in-1");
        TapResult in2 = service.tapIn("C1", "B01", at(2, 12, 0), "in-1");
        TapResult out1 = service.tapOut("C1", "B03", at(2, 12, 20), "out-1");
        TapResult out2 = service.tapOut("C1", "B03", at(2, 12, 20), "out-1");

        assertEquals(in1, in2);
        assertEquals(out1, out2);
        assertEquals(8200, card.balanceMinor());
        assertEquals(1, card.trips().size());
    }

    @Test
    void topUpIsIdempotentAndRejectsAConflictingReuseOfTheKey() {
        SmartCard card = cardWith(0);

        service.topUp("C1", 5_000, "tu-1", at(2, 8, 0));
        service.topUp("C1", 5_000, "tu-1", at(2, 8, 0));
        DomainException ex = assertThrows(DomainException.class,
                () -> service.topUp("C1", 9_000, "tu-1", at(2, 8, 0)));

        assertEquals(5_000, card.balanceMinor());
        assertEquals("IDEMPOTENCY_KEY_REUSED", ex.code());
    }

    @Test
    void topUpMustBePositiveAndNeedsAKey() {
        cardWith(0);

        assertEquals("INVALID_AMOUNT", assertThrows(DomainException.class,
                () -> service.topUp("C1", 0, "k", at(2, 8, 0))).code());
        assertEquals("MISSING_IDEMPOTENCY_KEY", assertThrows(DomainException.class,
                () -> service.topUp("C1", 100, " ", at(2, 8, 0))).code());
    }

    @Test
    void unknownCardAndUnknownStationAreRejected() {
        cardWith(10_000);

        assertEquals("CARD_NOT_FOUND", assertThrows(DomainException.class,
                () -> service.tapIn("NOPE", "B01", at(2, 12, 0), "k")).code());
        assertEquals("UNKNOWN_STATION", assertThrows(DomainException.class,
                () -> service.tapIn("C1", "ZZZ", at(2, 12, 0), "k")).code());
    }

    @Test
    void sixteenConcurrentRetriesOfOneTapOutChargeExactlyOnce() throws Exception {
        SmartCard card = cardWith(10_000);
        service.tapIn("C1", "B01", at(2, 12, 0), "in-1");

        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Callable<TapResult>> jobs = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                jobs.add(() -> service.tapOut("C1", "B03", at(2, 12, 20), "out-1"));
            }
            for (Future<TapResult> f : pool.invokeAll(jobs)) {
                assertEquals(1800, f.get().chargedMinor());
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(8200, card.balanceMinor());
        assertEquals(2, card.ledger().size());
    }

    @Test
    void concurrentTopUpsWithDistinctKeysAreAllApplied() throws Exception {
        SmartCard card = cardWith(0);

        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Callable<Object>> jobs = new ArrayList<>();
            for (int i = 0; i < 64; i++) {
                String key = "tu-" + i;
                jobs.add(() -> service.topUp("C1", 100, key, at(2, 8, 0)));
            }
            for (Future<Object> f : pool.invokeAll(jobs)) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(6_400, card.balanceMinor());
        assertEquals(64, card.ledger().size());
    }
}

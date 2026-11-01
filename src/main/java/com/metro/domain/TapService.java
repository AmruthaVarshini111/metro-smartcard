package com.metro.domain;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Gate logic: top-ups, tap-in and tap-out.
 *
 * <p>Rules:
 * <ul>
 *   <li>Tap-in needs at least the minimum entry balance (after any penalty it triggers).</li>
 *   <li>Tap-in while a trip is still open means the previous tap-out was missed: the old trip
 *       becomes INCOMPLETE and a penalty is charged.</li>
 *   <li>Tap-out with no open trip is charged the penalty.</li>
 *   <li>A normal tap-out charges the fare of the fastest route, capped per day.</li>
 *   <li>Every operation takes a client-supplied idempotency key; a retry returns the
 *       original result and never charges twice.</li>
 * </ul>
 */
public final class TapService {

    private final MetroNetwork network;
    private final RoutePlanner planner;
    private final FareCalculator fares;
    private final FarePolicy policy;
    private final CardRepository cards;

    public TapService(MetroNetwork network, RoutePlanner planner, FareCalculator fares,
                      FarePolicy policy, CardRepository cards) {
        this.network = network;
        this.planner = planner;
        this.fares = fares;
        this.policy = policy;
        this.cards = cards;
    }

    public SmartCard topUp(String cardId, long amountMinor, String key, Instant at) {
        requireKey(key);
        if (amountMinor <= 0) {
            throw new DomainException("INVALID_AMOUNT", "Top-up amount must be positive");
        }
        SmartCard card = cards.require(cardId);
        synchronized (card) {
            card.post(LedgerEntry.Type.TOP_UP, amountMinor, key, at, "Top-up");
            return card;
        }
    }

    public TapResult tapIn(String cardId, String stationId, Instant at, String key) {
        requireKey(key);
        Station station = network.station(stationId);
        SmartCard card = cards.require(cardId);
        synchronized (card) {
            TapResult prior = card.recalledTap(key);
            if (prior != null) {
                return prior;
            }
            Trip open = card.openTrip();
            long penalty = open == null ? 0 : policy.penaltyMinor();
            // Validate before mutating anything, so a rejection leaves no partial state.
            if (card.balanceMinor() - penalty < policy.minimumEntryBalanceMinor()) {
                throw new DomainException("INSUFFICIENT_BALANCE",
                        "Balance too low to enter; please top up");
            }

            String message = "Entry accepted at " + station.name();
            if (open != null) {
                card.post(LedgerEntry.Type.PENALTY, -penalty, key + ":penalty", at,
                        "No tap-out for trip " + open.id());
                card.finishTrip(new Trip(open.id(), open.entryStationId(), null, open.tapIn(), null,
                        penalty, Trip.Status.INCOMPLETE));
                message = "Previous trip had no tap-out; penalty applied. " + message;
            }
            card.startTrip(new Trip(card.nextTripId(), stationId, null, at, null, 0, Trip.Status.OPEN));

            TapResult result = new TapResult(TapResult.Type.IN, stationId, penalty, penalty > 0,
                    card.balanceMinor(), message);
            card.rememberTap(key, result);
            return result;
        }
    }

    public TapResult tapOut(String cardId, String stationId, Instant at, String key) {
        requireKey(key);
        Station station = network.station(stationId);
        SmartCard card = cards.require(cardId);
        synchronized (card) {
            TapResult prior = card.recalledTap(key);
            if (prior != null) {
                return prior;
            }
            Trip open = card.openTrip();
            long charged;
            boolean penaltyApplied;
            String message;

            if (open == null) {
                charged = policy.penaltyMinor();
                penaltyApplied = true;
                card.post(LedgerEntry.Type.PENALTY, -charged, key + ":penalty", at,
                        "Tap-out without tap-in at " + station.name());
                card.recordTrip(new Trip(card.nextTripId(), null, stationId, null, at, charged,
                        Trip.Status.INCOMPLETE));
                message = "No matching tap-in; penalty applied";
            } else {
                if (at.isBefore(open.tapIn())) {
                    throw new DomainException("INVALID_TIME", "Tap-out is earlier than tap-in");
                }
                Route route = planner.plan(open.entryStationId(), stationId, RoutePlanner.Criterion.FASTEST);
                long full = fares.fare(route, open.tapIn());
                LocalDate day = at.atZone(policy.zone()).toLocalDate();
                long remainingCap = Math.max(0, policy.dailyCapMinor() - card.fareSpentOn(day, policy.zone()));
                charged = Math.min(full, remainingCap);
                if (charged > 0) {
                    card.post(LedgerEntry.Type.FARE, -charged, key + ":fare", at,
                            "Trip " + open.id() + ": " + route.origin().name() + " to " + route.destination().name());
                }
                card.finishTrip(new Trip(open.id(), open.entryStationId(), stationId, open.tapIn(), at,
                        charged, Trip.Status.COMPLETED));
                penaltyApplied = false;
                message = charged < full ? "Daily cap reached; fare reduced" : "Fare charged";
            }

            TapResult result = new TapResult(TapResult.Type.OUT, stationId, charged, penaltyApplied,
                    card.balanceMinor(), message);
            card.rememberTap(key, result);
            return result;
        }
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new DomainException("MISSING_IDEMPOTENCY_KEY", "An idempotency key is required");
        }
    }
}

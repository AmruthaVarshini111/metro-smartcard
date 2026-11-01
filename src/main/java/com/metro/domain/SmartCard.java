package com.metro.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A smartcard and its wallet. The balance is derived from an append-only ledger.
 *
 * <p>Every ledger posting carries an idempotency key: posting the same key twice with the
 * same content is a no-op that returns the original entry; reusing a key for a different
 * amount or type is rejected. Public methods are synchronized on the card, and
 * {@link TapService} holds the same monitor across multi-step operations, so a card is
 * never mutated by two threads at once. (With a database this becomes a unique constraint
 * on (card_id, idempotency_key) plus a row lock on the card.)
 */
public final class SmartCard {

    private final String id;
    private final List<LedgerEntry> ledger = new ArrayList<>();
    private final Map<String, LedgerEntry> ledgerByKey = new HashMap<>();
    private final List<Trip> trips = new ArrayList<>();
    private final Map<String, TapResult> tapResults = new HashMap<>();
    private Trip openTrip;
    private long balanceMinor;
    private int tripSequence;

    public SmartCard(String id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public String id() {
        return id;
    }

    public synchronized long balanceMinor() {
        return balanceMinor;
    }

    public synchronized List<LedgerEntry> ledger() {
        return List.copyOf(ledger);
    }

    public synchronized List<Trip> trips() {
        return List.copyOf(trips);
    }

    public synchronized Trip openTrip() {
        return openTrip;
    }

    public synchronized LedgerEntry post(LedgerEntry.Type type, long amountMinor, String key, Instant at, String note) {
        Objects.requireNonNull(key, "idempotency key");
        LedgerEntry existing = ledgerByKey.get(key);
        if (existing != null) {
            if (existing.type() != type || existing.amountMinor() != amountMinor) {
                throw new DomainException("IDEMPOTENCY_KEY_REUSED",
                        "Idempotency key was already used for a different request: " + key);
            }
            return existing;
        }
        LedgerEntry entry = new LedgerEntry(id + "-L" + (ledger.size() + 1), type, amountMinor, key, at, note);
        ledger.add(entry);
        ledgerByKey.put(key, entry);
        balanceMinor += amountMinor;
        return entry;
    }

    /** Total fares (not penalties) charged on the given local calendar day. */
    public synchronized long fareSpentOn(LocalDate day, ZoneId zone) {
        long debited = 0;
        for (LedgerEntry e : ledger) {
            if (e.type() == LedgerEntry.Type.FARE && e.at().atZone(zone).toLocalDate().equals(day)) {
                debited += e.amountMinor();
            }
        }
        return -debited;
    }

    // --- trip lifecycle and tap-result memory, used by TapService (same package) ---

    synchronized String nextTripId() {
        return id + "-T" + (++tripSequence);
    }

    synchronized void startTrip(Trip trip) {
        openTrip = trip;
    }

    /** Closes the open trip (if any) and records the finished or incomplete trip in history. */
    synchronized void finishTrip(Trip finished) {
        trips.add(finished);
        openTrip = null;
    }

    /** Records a trip that never had an open state, e.g. a tap-out with no tap-in. */
    synchronized void recordTrip(Trip trip) {
        trips.add(trip);
    }

    synchronized TapResult recalledTap(String key) {
        return tapResults.get(key);
    }

    synchronized void rememberTap(String key, TapResult result) {
        tapResults.put(key, result);
    }
}

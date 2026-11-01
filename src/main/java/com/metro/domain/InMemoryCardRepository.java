package com.metro.domain;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryCardRepository implements CardRepository {

    private final ConcurrentMap<String, SmartCard> cards = new ConcurrentHashMap<>();

    @Override
    public SmartCard create(String id) {
        SmartCard card = new SmartCard(id);
        if (cards.putIfAbsent(id, card) != null) {
            throw new DomainException("CARD_EXISTS", "Card already exists: " + id);
        }
        return card;
    }

    @Override
    public Optional<SmartCard> find(String id) {
        return Optional.ofNullable(cards.get(id));
    }
}

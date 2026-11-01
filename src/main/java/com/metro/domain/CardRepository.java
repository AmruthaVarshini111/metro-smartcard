package com.metro.domain;

import java.util.Optional;

/** Port for card storage. The in-memory adapter ships first; a JPA adapter can replace it. */
public interface CardRepository {

    SmartCard create(String id);

    Optional<SmartCard> find(String id);

    default SmartCard require(String id) {
        return find(id).orElseThrow(() -> new DomainException("CARD_NOT_FOUND", "Unknown card: " + id));
    }
}

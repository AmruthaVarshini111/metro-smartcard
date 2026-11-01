package com.metro.api;

import com.metro.domain.CardRepository;
import com.metro.domain.FareCalculator;
import com.metro.domain.LedgerEntry;
import com.metro.domain.MetroNetwork;
import com.metro.domain.Route;
import com.metro.domain.RoutePlanner;
import com.metro.domain.SmartCard;
import com.metro.domain.Station;
import com.metro.domain.TapResult;
import com.metro.domain.TapService;
import com.metro.domain.Trip;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin HTTP layer: parse, call the domain, shape the response. Money is in minor units
 * (paise). State-changing endpoints require an {@code Idempotency-Key} header so clients
 * and gates can safely retry.
 */
@RestController
@RequestMapping("/api")
class MetroController {

    record RouteQuote(Route route, long fareMinor) {
    }

    record CardView(String id, long balanceMinor, Trip openTrip, List<Trip> trips, List<LedgerEntry> ledger) {
        static CardView of(SmartCard card) {
            return new CardView(card.id(), card.balanceMinor(), card.openTrip(), card.trips(), card.ledger());
        }
    }

    record TopUpRequest(@Positive long amountMinor) {
    }

    record TapRequest(@NotNull TapResult.Type type, @NotBlank String stationId) {
    }

    private final MetroNetwork network;
    private final RoutePlanner planner;
    private final FareCalculator fares;
    private final TapService taps;
    private final CardRepository cards;
    private final Clock clock;

    MetroController(MetroNetwork network, RoutePlanner planner, FareCalculator fares,
                    TapService taps, CardRepository cards, Clock clock) {
        this.network = network;
        this.planner = planner;
        this.fares = fares;
        this.taps = taps;
        this.cards = cards;
        this.clock = clock;
    }

    @GetMapping("/stations")
    Collection<Station> stations() {
        return network.stations();
    }

    @GetMapping("/route")
    RouteQuote route(@RequestParam String from,
                     @RequestParam String to,
                     @RequestParam(defaultValue = "FASTEST") RoutePlanner.Criterion criterion) {
        Route route = planner.plan(from, to, criterion);
        return new RouteQuote(route, fares.fare(route, clock.instant()));
    }

    @PostMapping("/cards")
    @ResponseStatus(HttpStatus.CREATED)
    CardView createCard() {
        String id = "MC" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        return CardView.of(cards.create(id));
    }

    @GetMapping("/cards/{id}")
    CardView card(@PathVariable String id) {
        return CardView.of(cards.require(id));
    }

    @PostMapping("/cards/{id}/top-ups")
    CardView topUp(@PathVariable String id,
                   @RequestHeader("Idempotency-Key") String key,
                   @Valid @RequestBody TopUpRequest request) {
        return CardView.of(taps.topUp(id, request.amountMinor(), key, clock.instant()));
    }

    @PostMapping("/cards/{id}/taps")
    TapResult tap(@PathVariable String id,
                  @RequestHeader("Idempotency-Key") String key,
                  @Valid @RequestBody TapRequest request) {
        return switch (request.type()) {
            case IN -> taps.tapIn(id, request.stationId(), clock.instant(), key);
            case OUT -> taps.tapOut(id, request.stationId(), clock.instant(), key);
        };
    }
}

package com.metro.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.metro.domain.RoutePlanner.Criterion;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoutePlannerTest {

    private final MetroNetwork network = SampleNetwork.build();
    private final RoutePlanner planner = new RoutePlanner(network);

    private static List<String> ids(Route route) {
        return route.stations().stream().map(Station::id).toList();
    }

    @Test
    void sameLineRouteSumsSegmentDistances() {
        Route route = planner.plan("B01", "B03", Criterion.FASTEST);

        assertEquals(List.of("B01", "B02", "B03"), ids(route));
        assertEquals(2700, route.distanceMeters());
        assertEquals(0, route.interchanges());
    }

    @Test
    void crossLineRouteUsesTheInterchangeAndCountsIt() {
        Route route = planner.plan("B01", "R01", Criterion.FASTEST);

        assertEquals(List.of("B01", "B02", "B03", "R03", "R02", "R01"), ids(route));
        assertEquals(5400, route.distanceMeters()); // interchange walking adds no distance
        assertEquals(1, route.interchanges());
    }

    @Test
    void routeIsSymmetric() {
        Route there = planner.plan("B01", "G06", Criterion.FASTEST);
        Route back = planner.plan("G06", "B01", Criterion.FASTEST);

        assertEquals(there.distanceMeters(), back.distanceMeters());
        assertEquals(there.seconds(), back.seconds());
    }

    @Test
    void sameStationIsAZeroLengthRoute() {
        Route route = planner.plan("B04", "B04", Criterion.FASTEST);

        assertEquals(0, route.distanceMeters());
        assertEquals(1, route.stations().size());
    }

    @Test
    void fewestInterchangesPrefersTheSlowerDirectRide() {
        // O -> M -> D on line L1 takes 2000 s with no interchange.
        // O -> O2 (L2) -> D2 -> D via two interchanges takes only 220 s.
        MetroNetwork n = new MetroNetwork()
                .addStation(new Station("O", "Origin", "L1"))
                .addStation(new Station("M", "Middle", "L1"))
                .addStation(new Station("D", "Dest", "L1"))
                .addStation(new Station("O2", "Origin L2", "L2"))
                .addStation(new Station("D2", "Dest L2", "L2"))
                .connect("O", "M", 1000, 1000)
                .connect("M", "D", 1000, 1000)
                .addInterchange("O", "O2", 60)
                .connect("O2", "D2", 1000, 100)
                .addInterchange("D2", "D", 60);
        RoutePlanner p = new RoutePlanner(n);

        Route fastest = p.plan("O", "D", Criterion.FASTEST);
        Route fewest = p.plan("O", "D", Criterion.FEWEST_INTERCHANGES);

        assertEquals(2, fastest.interchanges());
        assertEquals(220, fastest.seconds());
        assertEquals(0, fewest.interchanges());
        assertEquals(2000, fewest.seconds()); // real totals are reported, not penalised ones
    }

    @Test
    void unknownStationIsRejected() {
        DomainException ex = assertThrows(DomainException.class,
                () -> planner.plan("B01", "NOPE", Criterion.FASTEST));

        assertEquals("UNKNOWN_STATION", ex.code());
    }

    @Test
    void disconnectedStationsHaveNoRoute() {
        MetroNetwork n = new MetroNetwork()
                .addStation(new Station("A", "A", "L1"))
                .addStation(new Station("B", "B", "L2"));

        DomainException ex = assertThrows(DomainException.class,
                () -> new RoutePlanner(n).plan("A", "B", Criterion.FASTEST));

        assertEquals("NO_ROUTE", ex.code());
    }
}

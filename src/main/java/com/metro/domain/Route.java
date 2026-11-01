package com.metro.domain;

import java.util.List;

/** An ordered path through the network with its totals. Distance excludes interchange walking. */
public record Route(List<Station> stations, int distanceMeters, int seconds, int interchanges) {

    public Route {
        stations = List.copyOf(stations);
    }

    public Station origin() {
        return stations.get(0);
    }

    public Station destination() {
        return stations.get(stations.size() - 1);
    }
}

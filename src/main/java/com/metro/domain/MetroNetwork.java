package com.metro.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The metro as an undirected weighted graph. Stations are nodes; track segments and
 * interchange walkways are edges. Interchanges are explicit edges (zero distance, some
 * walking time) so the route planner can count and penalise them.
 */
public final class MetroNetwork {

    public record Edge(String from, String to, int distanceMeters, int seconds, boolean interchange) {
    }

    private final Map<String, Station> stations = new LinkedHashMap<>();
    private final Map<String, List<Edge>> adjacency = new HashMap<>();

    public MetroNetwork addStation(Station station) {
        if (stations.putIfAbsent(station.id(), station) != null) {
            throw new IllegalArgumentException("Duplicate station id: " + station.id());
        }
        adjacency.put(station.id(), new ArrayList<>());
        return this;
    }

    /** Track segment between two stations on the same line. */
    public MetroNetwork connect(String a, String b, int distanceMeters, int seconds) {
        link(a, b, distanceMeters, seconds, false);
        return this;
    }

    /** Walkway between two platforms of the same physical station on different lines. */
    public MetroNetwork addInterchange(String a, String b, int transferSeconds) {
        link(a, b, 0, transferSeconds, true);
        return this;
    }

    private void link(String a, String b, int distanceMeters, int seconds, boolean interchange) {
        station(a);
        station(b);
        if (distanceMeters < 0 || seconds < 0) {
            throw new IllegalArgumentException("distance and time must not be negative");
        }
        adjacency.get(a).add(new Edge(a, b, distanceMeters, seconds, interchange));
        adjacency.get(b).add(new Edge(b, a, distanceMeters, seconds, interchange));
    }

    public Station station(String id) {
        Station s = stations.get(id);
        if (s == null) {
            throw new DomainException("UNKNOWN_STATION", "Unknown station: " + id);
        }
        return s;
    }

    public Collection<Station> stations() {
        return Collections.unmodifiableCollection(stations.values());
    }

    public List<Edge> edgesFrom(String id) {
        station(id);
        return Collections.unmodifiableList(adjacency.get(id));
    }
}

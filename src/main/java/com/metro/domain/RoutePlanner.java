package com.metro.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Dijkstra over the metro graph.
 *
 * <p>FASTEST minimises ride time. FEWEST_INTERCHANGES adds a very large penalty to every
 * interchange edge so that any path with fewer interchanges always wins, and ride time
 * only breaks ties. The reported totals are always the real ones, not the weighted ones.
 */
public final class RoutePlanner {

    public enum Criterion { FASTEST, FEWEST_INTERCHANGES }

    /** Larger than any realistic ride time in seconds, so it dominates the cost. */
    private static final long INTERCHANGE_PENALTY = 1_000_000L;

    private record Entry(String stationId, long cost) implements Comparable<Entry> {
        @Override
        public int compareTo(Entry o) {
            int c = Long.compare(cost, o.cost);
            return c != 0 ? c : stationId.compareTo(o.stationId); // deterministic tie-break
        }
    }

    private final MetroNetwork network;

    public RoutePlanner(MetroNetwork network) {
        this.network = network;
    }

    public Route plan(String fromId, String toId, Criterion criterion) {
        Station from = network.station(fromId);
        network.station(toId);
        if (fromId.equals(toId)) {
            return new Route(List.of(from), 0, 0, 0);
        }

        Map<String, Long> best = new HashMap<>();
        Map<String, MetroNetwork.Edge> cameBy = new HashMap<>();
        PriorityQueue<Entry> queue = new PriorityQueue<>();
        best.put(fromId, 0L);
        queue.add(new Entry(fromId, 0L));

        while (!queue.isEmpty()) {
            Entry current = queue.poll();
            if (current.cost() > best.getOrDefault(current.stationId(), Long.MAX_VALUE)) {
                continue; // stale queue entry
            }
            if (current.stationId().equals(toId)) {
                break;
            }
            for (MetroNetwork.Edge edge : network.edgesFrom(current.stationId())) {
                long next = current.cost() + weight(edge, criterion);
                if (next < best.getOrDefault(edge.to(), Long.MAX_VALUE)) {
                    best.put(edge.to(), next);
                    cameBy.put(edge.to(), edge);
                    queue.add(new Entry(edge.to(), next));
                }
            }
        }

        if (!cameBy.containsKey(toId)) {
            throw new DomainException("NO_ROUTE", "No route from " + fromId + " to " + toId);
        }
        return rebuild(fromId, toId, cameBy);
    }

    private static long weight(MetroNetwork.Edge edge, Criterion criterion) {
        long w = edge.seconds();
        if (criterion == Criterion.FEWEST_INTERCHANGES && edge.interchange()) {
            w += INTERCHANGE_PENALTY;
        }
        return w;
    }

    private Route rebuild(String fromId, String toId, Map<String, MetroNetwork.Edge> cameBy) {
        List<Station> path = new ArrayList<>();
        int distance = 0;
        int seconds = 0;
        int interchanges = 0;
        String at = toId;
        path.add(network.station(at));
        while (!at.equals(fromId)) {
            MetroNetwork.Edge edge = cameBy.get(at);
            distance += edge.distanceMeters();
            seconds += edge.seconds();
            if (edge.interchange()) {
                interchanges++;
            }
            at = edge.from();
            path.add(network.station(at));
        }
        Collections.reverse(path);
        return new Route(path, distance, seconds, interchanges);
    }
}

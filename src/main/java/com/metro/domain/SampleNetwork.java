package com.metro.domain;

/**
 * A small fictional three-line network for demos and tests. Station names are invented
 * on purpose; swap in a real network (e.g. from GTFS data) behind the same MetroNetwork API.
 */
public final class SampleNetwork {

    private SampleNetwork() {
    }

    public static MetroNetwork build() {
        MetroNetwork n = new MetroNetwork();

        line(n, "BLUE", "B",
                new String[] {"Harbour Gate", "Old Bazaar", "Central Square", "University",
                        "Tech Park", "Lakeview", "Airport Road", "Airport"},
                new int[] {1200, 1500, 1100, 1800, 2200, 2600, 3000});

        line(n, "RED", "R",
                new String[] {"North Terminus", "Market Hill", "Central Square", "Stadium",
                        "Riverside", "Garden City", "South Depot"},
                new int[] {1400, 1300, 1600, 2000, 2400, 2800});

        line(n, "GREEN", "G",
                new String[] {"East Mills", "Tech Park", "Hospital", "Garden City", "Orchard", "West End"},
                new int[] {1500, 1700, 1300, 2100, 2500});

        n.addInterchange("B03", "R03", 240); // Central Square
        n.addInterchange("B05", "G02", 240); // Tech Park
        n.addInterchange("R06", "G04", 240); // Garden City
        return n;
    }

    /** Adds stations prefix01..prefixNN and joins neighbours; ~36 km/h average plus 30 s dwell. */
    private static void line(MetroNetwork n, String line, String prefix, String[] names, int[] gapsMeters) {
        for (int i = 0; i < names.length; i++) {
            n.addStation(new Station(id(prefix, i + 1), names[i], line));
        }
        for (int i = 0; i < gapsMeters.length; i++) {
            n.connect(id(prefix, i + 1), id(prefix, i + 2), gapsMeters[i], gapsMeters[i] / 10 + 30);
        }
    }

    private static String id(String prefix, int number) {
        return String.format("%s%02d", prefix, number);
    }
}

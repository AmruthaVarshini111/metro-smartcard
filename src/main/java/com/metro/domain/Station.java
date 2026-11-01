package com.metro.domain;

import java.util.Objects;

/** A stop on one line. A physical interchange is two Station nodes joined by an interchange edge. */
public record Station(String id, String name, String line) {

    public Station {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(line, "line");
    }
}

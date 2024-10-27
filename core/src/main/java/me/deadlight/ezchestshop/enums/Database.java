package me.deadlight.ezchestshop.enums;

import java.util.Objects;

public enum Database {
    SQLITE("SQLite"),
    MYSQL("MySQL");

    private final String name;

    Database(String name) {
        this.name = Objects.requireNonNull(name);
    }

    public String getName() {
        return name;
    }
}

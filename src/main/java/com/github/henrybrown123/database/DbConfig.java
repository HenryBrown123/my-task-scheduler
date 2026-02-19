package com.github.henrybrown123.database;

import com.github.henrybrown123.configuration.AppProperties;

/**
 * Database settings.
 * Immutable record built from application.properties.
 */
public record DbConfig(
        String path,
        boolean inMemory
) {
    public static DbConfig fromProps(AppProperties props) {
        return new DbConfig(
                props.getString("db.path", "scheduler.db"),
                props.getBool("db.in.memory", false)
        );
    }
}
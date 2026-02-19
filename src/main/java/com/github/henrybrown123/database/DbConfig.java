package com.github.henrybrown123.database;

import com.github.henrybrown123.configuration.AppProperties;

/**
 * Database settings.
 * Immutable record built from application.properties.
 */
public record DbProperties(
        String path,
        boolean inMemory
) {
    public static DbProperties fromProps(AppProperties props) {
        return new DbProperties(
                props.getString("db.path", "scheduler.db"),
                props.getBool("db.in.memory", false)
        );
    }
}
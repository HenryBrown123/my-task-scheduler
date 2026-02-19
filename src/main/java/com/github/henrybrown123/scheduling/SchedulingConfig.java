package com.github.henrybrown123.scheduling;

import com.github.henrybrown123.configuration.AppProperties;

/**
 * Scheduling settings.
 * Immutable record built from application.properties.
 */
public record SchedulingProperties(
        String logsDir,
        int poolSize,
        long pollIntervalMs
) {
    public static SchedulingProperties fromProps(AppProperties props) {
        return new SchedulingProperties(
                props.getString("scheduling.logs.dir", "logs"),
                props.getInt("scheduling.pool.size", 4),
                props.getLong("scheduling.poll.interval.ms", 60000)
        );
    }
}
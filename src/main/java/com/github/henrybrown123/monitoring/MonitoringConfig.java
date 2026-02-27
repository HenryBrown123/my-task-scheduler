package com.github.henrybrown123.monitoring;

import com.github.henrybrown123.configuration.AppProperties;

/**
 * Monitoring configuration, loaded from properties.
 *
 * @param intervalSeconds how often to log system health snapshots
 */
public record MonitoringConfig(
        long intervalSeconds
) {
    public static MonitoringConfig fromProps(AppProperties props) {
        return new MonitoringConfig(
                props.getLong("monitoring.interval.seconds", 30)
        );
    }
}

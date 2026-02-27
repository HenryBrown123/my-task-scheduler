package com.github.henrybrown123.scheduling;

import com.github.henrybrown123.configuration.AppProperties;

import java.nio.file.Path;

public record SchedulingConfig(
        Path jobsFile,
        String logsDir,
        int poolSize,
        long pollIntervalMs,
        long maxConcurrentProcesses
) {
    public static SchedulingConfig fromProps(AppProperties props) {
        String jobsPath = props.getString("scheduler.jobs", null);
        return new SchedulingConfig(
                jobsPath != null ? Path.of(jobsPath) : null,
                props.getString("scheduling.logs.dir", "logs"),
                props.getInt("scheduling.pool.size", 4),
                props.getLong("scheduling.poll.interval.ms", 60000),
                props.getLong("scheduling.max.concurrent.processes", 100)
        );
    }
}

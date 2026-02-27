package com.github.henrybrown123.monitoring;

import java.time.LocalDateTime;

/**
 * Immutable snapshot of system health at a point in time.
 * Covers application memory, OS resources, and process info.
 */
public record SystemSnapshot(
        LocalDateTime timestamp,
        MemoryInfo memory,
        CpuInfo cpu,
        ProcessInfo processes,
        ThreadInfo threads,
        SchedulerInfo scheduler
) {

    /**
     * @param appUsedMb          memory actively used by the application
     * @param appMaxMb           maximum memory available to the application
     * @param appUsagePercent    app memory as percentage of max (0-100)
     * @param systemTotalMb      total physical memory on the host
     * @param systemFreeMb       free physical memory on the host
     * @param systemUsagePercent system memory as percentage of total (0-100)
     */
    public record MemoryInfo(
            long appUsedMb,
            long appMaxMb,
            double appUsagePercent,
            long systemTotalMb,
            long systemFreeMb,
            double systemUsagePercent
    ) {}

    /**
     * @param systemLoadPercent   CPU usage across all cores (0-100, -1 if unavailable)
     * @param processLoadPercent  CPU usage of this application (0-100, -1 if unavailable)
     * @param loadAverage         1-minute OS load average (-1 if unavailable)
     * @param availableProcessors number of CPU cores
     */
    public record CpuInfo(
            double systemLoadPercent,
            double processLoadPercent,
            double loadAverage,
            int availableProcessors
    ) {}

    /**
     * @param childProcessCount  processes spawned by this application
     * @param systemProcessCount total processes running on the host
     */
    public record ProcessInfo(
            long childProcessCount,
            long systemProcessCount
    ) {}

    /**
     * @param activeThreads threads currently alive in the application
     * @param peakThreads   highest thread count since startup
     */
    public record ThreadInfo(
            int activeThreads,
            int peakThreads
    ) {}

    /**
     * @param queuedJobs  jobs waiting to be picked up by the thread pool
     * @param runningJobs jobs currently executing as OS processes
     */
    public record SchedulerInfo(
            long queuedJobs,
            long runningJobs
    ) {}

    /**
     * Human-readable summary for logging.
     */
    public String summary() {
        return String.format(
                "memory: %dMB/%dMB (%.0f%%) | sys memory: %dMB/%dMB (%.0f%%) | " +
                "cpu: %.1f%% (sys: %.1f%%, load avg: %.1f) | " +
                "threads: %d (peak: %d) | processes: %d spawned, %d system | " +
                "jobs: %d queued, %d running",
                memory.appUsedMb, memory.appMaxMb, memory.appUsagePercent,
                memory.systemFreeMb, memory.systemTotalMb, memory.systemUsagePercent,
                cpu.processLoadPercent, cpu.systemLoadPercent, cpu.loadAverage,
                threads.activeThreads, threads.peakThreads,
                processes.childProcessCount, processes.systemProcessCount,
                scheduler.queuedJobs, scheduler.runningJobs
        );
    }
}

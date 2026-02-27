package com.github.henrybrown123.monitoring;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SystemSnapshot} and its summary formatting.
 */
class SystemSnapshotTest {

    @Test
    void shouldFormatSummaryWithAllMetrics() {
        var snapshot = createSnapshot(
                new SystemSnapshot.MemoryInfo(45, 256, 17.6, 16384, 4200, 74.4),
                new SystemSnapshot.CpuInfo(34.5, 2.3, 1.8, 4),
                new SystemSnapshot.ProcessInfo(47, 312),
                new SystemSnapshot.ThreadInfo(12, 14),
                new SystemSnapshot.SchedulerInfo(3, 44)
        );

        String summary = snapshot.summary();

        assertTrue(summary.contains("memory: 45MB/256MB (18%)"));
        assertTrue(summary.contains("sys memory: 4200MB/16384MB (74%)"));
        assertTrue(summary.contains("cpu: 2.3%"));
        assertTrue(summary.contains("sys: 34.5%"));
        assertTrue(summary.contains("load avg: 1.8"));
        assertTrue(summary.contains("threads: 12 (peak: 14)"));
        assertTrue(summary.contains("processes: 47 spawned, 312 system"));
        assertTrue(summary.contains("jobs: 3 queued, 44 running"));
    }

    @Test
    void shouldFormatSummaryWithZeroValues() {
        var snapshot = createSnapshot(
                new SystemSnapshot.MemoryInfo(0, 256, 0, 16384, 16384, 0),
                new SystemSnapshot.CpuInfo(0, 0, 0, 4),
                new SystemSnapshot.ProcessInfo(0, 50),
                new SystemSnapshot.ThreadInfo(1, 1),
                new SystemSnapshot.SchedulerInfo(0, 0)
        );

        String summary = snapshot.summary();

        assertTrue(summary.contains("memory: 0MB/256MB (0%)"));
        assertTrue(summary.contains("jobs: 0 queued, 0 running"));
        assertTrue(summary.contains("processes: 0 spawned"));
    }

    @Test
    void shouldFormatSummaryWithUnavailableCpuMetrics() {
        var snapshot = createSnapshot(
                new SystemSnapshot.MemoryInfo(100, 512, 19.5, 8192, 2000, 75.6),
                new SystemSnapshot.CpuInfo(-1, -1, -1, 8),
                new SystemSnapshot.ProcessInfo(10, 200),
                new SystemSnapshot.ThreadInfo(8, 10),
                new SystemSnapshot.SchedulerInfo(0, 5)
        );

        String summary = snapshot.summary();

        assertTrue(summary.contains("cpu: -1.0%"));
        assertTrue(summary.contains("load avg: -1.0"));
    }

    @Test
    void shouldIncludeTimestamp() {
        var snapshot = createSnapshot(
                new SystemSnapshot.MemoryInfo(0, 0, 0, 0, 0, 0),
                new SystemSnapshot.CpuInfo(0, 0, 0, 0),
                new SystemSnapshot.ProcessInfo(0, 0),
                new SystemSnapshot.ThreadInfo(0, 0),
                new SystemSnapshot.SchedulerInfo(0, 0)
        );

        assertNotNull(snapshot.timestamp());
        assertFalse(snapshot.timestamp().isAfter(LocalDateTime.now()));
    }

    private SystemSnapshot createSnapshot(
            SystemSnapshot.MemoryInfo memory,
            SystemSnapshot.CpuInfo cpu,
            SystemSnapshot.ProcessInfo processes,
            SystemSnapshot.ThreadInfo threads,
            SystemSnapshot.SchedulerInfo scheduler
    ) {
        return new SystemSnapshot(LocalDateTime.now(), memory, cpu, processes, threads, scheduler);
    }
}

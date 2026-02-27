package com.github.henrybrown123.monitoring;

import com.github.henrybrown123.repository.sql.ExecutionDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SystemMonitor}.
 * Verifies that snapshots contain sensible values from JVM and OS APIs.
 */
@ExtendWith(MockitoExtension.class)
class SystemMonitorTest {

    @Mock
    private ExecutionDao executionDao;

    private SystemMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new SystemMonitor(executionDao);
    }

    @Test
    void shouldReturnNonNullSnapshot() {
        when(executionDao.getActiveExecutionCounts())
                .thenReturn(new ExecutionDao.ActiveCounts(0, 0));

        SystemSnapshot snapshot = monitor.snapshot();

        assertNotNull(snapshot);
        assertNotNull(snapshot.timestamp());
        assertNotNull(snapshot.memory());
        assertNotNull(snapshot.cpu());
        assertNotNull(snapshot.processes());
        assertNotNull(snapshot.threads());
        assertNotNull(snapshot.scheduler());
    }

    @Test
    void shouldReportPositiveAppMemory() {
        when(executionDao.getActiveExecutionCounts())
                .thenReturn(new ExecutionDao.ActiveCounts(0, 0));

        var memory = monitor.snapshot().memory();

        assertTrue(memory.appUsedMb() >= 0, "Used memory should be non-negative");
        assertTrue(memory.appMaxMb() > 0, "Max memory should be positive");
        assertTrue(memory.appUsedMb() <= memory.appMaxMb(), "Used should not exceed max");
        assertTrue(memory.appUsagePercent() >= 0 && memory.appUsagePercent() <= 100,
                "Usage percent should be 0-100");
    }

    @Test
    void shouldReportPositiveSystemMemory() {
        when(executionDao.getActiveExecutionCounts())
                .thenReturn(new ExecutionDao.ActiveCounts(0, 0));

        var memory = monitor.snapshot().memory();

        assertTrue(memory.systemTotalMb() > 0, "System total memory should be positive");
        assertTrue(memory.systemFreeMb() >= 0, "System free memory should be non-negative");
        assertTrue(memory.systemUsagePercent() >= 0 && memory.systemUsagePercent() <= 100,
                "System usage percent should be 0-100");
    }

    @Test
    void shouldReportCpuCores() {
        when(executionDao.getActiveExecutionCounts())
                .thenReturn(new ExecutionDao.ActiveCounts(0, 0));

        var cpu = monitor.snapshot().cpu();

        assertTrue(cpu.availableProcessors() > 0, "Should have at least 1 CPU core");
    }

    @Test
    void shouldReportCpuLoadOrUnavailable() {
        when(executionDao.getActiveExecutionCounts())
                .thenReturn(new ExecutionDao.ActiveCounts(0, 0));

        var cpu = monitor.snapshot().cpu();

        assertTrue(cpu.systemLoadPercent() == -1
                        || (cpu.systemLoadPercent() >= 0 && cpu.systemLoadPercent() <= 100),
                "System CPU load should be -1 or 0-100");
        assertTrue(cpu.processLoadPercent() == -1
                        || (cpu.processLoadPercent() >= 0 && cpu.processLoadPercent() <= 100),
                "Process CPU load should be -1 or 0-100");
    }

    @Test
    void shouldReportActiveThreads() {
        when(executionDao.getActiveExecutionCounts())
                .thenReturn(new ExecutionDao.ActiveCounts(0, 0));

        var threads = monitor.snapshot().threads();

        assertTrue(threads.activeThreads() > 0, "Should have at least 1 active thread");
        assertTrue(threads.peakThreads() >= threads.activeThreads(),
                "Peak should be >= active");
    }

    @Test
    void shouldReportProcessCounts() {
        when(executionDao.getActiveExecutionCounts())
                .thenReturn(new ExecutionDao.ActiveCounts(0, 0));

        var processes = monitor.snapshot().processes();

        assertTrue(processes.childProcessCount() >= 0, "Child count should be non-negative");
        assertTrue(processes.systemProcessCount() > 0, "System should have at least 1 process");
    }

    @Test
    void shouldReportSchedulerCountsFromDao() {
        when(executionDao.getActiveExecutionCounts())
                .thenReturn(new ExecutionDao.ActiveCounts(5, 12));

        var scheduler = monitor.snapshot().scheduler();

        assertEquals(5, scheduler.queuedJobs());
        assertEquals(12, scheduler.runningJobs());
    }

    @Test
    void shouldReportZeroSchedulerCountsWhenIdle() {
        when(executionDao.getActiveExecutionCounts())
                .thenReturn(new ExecutionDao.ActiveCounts(0, 0));

        var scheduler = monitor.snapshot().scheduler();

        assertEquals(0, scheduler.queuedJobs());
        assertEquals(0, scheduler.runningJobs());
    }

    @Test
    void shouldProduceConsecutiveSnapshots() {
        when(executionDao.getActiveExecutionCounts())
                .thenReturn(new ExecutionDao.ActiveCounts(0, 0));

        SystemSnapshot first = monitor.snapshot();
        SystemSnapshot second = monitor.snapshot();

        assertNotSame(first, second);
        assertFalse(second.timestamp().isBefore(first.timestamp()),
                "Second snapshot should not be before first");
    }
}

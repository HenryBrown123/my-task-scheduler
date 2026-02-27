package com.github.henrybrown123.monitoring;

import com.github.henrybrown123.repository.sql.ExecutionDao;
import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.LocalDateTime;

/**
 * Collects system health metrics from the JVM and OS.
 * Stateless — each call to {@link #snapshot()} reads current values.
 *
 * <p>Uses {@link OperatingSystemMXBean} (available on all standard JDKs)
 * for OS-level metrics, {@link ProcessHandle} for process tracking,
 * and {@link ExecutionDao} for scheduler job counts.
 */
public class SystemMonitor {
    private static final long BYTES_TO_MB = 1_048_576;

    private final OperatingSystemMXBean osMxBean;
    private final ThreadMXBean threadMxBean;
    private final ExecutionDao executionDao;

    public SystemMonitor(ExecutionDao executionDao) {
        this.osMxBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        this.threadMxBean = ManagementFactory.getThreadMXBean();
        this.executionDao = executionDao;
    }

    public SystemSnapshot snapshot() {
        return new SystemSnapshot(
                LocalDateTime.now(),
                collectMemory(),
                collectCpu(),
                collectProcesses(),
                collectThreads(),
                collectScheduler()
        );
    }

    private SystemSnapshot.MemoryInfo collectMemory() {
        Runtime rt = Runtime.getRuntime();

        long appUsed = (rt.totalMemory() - rt.freeMemory()) / BYTES_TO_MB;
        long appMax = rt.maxMemory() / BYTES_TO_MB;
        double appPercent = appMax > 0 ? (appUsed * 100.0) / appMax : 0;

        long sysTotal = osMxBean.getTotalMemorySize() / BYTES_TO_MB;
        long sysFree = osMxBean.getFreeMemorySize() / BYTES_TO_MB;
        double sysPercent = sysTotal > 0
                ? ((sysTotal - sysFree) * 100.0) / sysTotal
                : 0;

        return new SystemSnapshot.MemoryInfo(
                appUsed, appMax, appPercent,
                sysTotal, sysFree, sysPercent
        );
    }

    private SystemSnapshot.CpuInfo collectCpu() {
        double sysLoad = osMxBean.getCpuLoad() * 100;
        double processLoad = osMxBean.getProcessCpuLoad() * 100;
        double loadAverage = osMxBean.getSystemLoadAverage();
        int cores = osMxBean.getAvailableProcessors();

        return new SystemSnapshot.CpuInfo(
                sysLoad >= 0 ? sysLoad : -1,
                processLoad >= 0 ? processLoad : -1,
                loadAverage,
                cores
        );
    }

    private SystemSnapshot.ProcessInfo collectProcesses() {
        long childCount = ProcessHandle.current().children().count();
        long systemCount = ProcessHandle.allProcesses().count();

        return new SystemSnapshot.ProcessInfo(childCount, systemCount);
    }

    private SystemSnapshot.ThreadInfo collectThreads() {
        return new SystemSnapshot.ThreadInfo(
                threadMxBean.getThreadCount(),
                threadMxBean.getPeakThreadCount()
        );
    }

    private SystemSnapshot.SchedulerInfo collectScheduler() {
        var counts = executionDao.getActiveExecutionCounts();
        return new SystemSnapshot.SchedulerInfo(counts.queued(), counts.running());
    }
}

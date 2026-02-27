package com.github.henrybrown123.monitoring;

import com.github.henrybrown123.configuration.AppConfig;
import com.github.henrybrown123.repository.sql.ExecutionDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Infrastructure: system health monitoring.
 *
 * <p>Depends on: {@link ExecutionDao} for scheduler job counts.
 * <p>Logs periodic snapshots on a daemon thread. Provides
 * on-demand snapshots for gRPC or other consumers.
 */
public class MonitoringModule {
    private static final Logger log = LoggerFactory.getLogger(MonitoringModule.class);

    private final SystemMonitor monitor;
    private final long intervalMs;

    public MonitoringModule(ExecutionDao executionDao) {
        this.monitor = new SystemMonitor(executionDao);
        this.intervalMs = AppConfig.monitoring().intervalSeconds() * 1000;
    }

    public void start() {
        Thread monitorThread = new Thread(this::run, "SystemMonitor");
        monitorThread.setDaemon(true);
        monitorThread.start();
        log.info("System monitoring started (interval: {}s)", intervalMs / 1000);
    }

    /**
     * Returns a snapshot of current system health.
     * Can be called from gRPC handlers, tick logging, etc.
     */
    public SystemSnapshot snapshot() {
        return monitor.snapshot();
    }

    private void run() {
        while (true) {
            try {
                SystemSnapshot snap = monitor.snapshot();
                log.info("System health: {}", snap.summary());
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}

package com.github.henrybrown123.scheduling;

import com.github.henrybrown123.configuration.AppConfig;
import com.github.henrybrown123.configuration.JobConfigLoader;
import com.github.henrybrown123.configuration.InvalidJobConfigException;
import com.github.henrybrown123.execution.JobExecutor;
import com.github.henrybrown123.model.JobData;
import com.github.henrybrown123.repository.JobDataRepository;

import java.util.List;

public class SchedulerService {
    private final JobScheduler jobScheduler;
    private final JobDataRepository jobDataRepository;
    private final JobExecutor jobExecutor;
    private final JobConfigLoader jobConfigLoader;
    private final long pollIntervalMs;

    private volatile boolean running = false;
    private Thread schedulerThread;

    public SchedulerService(
            JobScheduler jobScheduler,
            JobDataRepository jobDataRepository,
            JobExecutor jobExecutor,
            JobConfigLoader jobConfigLoader
    ) {
        this.jobScheduler = jobScheduler;
        this.jobDataRepository = jobDataRepository;
        this.jobExecutor = jobExecutor;
        this.jobConfigLoader = jobConfigLoader;
        this.pollIntervalMs = AppConfig.scheduling().pollIntervalMs();
    }

    public void start() {
        running = true;
        schedulerThread = new Thread(this::run, "SchedulerService-Thread");
        schedulerThread.start();
    }

    private void run() {
        while (running) {
            try {
                tick();
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Performs a single scheduling cycle: loads config, syncs to DB,
     * finds due jobs, and submits them for execution.
     *
     * Package-private so integration tests can call this synchronously.
     */
    public void tick() {
        try {
            jobConfigLoader.loadAndSync();
        } catch (InvalidJobConfigException e) {
            System.err.println("Failed to load job config: " + e.getMessage());
            return;
        }

        List<JobData> dueJobs = jobDataRepository.getAll().stream()
                .filter(JobData::isDue)
                .toList();

        dueJobs.forEach(job -> jobScheduler.scheduleJob(
                job.meta().name(),
                () -> jobExecutor.runJob(job),
                job.getNextExecutionTime()
        ));

        if (!dueJobs.isEmpty()) {
            System.out.println("Scheduled " + dueJobs.size() + " due job(s)");
        }
    }

    public void shutdown() {
        running = false;
        if (schedulerThread != null) {
            schedulerThread.interrupt();
        }
        jobScheduler.shutdown();
    }
}
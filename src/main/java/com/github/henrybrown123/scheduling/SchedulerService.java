package com.github.henrybrown123.scheduling;

import com.github.henrybrown123.configuration.AppConfig;
import com.github.henrybrown123.configuration.JobConfigSync;
import com.github.henrybrown123.scheduling.execution.JobExecutor;
import com.github.henrybrown123.model.JobData;
import com.github.henrybrown123.repository.JobDataRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Main scheduling service that polls for due jobs and executes them.
 * Combines job scheduling logic with the executor service.
 */
public class SchedulerService {
    private final ScheduledExecutorService executor;
    private final Map<String, ScheduledFuture<?>> scheduledJobs = new ConcurrentHashMap<>();
    private final Set<String> runningJobs = ConcurrentHashMap.newKeySet();

    private final JobDataRepository jobDataRepository;
    private final JobExecutor jobExecutor;
    private final JobConfigSync configSync;
    private final long pollIntervalMs;

    private volatile boolean running = false;
    private Thread schedulerThread;

    public SchedulerService(
            JobDataRepository jobDataRepository,
            JobExecutor jobExecutor,
            JobConfigSync configSync
    ) {
        this.executor = Executors.newScheduledThreadPool(AppConfig.scheduling().poolSize());
        this.jobDataRepository = jobDataRepository;
        this.jobExecutor = jobExecutor;
        this.configSync = configSync;
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
     * Performs a single scheduling cycle: syncs config if changed,
     * finds due jobs, and submits them for execution.
     */
    public void tick() {
        configSync.syncIfChanged();

        List<JobData> dueJobs = jobDataRepository.getAll().stream()
                .filter(JobData::isDue)
                .toList();

        dueJobs.forEach(job -> scheduleJob(
                job.meta().name(),
                () -> jobExecutor.runJob(job),
                job.getNextExecutionTime()
        ));

        if (!dueJobs.isEmpty()) {
            System.out.println("Scheduled " + dueJobs.size() + " due job(s)");
        }
    }

    private void scheduleJob(String jobId, Runnable job, LocalDateTime when) {
        long delayInSeconds = Duration.between(LocalDateTime.now(), when).getSeconds();

        if (runningJobs.contains(jobId)) {
            System.out.println("[" + jobId + "] Job already running... unable to schedule");
            return;
        }

        ScheduledFuture<?> future = executor.schedule(
                wrappedJob(jobId, job),
                delayInSeconds,
                TimeUnit.SECONDS
        );

        scheduledJobs.put(jobId, future);
    }

    private Runnable wrappedJob(String jobId, Runnable job) {
        return () -> {
            runningJobs.add(jobId);
            try {
                job.run();
            } finally {
                runningJobs.remove(jobId);
            }
        };
    }

    public void shutdown() {
        running = false;
        if (schedulerThread != null) {
            schedulerThread.interrupt();
        }
        executor.shutdown();
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }
}

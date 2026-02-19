package com.github.henrybrown123.scheduling;

import com.github.henrybrown123.configuration.AppConfig;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Bespoke implementation of a scheduler, wrapping ScheduledExecutorService
 */
public class JobScheduler {
    private final ScheduledExecutorService executor;
    private final Map<String, ScheduledFuture<?>> jobs = new ConcurrentHashMap<>();
    private final Set<String> runningJobs = ConcurrentHashMap.newKeySet();

    public JobScheduler() {
        this(AppConfig.scheduling().poolSize());
    }

    JobScheduler(int poolSize) {
        this.executor = Executors.newScheduledThreadPool(poolSize);
    }

    private Runnable wrappedScheduledJob(String jobId, Runnable job) {
        return () -> {
            runningJobs.add(jobId);
            try {
                job.run();
            } finally {
                runningJobs.remove(jobId);
            }
        };
    }

    public void scheduleJob(String jobId, Runnable job, LocalDateTime when) {
        long delayInSeconds = Duration.between(LocalDateTime.now(), when).getSeconds();

        if (runningJobs.contains(jobId)) {
            System.out.println("[" + jobId + "] Job already running... unable to schedule");
            return;
        }

        ScheduledFuture<?> future = executor.schedule(
                wrappedScheduledJob(jobId, job),
                delayInSeconds,
                TimeUnit.SECONDS
        );

        jobs.put(jobId, future);
    }

    public void shutdown() {
        executor.shutdown();
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }
}
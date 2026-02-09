package com.github.henrybrown123.scheduling;

import com.github.henrybrown123.configuration.ConfigLoader;
import com.github.henrybrown123.execution.JobExecutor;
import com.github.henrybrown123.model.JobData;
import com.github.henrybrown123.repository.JobAggregateProvider;
import com.github.henrybrown123.repository.JobRepository;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class SchedulerService {
    private final JobScheduler jobScheduler;
    private final JobAggregateProvider jobAggregateProvider;
    private final JobExecutor jobExecutor;
    private final ConfigLoader jobConfigLoader;

    private volatile boolean running = false;
    private Thread schedulerThread;

    public SchedulerService(
            JobScheduler jobScheduler,
            JobAggregateProvider jobAggregateProvider,
            JobExecutor jobExecutor,
            ConfigLoader jobConfigLoader
    ) {
        this.jobScheduler = jobScheduler;
        this.jobAggregateProvider = jobAggregateProvider;
        this.jobExecutor = jobExecutor;
        this.jobConfigLoader = jobConfigLoader;
    }

    public void start() {
        running = true;
        schedulerThread = new Thread(this::run, "SchedulerService-Thread");
        schedulerThread.start();
    }

    // todo: sort out exception handling here.... way too many try catch... can use result pattern maybe here ?
    // I.e. ConfigLoadResult, JobProviderResult, ScheduleJobResult? ... then i can have a "proper" exception thrown
    // from here ....
    private void run() {
        while (running) {
            try {
                // reads in "fresh" and updates the database with any changes...
                jobConfigLoader.loadAndSync();

                List<JobData> allJobs = jobAggregateProvider.getActiveJobs();

                List<JobData> dueJobs = allJobs.stream()
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

                // todo: implement better logic here ....
                Thread.sleep(5000);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (SQLException e) {
                System.err.println("Database error in scheduler: " + e.getMessage());
                e.printStackTrace();
                try {
                    Thread.sleep(10000); // Wait longer after error
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // todo: implement proper shutdown logic..
    public void shutdown() {
        running = false;
        if (schedulerThread != null) {
            schedulerThread.interrupt();
        }
        jobScheduler.shutdown();
    }
}
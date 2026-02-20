package com.github.henrybrown123;

import com.github.henrybrown123.configuration.JobConfigLoader;
import com.github.henrybrown123.database.Database;
import com.github.henrybrown123.scheduling.execution.JobExecutor;
import com.github.henrybrown123.repository.sql.CredentialDao;
import com.github.henrybrown123.repository.sql.ExecutionDao;
import com.github.henrybrown123.repository.JobDataRepository;
import com.github.henrybrown123.repository.sql.JobDao;
import com.github.henrybrown123.repository.sql.ScheduleDao;
import com.github.henrybrown123.scheduling.JobScheduler;
import com.github.henrybrown123.scheduling.SchedulerService;
import com.github.henrybrown123.security.CredentialService;
import com.github.henrybrown123.security.VaultLifecycle;

import java.nio.file.Path;
import java.sql.Connection;

public class Main {

    public static void main(String[] args) {
        String jobsFile = args.length > 0
                ? args[0]
                : System.getProperty("scheduler.jobs");

        if (jobsFile == null){
            System.err.println("Jobs file must be specified by scheduler.jobs arg");
            System.exit(1);
        }

        Path configPath = Path.of(jobsFile);
        if (!configPath.toFile().exists()) {
            System.err.println("Jobs file not found: " + configPath);
            System.exit(1);
        }

        System.out.println("Running task scheduler");
        System.out.println("Jobs file: " + configPath);

        try {
            // vault + credentials
            VaultLifecycle vaultLifecycle = new VaultLifecycle();
            vaultLifecycle.ensureReady();
            CredentialService credentialService = new CredentialService(vaultLifecycle);

            // database
            Database database = new Database();
            Connection conn = database.getConnection();

            // repositories
            ExecutionDao executionDao = new ExecutionDao(conn);
            JobDao jobDao = new JobDao(conn);
            ScheduleDao scheduleDao = new ScheduleDao(conn);
            CredentialDao credentialDao = new CredentialDao(conn);

            JobDataRepository jobDataRepository = new JobDataRepository(jobDao, scheduleDao, executionDao, credentialDao);

            // wire up dependencies
            JobScheduler scheduler = new JobScheduler();
            JobExecutor jobExecutor = new JobExecutor(executionDao, credentialService);
            JobConfigLoader configLoader = new JobConfigLoader(configPath, jobDataRepository);

            configLoader.loadAndSync();

            // report missing credentials
            var missing = credentialService.scanMissing(jobDataRepository.getAll());
            if (!missing.isEmpty()) {
                System.out.println("\nMissing credentials:");
                for (var m : missing) {
                    System.out.println("  " + m.name() + " (" + m.type() + ") — blocks: " + m.jobIds());
                }
                System.out.println();
            }

            // start
            SchedulerService schedulerService = new SchedulerService(
                    scheduler, jobDataRepository, jobExecutor, configLoader);
            schedulerService.start();

        } catch (Exception e) {
            System.err.println("Fatal: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
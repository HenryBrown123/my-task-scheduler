package com.github.henrybrown123;

import com.github.henrybrown123.configuration.ConfigLoader;
import com.github.henrybrown123.database.Database;
import com.github.henrybrown123.execution.JobExecutor;
import com.github.henrybrown123.repository.ExecutionRepository;
import com.github.henrybrown123.repository.JobAggregateProvider;
import com.github.henrybrown123.repository.JobRepository;
import com.github.henrybrown123.repository.ScheduleRepository;
import com.github.henrybrown123.scheduling.JobScheduler;
import com.github.henrybrown123.scheduling.SchedulerService;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

public class Main {
    static void main() throws URISyntaxException, SQLException {
        System.out.println("Running task scheduler");
        //todo: pool size from config
        JobScheduler scheduler = new JobScheduler(20);

        //todo: get config file from input param or config file
        URI config = Objects.requireNonNull(Main.class.getResource("/jobs.yaml")).toURI();
        Path configPath = Path.of(config);

        // todo: better db connection management...
        Database database = new Database();
        Connection conn = database.getConnection();

        // Repositories
        ExecutionRepository execRepo = new ExecutionRepository(conn);
        JobRepository jobRepo = new JobRepository(conn);
        ScheduleRepository scheduleRepo = new ScheduleRepository(conn);

        // wire up dependencies
        JobExecutor jobExecutor = new JobExecutor(execRepo);
        JobAggregateProvider aggregateProvider = new JobAggregateProvider(jobRepo,scheduleRepo, execRepo);
        ConfigLoader configLoader = new ConfigLoader(configPath,aggregateProvider);


        // create service instances
        SchedulerService schedulerService = new SchedulerService(scheduler,aggregateProvider,jobExecutor, configLoader);

        // start up services
        schedulerService.start();

    }
}



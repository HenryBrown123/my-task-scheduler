package com.github.henrybrown123;

import com.github.henrybrown123.configuration.ConfigLoader;
import com.github.henrybrown123.configuration.JobConfig;
import com.github.henrybrown123.database.Database;
import com.github.henrybrown123.execution.JobExecutor;
import com.github.henrybrown123.model.JobData;
import com.github.henrybrown123.model.job.Status;
import com.github.henrybrown123.model.job.execution.JobExecutionData;
import com.github.henrybrown123.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class JobRunnerTest {

    @Test
    void shouldRunBashCmdJob() throws Exception {
        Path filePath = Paths.get(
                Objects.requireNonNull(getClass().getClassLoader()
                                .getResource("test_jobs.yaml"))
                        .toURI()
        );

        try (Database database = new Database()) {
            Connection conn = database.getConnection();

            ExecutionRepository execRepo = new ExecutionRepository(conn);


//            ConfigLoader configLoader = new ConfigLoader(filePath);
//            List<JobConfig> jobs = configLoader.read();

//            JobExecutor executor = new JobExecutor(execRepo);
//
//            List<JobData> dueJobs = jobs.stream()
//                    .map(job -> new JobData(
//                            job.meta(),
//                            job.command(),
//                            job.schedule(),
//                            new JobExecutionData(Status.ACTIVE, null, null, null, null)
//                    ))
//                    .toList();
//
//            for (JobData job : dueJobs) {
//                executor.runJob(job);
//            }
        }
    }

    @Disabled("Python runner not yet implemented")
    @Test
    void shouldRunPythonJob() throws Exception {
        // TODO: implement when Python support is added
    }
}
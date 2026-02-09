package com.github.henrybrown123.execution;


import com.github.henrybrown123.configuration.ConfigLoader;
import com.github.henrybrown123.configuration.JobConfig;
import com.github.henrybrown123.model.JobData;
import org.junit.jupiter.api.Test;
import org.testng.annotations.Ignore;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;


public class JobExecutorTest
{
    @Test
    void shouldRunBashCmdJob() throws Exception {
//        Path filePath = Paths.get(
//                Objects.requireNonNull(getClass().getClassLoader()
//                                .getResource("test_jobs.yaml"))
//                        .toURI()
//        );
//
//        List<JobConfig> jobs = new ConfigLoader(filePath).read();
//        JobExecutor executor = new JobExecutor();
//
//        jobs.forEach(executor::runJob);

    }

    @Ignore("Python runner not yet implemented")
    @Test
    void shouldRunPythonJob() throws Exception {

    }
}


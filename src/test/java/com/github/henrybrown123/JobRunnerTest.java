package com.github.henrybrown123;

import com.github.henrybrown123.configuration.ConfigLoader;
import com.github.henrybrown123.execution.JobExecutor;
import com.github.henrybrown123.shared.model.JobData;
import org.junit.jupiter.api.Test;
import org.testng.annotations.Ignore;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

public class JobRunnerTest
{
    @Test
    void shouldRunBashCmdJob() throws Exception {
        Path filePath = Paths.get(
                Objects.requireNonNull(getClass().getClassLoader()
                                .getResource("test_jobs.yaml"))
                        .toURI()
        );

        List<JobData> jobs = ConfigLoader.read(filePath);

        JobExecutor jobExecutor = new JobExecutor(jobs);
        jobExecutor.runAllJobs();

    }

    @Ignore("Python runner not yet implemented")
    @Test
    void shouldRunPythonJob() throws Exception {

    }
}

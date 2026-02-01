package com.github.henrybrown123;

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
                                .getResource("health_test.yaml"))
                        .toURI()
        );

        List<JobData> jobs = JobConfigFileReader.read(filePath);

        JobRunner  jobRunner = new JobRunner(jobs);
        jobRunner.runAllJobs();

    }

    @Ignore("Python runner not yet implemented")
    @Test
    void shouldRunPythonJob() throws Exception {

    }
}

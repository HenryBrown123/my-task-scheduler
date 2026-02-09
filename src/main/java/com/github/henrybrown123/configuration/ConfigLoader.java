package com.github.henrybrown123.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.henrybrown123.model.JobData;
import com.github.henrybrown123.repository.JobAggregateProvider;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reads in config file and updates database if any changes to the relevant data.
 */
public class ConfigLoader {
    record JobConfigFile(List<JobConfig> jobs) {}

    private final Path jobConfigPath;
    private final JobAggregateProvider jobAggregateProvider;

    /**
     * ConfigLoader constructor.
     *
     * @param jobConfigPath        Path instance "preloaded" with specified path
     * @param jobAggregateProvider Repository instance for synchronising job data
     */
    public ConfigLoader(Path jobConfigPath, JobAggregateProvider jobAggregateProvider) {
        this.jobConfigPath = jobConfigPath;
        this.jobAggregateProvider = jobAggregateProvider;
    }

    public void loadAndSync() throws IOException, SQLException {
        List<JobConfig> configs = read();
        syncWithDatabase(configs);
    }

    /**
     * Main call to Jackson mapper, deserialising the config YAML file into JobConfig object
     * @return
     * @throws IOException
     */
    public List<JobConfig> read() throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
        JobConfigFile jobsFile = mapper.readValue(jobConfigPath.toFile(), JobConfigFile.class);
        return jobsFile.jobs();
    }

    private void syncWithDatabase(List<JobConfig> configs) throws SQLException {
        List<JobData> existingJobs = jobAggregateProvider.getAllJobs();

        Map<String, JobConfig> existingJobsMap = existingJobs.stream()
                .collect(Collectors.toMap(job -> job.meta().id(), JobConfig::fromJobData));

        for (JobConfig config : configs) {
            JobConfig existingJob = existingJobsMap.get(config.meta().id());

            // save if new job or the config params have changed
            if (existingJob == null || hasChanged(config, existingJob) ) {
                jobAggregateProvider.saveJob(config.toJobData());
            }
        }
    }

    private boolean hasChanged(JobConfig newConfig, JobConfig oldConfig) {

        return !Objects.equals(newConfig.meta(), oldConfig.meta()) ||
                !Objects.equals(newConfig.command(), oldConfig.command()) ||
                !Objects.equals(newConfig.schedule(), oldConfig.schedule());
    }

}
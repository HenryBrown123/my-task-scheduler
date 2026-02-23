package com.github.henrybrown123.configuration;

import com.github.henrybrown123.repository.JobDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Watches the YAML jobs config file and syncs changes to the database.
 * Uses last-modified timestamp to avoid re-parsing on every tick.
 *
 * <p>Call {@link #forceSync()} on startup for the initial load.
 * Call {@link #syncIfChanged()} on each scheduler tick.
 */
public class JobConfigSync {
    private static final Logger log = LoggerFactory.getLogger(JobConfigSync.class);

    private final Path configPath;
    private final JobDataRepository jobDataRepo;
    private final JobConfigLoader loader;
    private long lastModified = 0;

    public JobConfigSync(JobDataRepository jobDataRepo) {
        this.configPath = AppConfig.scheduling().jobsFile();
        this.jobDataRepo = jobDataRepo;
        this.loader = new JobConfigLoader(configPath);
    }

    /**
     * Re-reads and syncs config only if the file has changed since last check.
     * No-op if the file hasn't been touched.
     */
    public void syncIfChanged() {
        long modified = configPath.toFile().lastModified();
        if (modified == lastModified) return;

        lastModified = modified;
        doSync();
    }

    /**
     * Forces a sync regardless of file modification time.
     * Use on startup to guarantee initial load.
     */
    public void forceSync() {
        lastModified = 0;
        syncIfChanged();
    }

    private void doSync() {
        try {
            var configs = loader.read();
            jobDataRepo.sync(configs);
            log.info("Job config synced ({} jobs)", configs.size());
        } catch (InvalidJobConfigException e) {
            log.error("Failed to sync job config: {}", e.getMessage());
        }
    }
}

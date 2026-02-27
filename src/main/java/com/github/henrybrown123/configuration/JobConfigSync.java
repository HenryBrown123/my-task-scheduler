package com.github.henrybrown123.configuration;

import com.github.henrybrown123.repository.JobDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;

/**
 * Watches the YAML jobs config file for changes and syncs to the database.
 * Runs a daemon thread using {@link WatchService} — no polling required.
 *
 * <p>Call {@link #forceSync()} on startup for the initial load.
 * Call {@link #startWatching()} to begin monitoring for changes.
 */
public class JobConfigSync {
    private static final Logger log = LoggerFactory.getLogger(JobConfigSync.class);

    private final Path configPath;
    private final JobDataRepository jobDataRepo;
    private final JobConfigLoader loader;

    public JobConfigSync(JobDataRepository jobDataRepo) {
        this.configPath = AppConfig.scheduling().jobsFile();
        this.jobDataRepo = jobDataRepo;
        this.loader = new JobConfigLoader(configPath);
    }

    public void forceSync() {
        doSync();
    }

    public void startWatching() {
        Thread watchThread = new Thread(this::watch, "ConfigFileWatcher");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    private void watch() {
        try (WatchService watcher = registerWatcher()) {
            log.info("Watching config file: {}", configPath);
            processEvents(watcher);
        } catch (IOException e) {
            log.error("Failed to start config file watcher: {}", e.getMessage());
        }
    }

    private WatchService registerWatcher() throws IOException {
        WatchService watcher = FileSystems.getDefault().newWatchService();
        configPath.getParent().register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
        return watcher;
    }

    private void processEvents(WatchService watcher) {
        while (true) {
            try {
                WatchKey key = watcher.take();

                if (isConfigFileEvent(key)) {
                    log.info("Config file changed, resyncing...");
                    doSync();
                }

                if (!key.reset()) {
                    log.warn("Watch key invalid — directory may have been deleted");
                    break;
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private boolean isConfigFileEvent(WatchKey key) {
        return key.pollEvents().stream()
                .map(event -> (Path) event.context())
                .anyMatch(changed -> changed.equals(configPath.getFileName()));
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

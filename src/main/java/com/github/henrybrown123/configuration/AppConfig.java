package com.github.henrybrown123.configuration;

import com.github.henrybrown123.database.DbConfig;
import com.github.henrybrown123.scheduling.SchedulingConfig;
import com.github.henrybrown123.security.SecurityConfig;

/**
 * Central configuration entry point for the application.
 *
 * <p>Lazily loaded on first access to any feature config. All configuration
 * is read once and stored as immutable feature-specific records. Access
 * feature configs via static methods:
 *
 * <pre>
 *   AppConfig.vault().address()
 *   AppConfig.db().path()
 *   AppConfig.scheduling().poolSize()
 * </pre>
 *
 * <p>Properties are loaded by {@link AppPropertiesLoader} and mapped into feature
 * configs here. Validation errors are accumulated across all features and
 * reported together on first access.
 *
 * <p>Constructor is package-private for testability — tests can construct
 * with custom {@link AppProperties} without hitting the filesystem.
 */
public class AppConfig {

    private static AppConfig config;

    private final SecurityConfig vault;
    private final DbConfig db;
    private final SchedulingConfig scheduling;

    /**
     * Maps the application properties into feature-specific config records.
     *
     * <p>Each feature config is constructed via its {@code fromProps} factory method,
     * which reads the relevant namespaced keys from {@link AppProperties}. Any missing
     * required properties or type parsing errors are accumulated on the AppProperties
     * instance and checked after all configs are built.
     *
     * @param appProperties typed wrapper around the merged properties
     * @throws RuntimeException if any required properties are missing or invalid
     */
    AppConfig(AppProperties appProperties) {
        this.vault = SecurityConfig.fromProps(appProperties);
        this.db = DbConfig.fromProps(appProperties);
        this.scheduling = SchedulingConfig.fromProps(appProperties);

        if (!appProperties.errors().isEmpty()) {
            throw new RuntimeException("Invalid config, failed to parse .properties file(s):\n  "
                    + String.join("\n  ", appProperties.errors()));
        }
    }

    /**
     * Loads configuration on first access. Subsequent calls return the
     * cached instance.
     */
    private static AppConfig get() {
        if (config == null) {
            var appProperties = AppPropertiesLoader.load();
            config = new AppConfig(appProperties);
        }
        return config;
    }

    /** @return Vault connection and lifecycle settings. */
    public static SecurityConfig vault() { return get().vault; }

    /** @return database connection settings. */
    public static DbConfig db() { return get().db; }

    /** @return job scheduling settings (pool size, poll interval, logs directory). */
    public static SchedulingConfig scheduling() { return get().scheduling; }
}
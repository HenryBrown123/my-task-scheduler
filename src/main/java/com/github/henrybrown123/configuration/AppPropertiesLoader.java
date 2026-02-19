package com.github.henrybrown123.configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Static factory that loads configuration from property sources and returns
 * an {@link AppProperties} wrapper for type-safe access.
 *
 * <p><b>Loading order:</b>
 * <ol>
 *   <li>Classpath defaults from {@code app.default.properties} (bundled in the jar)</li>
 *   <li>Optional user overrides from {@code -Dapp.config=/path/to/overrides.properties}</li>
 * </ol>
 *
 * <p>User overrides are layered on top — duplicate keys take the user-specified value.
 */
class AppPropertiesLoader {

    private static final String DEFAULT_PROPS = "app.default.properties";
    private static final String USER_CONFIG_ARG = "app.config";

    /**
     * Loads and merges all property sources into an {@link AppProperties} instance.
     *
     * @return typed wrapper around the merged properties
     * @throws RuntimeException if app.default.properties is not found on the classpath
     */
    static AppProperties load() {
        var props = new Properties();
        loadDefaults(props);
        loadUserOverrides(props);
        return new AppProperties(props);
    }

    /**
     * Loads mandatory default properties bundled on the classpath (inside the jar).
     *
     * @param props the Properties instance to load into
     * @throws RuntimeException if app.default.properties is not found on the classpath
     */
    private static void loadDefaults(Properties props) {
        try (InputStream is = AppPropertiesLoader.class.getClassLoader()
                .getResourceAsStream(DEFAULT_PROPS)) {
            if (is == null) {
                throw new RuntimeException(DEFAULT_PROPS + " not found on classpath");
            }
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + DEFAULT_PROPS, e);
        }
    }

    /**
     * Loads optional user overrides from an external file.
     *
     * <p>The file path is specified via the {@code -Dapp.config} system property.
     * If the property is not set or the file does not exist, this method is a no-op.
     *
     * @param props the Properties instance to load overrides into
     * @throws RuntimeException if the specified file exists but cannot be read
     */
    private static void loadUserOverrides(Properties props) {
        String configFile = System.getProperty(USER_CONFIG_ARG);
        if (configFile == null) return;

        Path overrides = Path.of(configFile);
        if (!Files.exists(overrides)) return;

        try (InputStream is = Files.newInputStream(overrides)) {
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config: " + overrides, e);
        }
    }
}
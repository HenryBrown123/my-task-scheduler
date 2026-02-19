package com.github.henrybrown123.configuration;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigLoaderTest {

    private String testResource(String name) throws URISyntaxException {
        return Path.of(getClass().getClassLoader().getResource("test-user-config/" + name).toURI()).toString();
    }

    @Test
    void shouldReturnTestDefaultValues() {
        AppProperties props = AppPropertiesLoader.load();

        assertEquals("target/test-logs", props.getString("scheduling.logs.dir", null));
        assertEquals(":memory:", props.getString("db.path", null));
        assertEquals(4, props.getInt("scheduling.pool.size", -1));
        assertEquals(5000, props.getLong("scheduling.poll.interval.ms", -1));
        assertTrue(props.errors().isEmpty());
    }

    @Test
    void shouldOverrideDefaultsWithUserProps() throws Exception {
        System.setProperty("app.config", testResource("app.valid.properties"));
        try {
            AppProperties props = AppPropertiesLoader.load();

            assertEquals("/var/custom/logs", props.getString("scheduling.logs.dir", null));
            assertEquals(":memory:", props.getString("db.path", null));
            // non-overridden values fall through to defaults
            assertEquals(2, props.getInt("scheduling.pool.size", -1));
            assertEquals(5000, props.getLong("scheduling.poll.interval.ms", -1));
            assertTrue(props.errors().isEmpty());
        } finally {
            System.clearProperty("app.config");
        }
    }

    @Test
    void shouldIgnoreNullConfigPath() {
        System.clearProperty("app.config");

        AppProperties props = AppPropertiesLoader.load();
        assertNotNull(props.getString("scheduling.logs.dir", null));
    }

    @Test
    void shouldIgnoreNonExistentConfigPath() {
        System.setProperty("app.config", "/does/not/exist/app.valid.properties");
        try {
            AppProperties props = AppPropertiesLoader.load();
            assertNotNull(props.getString("scheduling.logs.dir", null));
        } finally {
            System.clearProperty("app.config");
        }
    }

    @Test
    void shouldReportAllInvalidTypes() throws Exception {
        System.setProperty("app.config", testResource("app.invalid.properties"));
        try {
            AppProperties props = AppPropertiesLoader.load();
            // force parsing of invalid values
            props.getInt("scheduling.pool.size", 0);
            props.getLong("scheduling.poll.interval.ms", 0);

            assertFalse(props.errors().isEmpty());
            assertTrue(props.errors().stream().anyMatch(e -> e.contains("scheduling.pool.size")));
            assertTrue(props.errors().stream().anyMatch(e -> e.contains("scheduling.poll.interval.ms")));
        } finally {
            System.clearProperty("app.config");
        }
    }
}
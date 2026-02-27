package com.github.henrybrown123.monitoring;

import com.github.henrybrown123.configuration.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MonitoringConfig} properties mapping.
 */
class MonitoringConfigTest {

    @Test
    void shouldReadIntervalFromProperties() {
        Properties props = new Properties();
        props.setProperty("monitoring.interval.seconds", "60");

        MonitoringConfig config = MonitoringConfig.fromProps(new AppProperties(props));

        assertEquals(60, config.intervalSeconds());
    }

    @Test
    void shouldUseDefaultIntervalWhenNotSet() {
        Properties props = new Properties();

        MonitoringConfig config = MonitoringConfig.fromProps(new AppProperties(props));

        assertEquals(30, config.intervalSeconds());
    }
}

package com.github.henrybrown123.configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Typed wrapper around the application Properties instance.
 * Provides type-safe accessors with error accumulation for validation.
 */
public class AppProperties {

    private final Properties props;
    private final List<String> errors = new ArrayList<>();

    public AppProperties(Properties props) {
        this.props = props;
    }

    public String getString(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String value = props.getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            errors.add(key + " must be an integer, got: " + value);
            return defaultValue;
        }
    }

    public long getLong(String key, long defaultValue) {
        String value = props.getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            errors.add(key + " must be a long, got: " + value);
            return defaultValue;
        }
    }

    public boolean getBool(String key, boolean defaultValue) {
        String value = props.getProperty(key);
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(value);
    }

    public List<String> errors() {
        return errors;
    }
}
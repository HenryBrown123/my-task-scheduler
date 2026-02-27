package com.github.henrybrown123.security;

import com.github.henrybrown123.configuration.AppProperties;

/**
 * Vault connection settings.
 * Immutable record built from application.properties.
 */
public record SecurityConfig(
        String address,
        String secretPath
) {
    public static SecurityConfig fromProps(AppProperties props) {
        return new SecurityConfig(
                props.getString("vault.address", "http://127.0.0.1:8200"),
                props.getString("vault.secret.path", "secret/my-scheduler")
        );
    }
}

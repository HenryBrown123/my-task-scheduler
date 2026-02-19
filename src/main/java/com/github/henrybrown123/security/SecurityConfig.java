package com.github.henrybrown123.security;

import com.github.henrybrown123.configuration.AppProperties;

/**
 * Vault connection and lifecycle settings.
 * Immutable record built from application.properties.
 */
public record SecurityConfig(
        String address,
        String secretPath,
        boolean managed,
        String dataDir,
        String listenerAddress,
        boolean tlsDisable,
        boolean uiEnabled,
        boolean disableMlock
) {
    public static SecurityConfig fromProps(AppProperties props) {
        return new SecurityConfig(
                props.getString("vault.address", "http://127.0.0.1:8200"),
                props.getString("vault.secret.path", "secret/my-scheduler"),
                props.getBool("vault.managed", true),
                props.getString("vault.data.dir",
                        System.getProperty("user.home") + "/.my-scheduler/vault-data"),
                props.getString("vault.listener.address", "127.0.0.1:8200"),
                props.getBool("vault.tls.disable", true),
                props.getBool("vault.ui", true),
                props.getBool("vault.disable.mlock", true)
        );
    }
}
package com.github.henrybrown123.security;

import java.util.Set;

/**
 * Supported credential types with their expected field names.
 * Each type defines the fields that must be provided when storing
 * a credential of that type in Vault.
 */
public enum ESecretType {
    SMTP("host", "port", "username", "password"),
    DATABASE("host", "port", "username", "password", "database"),
    SSH("host", "port", "username", "private_key"),
    API_KEY("name", "token"),
    CUSTOM();

    public static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "token", "secret", "key", "private_key"
    );

    public final String[] fields;

    ESecretType(String... fields) {
        this.fields = fields;
    }

    public boolean isSensitive(String field) {
        return SENSITIVE_FIELDS.contains(field.toLowerCase());
    }
}
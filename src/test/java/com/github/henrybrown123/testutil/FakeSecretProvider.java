package com.github.henrybrown123.testutil;

import com.github.henrybrown123.security.SecretProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory fake secret provider for tests.
 * Stores credentials in memory without requiring Vault.
 */
public class FakeSecretProvider implements SecretProvider {
    private final Map<String, Map<String, String>> secrets = new HashMap<>();

    public void store(String name, Map<String, String> fields) {
        secrets.put(name, new HashMap<>(fields));
    }

    @Override
    public Optional<Map<String, String>> read(String name) {
        return Optional.ofNullable(secrets.get(name));
    }

    @Override
    public void write(String name, Map<String, Object> fields) {
        Map<String, String> stringFields = new HashMap<>();
        fields.forEach((k, v) -> stringFields.put(k, v.toString()));
        secrets.put(name, stringFields);
    }

    @Override
    public void delete(String name) {
        secrets.remove(name);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}

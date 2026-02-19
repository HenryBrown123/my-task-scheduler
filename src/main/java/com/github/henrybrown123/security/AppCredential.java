package com.github.henrybrown123.security;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A resolved credential with actual field values from Vault.
 * Immutable — created by CredentialService when resolving
 * a job's credential requirements at execution time.
 *
 * <p>All field values are stored internally as {@code SecretString} to prevent
 * accidental exposure via logging, toString(), or stack traces. The public API
 * deals only in plain strings via {@link #fromRawFields} and {@link #exposeFields}.
 */
public record AppCredential(
        String name,
        ESecretType type,
        Map<String, SecretString> fields
) {

    /**
     * Creates an AppCredential from raw string fields, wrapping each
     * value in SecretString. Use this when reading from Vault.
     * note: LinkedHashMap used here to preserve insertion order of the credential parameters
     */
    public static AppCredential fromRawFields(String name, ESecretType type, Map<String, String> rawFields) {
        var secure = new LinkedHashMap<String, SecretString>();
        rawFields.forEach((key, value) -> secure.put(key, new SecretString(value)));
        return new AppCredential(name, type, secure);
    }

    public Map<String, String> exposeFields() {
        var raw = new LinkedHashMap<String, String>();
        fields.forEach((key, value) -> raw.put(key, value.expose()));
        return raw;
    }

    /**
     * Wrapper around sensitive string values that prevents accidental
     * exposure via toString(), logging, or stack traces.
     */
    static final class SecretString {

        private final String value;

        SecretString(String value) {
            this.value = value;
        }

        String expose() {
            return value;
        }

        @Override
        public String toString() {
            return "********";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SecretString s)) return false;
            return value.equals(s.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
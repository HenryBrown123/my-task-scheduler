package com.github.henrybrown123.security;

import java.util.Map;
import java.util.Optional;

/**
 * Contract for reading, writing, and deleting secrets.
 * Implementations may be backed by Vault, AWS Secrets Manager,
 * or an in-memory store for testing.
 *
 * <p>Callers should check {@link #isAvailable()} before performing
 * operations. If the store is not available, jobs requiring
 * credentials are skipped rather than failing the application.
 *
 * <p>Methods throw {@link SecretStoreException} when an operation
 * fails unexpectedly. A missing secret is not exceptional —
 * {@link #read(String)} returns {@link Optional#empty()}.
 */
public interface SecretProvider {

    /**
     * Whether the secret store is connected and usable.
     * If false, the store was not reachable at startup —
     * callers should skip credential-dependent work.
     */
    boolean isAvailable();

    /**
     * Read a secret by name.
     *
     * @param name the credential name
     * @return the fields if present, empty if the secret does not exist
     * @throws SecretStoreException if the store cannot be reached
     */
    Optional<Map<String, String>> read(String name);

    /**
     * Write or overwrite a secret.
     *
     * @param name   the credential name
     * @param fields the key-value pairs to store
     * @throws SecretStoreException if the operation fails
     */
    void write(String name, Map<String, Object> fields);

    /**
     * Delete a secret.
     *
     * @param name the credential name
     * @throws SecretStoreException if the operation fails
     */
    void delete(String name);
}

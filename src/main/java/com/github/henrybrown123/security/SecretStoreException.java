package com.github.henrybrown123.security;

/**
 * Thrown when the secret store is unreachable or an operation fails
 * unexpectedly. Normal "not found" results are handled via
 * {@link java.util.Optional} in control flow — this exception is
 * reserved for genuinely exceptional situations.
 */
public class SecretStoreException extends RuntimeException {

    public SecretStoreException(String message) {
        super(message);
    }

    public SecretStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.github.henrybrown123.security;

import java.util.Map;

/**
 * A resolved credential with actual field values from Vault.
 * Immutable — created by CredentialService when resolving
 * a job's credential requirements at execution time.
 */
public record Credential(
        String name,
        ECredentialType type,
        Map<String, String> fields
) {}
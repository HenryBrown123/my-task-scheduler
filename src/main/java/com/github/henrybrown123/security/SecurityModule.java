package com.github.henrybrown123.security;

import com.github.henrybrown123.configuration.AppConfig;

/**
 * Infrastructure: secrets and credential management.
 *
 * <p>Requires: {@link AppConfig} (Vault configuration).
 * <p>Owns: VaultLifecycle, CredentialService.
 */
public class SecurityModule {
    private final CredentialService credentialService;

    public SecurityModule() throws Exception {
        var vault = new VaultLifecycle();
        vault.ensureReady();
        this.credentialService = new CredentialService(vault);
    }

    public CredentialService credentialService() {
        return credentialService;
    }
}

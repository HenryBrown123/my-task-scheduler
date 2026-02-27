package com.github.henrybrown123.security;

import com.github.henrybrown123.repository.PersistenceModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Infrastructure: secrets and credential management.
 *
 * <p>Depends on: {@link PersistenceModule} (for credential verification at startup).
 * <p>Exposes: CredentialService.
 *
 * <p>If Vault is not reachable at startup, the module initialises in a
 * degraded state — jobs requiring credentials will be skipped until
 * Vault becomes available.
 */
public class SecurityModule {
    private static final Logger log = LoggerFactory.getLogger(SecurityModule.class);

    private final CredentialService credentialService;

    public SecurityModule(PersistenceModule persistence) {
        var vault = new VaultManager();
        this.credentialService = new CredentialService(vault);

        if (vault.isAvailable()) {
            verifyCredentials(persistence);
        } else {
            log.warn("Vault is not available — jobs requiring credentials will be skipped");
        }
    }

    private void verifyCredentials(PersistenceModule persistence) {
        var blocked = credentialService.findJobsMissingCredentials(
                persistence.jobDataRepo().getAll());
        if (blocked.isEmpty()) return;

        blocked.forEach(job ->
                log.warn("Job {} has missing credentials — will be skipped", job.meta().id()));
    }

    public CredentialService credentialService() {
        return credentialService;
    }

    @Override
    public String toString() {
        return "SecurityModule[credentialService=***]";
    }
}

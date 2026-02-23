package com.github.henrybrown123.security;

import com.github.henrybrown123.model.JobData;
import com.github.henrybrown123.model.job.command.JobCredential;

import java.util.*;

/**
 * Manages credential lifecycle and checks job readiness against Vault.
 * Used by the gRPC layer for credential CRUD and by the scheduler
 * to check job readiness before execution.
 *
 * <p>When Vault is unavailable, callers are expected to route around
 * credential logic entirely rather than calling into this service.
 */
public class CredentialService {

    private final SecretProvider vault;

    public CredentialService(SecretProvider vault) {
        this.vault = vault;
    }

    /**
     * Returns jobs that require credentials but are missing at least one.
     */
    public List<JobData> findJobsMissingCredentials(List<JobData> jobs) {
        return jobs.stream()
                .filter(job -> !job.command().credentials().isEmpty())
                .filter(job -> !jobIsReady(job))
                .toList();
    }

    /**
     * Checks whether a specific job has all its required credentials in Vault.
     */
    public boolean jobIsReady(JobData job) {
        if (job.command().credentials().isEmpty()) {
            return true;
        }
        return job.command().credentials().stream()
                .allMatch(req -> vault.read(req.name()).isPresent());
    }

    /**
     * Resolves all credentials for a job from Vault.
     *
     * @param requiredCredentials the credential requirements from the job config
     * @return map of credential name to resolved Credential
     * @throws SecretStoreException if a required credential is missing
     */
    public Map<String, AppCredential> resolveForJob(List<JobCredential> requiredCredentials) {
        Map<String, AppCredential> resolved = new LinkedHashMap<>();
        requiredCredentials.forEach(cred -> resolved.put(cred.name(), resolve(cred)));
        return resolved;
    }

    private AppCredential resolve(JobCredential cred) {
        Map<String, String> fields = vault.read(cred.name())
                .orElseThrow(() -> new SecretStoreException(
                        "Missing credential: " + cred.name() + " (" + cred.type() + ")"));
        return AppCredential.fromRawFields(cred.name(), cred.type(), fields);
    }

    /**
     * Stores a credential in Vault.
     */
    public void store(String name, ESecretType type, Map<String, String> fields) {
        vault.write(name, new LinkedHashMap<>(fields));
    }

    /**
     * Retrieves a credential's fields from Vault.
     */
    public Optional<Map<String, String>> get(String name) {
        return vault.read(name);
    }

    /**
     * Deletes a credential from Vault.
     */
    public void delete(String name) {
        vault.delete(name);
    }
}

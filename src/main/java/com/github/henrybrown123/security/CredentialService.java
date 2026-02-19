package com.github.henrybrown123.security;



import com.github.henrybrown123.model.JobData;
import com.github.henrybrown123.model.job.command.JobCredential;

import java.util.*;

/**
 * Scans job configs against Vault and manages credential lifecycle.
 * Used by the gRPC layer for credential CRUD and by the scheduler
 * to check job readiness before execution.
 */
public class SecretService {

    /**
     * Status of a single credential across all jobs that require it.
     */
    public record CredentialStatus(
            String name,
            SecretType type,
            boolean present,
            List<String> jobIds
    ) {}

    private final VaultLifecycle vault;

    public SecretService(VaultLifecycle vault) {
        this.vault = vault;
    }

    /**
     * Scans all jobs and returns the status of every required credential.
     *
     * @param jobs the full list of configured jobs
     * @return status of each unique credential requirement
     */
    public List<CredentialStatus> scan(List<JobData> jobs) {
        Map<String, JobCredential> requirements = new LinkedHashMap<>();
        Map<String, List<String>> credToJobs = new LinkedHashMap<>();

        for (JobData job : jobs) {
            if (job.command().credentials() == null) continue;
            for (JobCredential req : job.command().credentials()) {
                requirements.putIfAbsent(req.name(), req);
                credToJobs
                        .computeIfAbsent(req.name(), k -> new ArrayList<>())
                        .add(job.meta().id());
            }
        }

        List<CredentialStatus> results = new ArrayList<>();
        for (var entry : requirements.entrySet()) {
            boolean present = vault.read(entry.getKey()).isPresent();
            results.add(new CredentialStatus(
                    entry.getKey(),
                    entry.getValue().type(),
                    present,
                    credToJobs.getOrDefault(entry.getKey(), List.of())
            ));
        }
        return results;
    }

    /**
     * Returns only credentials that are required but missing from Vault.
     *
     * @param jobs the full list of configured jobs
     * @return missing credential statuses
     */
    public List<CredentialStatus> scanMissing(List<JobData> jobs) {
        return scan(jobs).stream()
                .filter(s -> !s.present())
                .toList();
    }

    /**
     * Checks whether a specific job has all its required credentials in Vault.
     *
     * @param job the job to check
     * @return true if all credentials are present or the job has no requirements
     */
    public boolean jobIsReady(JobData job) {
        if (job.command().credentials() == null || job.command().credentials().isEmpty()) {
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
     * @throws RuntimeException if any required credential is missing
     */
    public Map<String, AppCredential> resolveForJob(List<JobCredential> requiredCredentials) {
        if (requiredCredentials == null || requiredCredentials.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, AppCredential> resolved = new LinkedHashMap<>();
        for (JobCredential cred : requiredCredentials) {
            Map<String, String> fields = vault.read(cred.name())
                    .orElseThrow(() -> new RuntimeException(
                            "Missing credential: " + cred.name() + " (" + cred.type() + ")"));

            resolved.put(cred.name(), AppCredential.fromRawFields(cred.name(), cred.type(), fields));
        }
        return resolved;
    }

    /**
     * Stores a credential in Vault.
     *
     * @param name the credential name
     * @param type the credential type
     * @param fields the key-value pairs to store
     */
    public void store(String name, SecretType type, Map<String, String> fields) {
        vault.write(name, new LinkedHashMap<>(fields));
    }

    /**
     * Retrieves a credential's fields from Vault.
     *
     * @param name the credential name
     * @return the fields if present, empty otherwise
     */
    public Optional<Map<String, String>> get(String name) {
        return vault.read(name);
    }

    /**
     * Deletes a credential from Vault.
     *
     * @param name the credential name
     */
    public void delete(String name) {
        vault.delete(name);
    }

    /**
     * Checks if the underlying Vault connection is healthy.
     *
     * @return true if Vault is reachable
     */
    public boolean isVaultHealthy() {
        return vault.isHealthy();
    }
}
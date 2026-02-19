package com.github.henrybrown123.security;

import com.github.henrybrown123.model.JobData;
import com.github.henrybrown123.model.job.Interpreter;
import com.github.henrybrown123.model.job.JobMeta;
import com.github.henrybrown123.model.job.command.JobCommandData;
import com.github.henrybrown123.model.job.command.JobCredential;
import com.github.henrybrown123.model.job.execution.ExecutionType;
import com.github.henrybrown123.model.job.schedule.SimpleSchedule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CredentialServiceTest {

    @Mock
    private VaultLifecycle vault;

    @InjectMocks
    private CredentialService credentialService;

    @Test
    void scanShouldReturnPresentCredential() {
        when(vault.read("smtp")).thenReturn(Optional.of(Map.of("host", "smtp.gmail.com")));

        var results = credentialService.scan(List.of(
                jobWith("job-1", cred("smtp", ESecretType.SMTP))
        ));

        assertEquals(1, results.size());
        assertEquals("smtp", results.get(0).name());
        assertTrue(results.get(0).present());
        assertEquals(List.of("job-1"), results.get(0).jobIds());
    }

    @Test
    void scanShouldReturnMissingCredential() {
        when(vault.read("smtp")).thenReturn(Optional.empty());

        var results = credentialService.scan(List.of(
                jobWith("job-1", cred("smtp", ESecretType.SMTP))
        ));

        assertEquals(1, results.size());
        assertFalse(results.get(0).present());
    }

    @Test
    void scanShouldGroupMultipleJobsPerCredential() {
        when(vault.read("smtp")).thenReturn(Optional.of(Map.of("host", "smtp.gmail.com")));

        var results = credentialService.scan(List.of(
                jobWith("job-1", cred("smtp", ESecretType.SMTP)),
                jobWith("job-2", cred("smtp", ESecretType.SMTP))
        ));

        assertEquals(1, results.size());
        assertEquals(List.of("job-1", "job-2"), results.get(0).jobIds());
    }

    @Test
    void scanMissingShouldFilterToMissingOnly() {
        when(vault.read("smtp")).thenReturn(Optional.of(Map.of("host", "smtp.gmail.com")));
        when(vault.read("db")).thenReturn(Optional.empty());

        var missing = credentialService.scanMissing(List.of(
                jobWith("job-1",
                        cred("smtp", ESecretType.SMTP),
                        cred("db", ESecretType.DATABASE))
        ));

        assertEquals(1, missing.size());
        assertEquals("db", missing.get(0).name());
    }

    @Test
    void scanShouldHandleJobsWithNoCredentials() {
        var results = credentialService.scan(List.of(jobWith("job-1")));

        assertTrue(results.isEmpty());
        verify(vault, never()).read(anyString());
    }

    @Test
    void jobIsReadyShouldReturnTrueWhenAllPresent() {
        when(vault.read("smtp")).thenReturn(Optional.of(Map.of("host", "smtp.gmail.com")));

        assertTrue(credentialService.jobIsReady(
                jobWith("job-1", cred("smtp", ESecretType.SMTP))));
    }

    @Test
    void jobIsReadyShouldReturnFalseWhenMissing() {
        when(vault.read("smtp")).thenReturn(Optional.empty());

        assertFalse(credentialService.jobIsReady(
                jobWith("job-1", cred("smtp", ESecretType.SMTP))));
    }

    @Test
    void jobIsReadyShouldReturnTrueWhenNoCredentialsRequired() {
        assertTrue(credentialService.jobIsReady(jobWith("job-1")));
        verify(vault, never()).read(anyString());
    }

    @Test
    void resolveForJobShouldReturnAppCredentials() {
        when(vault.read("smtp")).thenReturn(Optional.of(Map.of(
                "host", "smtp.gmail.com",
                "port", "587",
                "username", "test@gmail.com",
                "password", "secret"
        )));

        var resolved = credentialService.resolveForJob(
                List.of(cred("smtp", ESecretType.SMTP)));

        assertEquals(1, resolved.size());
        assertTrue(resolved.containsKey("smtp"));

        var appCred = resolved.get("smtp");
        assertEquals("smtp", appCred.name());
        assertEquals(ESecretType.SMTP, appCred.type());

        var fields = appCred.exposeFields();
        assertEquals("smtp.gmail.com", fields.get("host"));
        assertEquals("secret", fields.get("password"));
    }

    @Test
    void resolveForJobShouldThrowWhenMissing() {
        when(vault.read("smtp")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () ->
                credentialService.resolveForJob(
                        List.of(cred("smtp", ESecretType.SMTP))));
    }

    @Test
    void resolveForJobShouldReturnEmptyForNullRequirements() {
        assertTrue(credentialService.resolveForJob(null).isEmpty());
        verify(vault, never()).read(anyString());
    }

    @Test
    void resolveForJobShouldReturnEmptyForEmptyRequirements() {
        assertTrue(credentialService.resolveForJob(List.of()).isEmpty());
        verify(vault, never()).read(anyString());
    }

    @Test
    void storeShouldWriteToVault() {
        credentialService.store("smtp", ESecretType.SMTP,
                Map.of("host", "smtp.gmail.com", "password", "secret"));

        verify(vault).write(eq("smtp"), anyMap());
    }

    @Test
    void getShouldDelegateToVault() {
        when(vault.read("smtp")).thenReturn(Optional.of(Map.of("host", "smtp.gmail.com")));

        var result = credentialService.get("smtp");

        assertTrue(result.isPresent());
        assertEquals("smtp.gmail.com", result.get().get("host"));
    }

    @Test
    void deleteShouldDelegateToVault() {
        credentialService.delete("smtp");
        verify(vault).delete("smtp");
    }

    @Test
    void isVaultHealthyShouldDelegateToVault() {
        when(vault.isHealthy()).thenReturn(true);
        assertTrue(credentialService.isVaultHealthy());
    }

    private JobCredential cred(String name, ESecretType type) {
        return new JobCredential(name, type);
    }

    private JobData jobWith(String id, JobCredential... creds) {
        JobMeta meta = new JobMeta(id, "Test Job", "description", "medium", List.of());
        JobCommandData cmd = new JobCommandData(
                ExecutionType.CMD, "echo test", Interpreter.BASH, List.of(creds));
        SimpleSchedule schedule = new SimpleSchedule("1h", null, null);
        return new JobData(meta, cmd, schedule, null);
    }
}
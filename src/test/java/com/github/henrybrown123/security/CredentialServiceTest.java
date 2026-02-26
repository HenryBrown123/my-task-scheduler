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
    private SecretProvider vault;

    @InjectMocks
    private CredentialService credentialService;

    @Test
    void isVaultAvailableShouldDelegateToVault() {
        when(vault.isAvailable()).thenReturn(true);
        assertTrue(credentialService.isVaultAvailable());
    }

    @Test
    void findJobsMissingCredentialsShouldReturnJobsWithMissingCredentials() {
        when(vault.read("smtp")).thenReturn(Optional.empty());

        var missing = credentialService.findJobsMissingCredentials(List.of(
                jobWith("job-1", cred("smtp", ESecretType.SMTP))
        ));

        assertEquals(1, missing.size());
        assertEquals("job-1", missing.get(0).meta().id());
    }

    @Test
    void findJobsMissingCredentialsShouldExcludeJobsWithAllCredentials() {
        when(vault.read("smtp")).thenReturn(Optional.of(Map.of("host", "smtp.gmail.com")));

        var missing = credentialService.findJobsMissingCredentials(List.of(
                jobWith("job-1", cred("smtp", ESecretType.SMTP))
        ));

        assertTrue(missing.isEmpty());
    }

    @Test
    void findJobsMissingCredentialsShouldExcludeJobsWithNoCredentials() {
        var missing = credentialService.findJobsMissingCredentials(List.of(jobWith("job-1")));

        assertTrue(missing.isEmpty());
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

        assertThrows(SecretStoreException.class, () ->
                credentialService.resolveForJob(
                        List.of(cred("smtp", ESecretType.SMTP))));
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

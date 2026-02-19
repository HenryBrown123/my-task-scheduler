package com.github.henrybrown123.security;

import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.response.LogicalResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.henrybrown123.configuration.AppConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Manages the Vault server lifecycle (start, init, unseal) and provides
 * read/write/delete operations against the Vault KV secrets engine.
 *
 * <p>When {@code vault.managed=true}, handles the full lifecycle: starts the
 * Vault server process, initialises on first run, and unseals on each startup.
 *
 * <p>When {@code vault.managed=false} (production), connects to an externally
 * managed Vault using the address from config and token from VAULT_TOKEN env var.
 *
 * <p>All Vault files (config, unseal key, token, logs) live inside the
 * configured vault data directory.
 */
public class VaultLifecycle {

    private final SecurityConfig vaultConfig;
    private final Path vaultDataDir;
    private final Path configFile;
    private final Path unsealKeyFile;
    private final Path tokenFile;
    private final Path logFile;
    private Vault vault;

    public VaultLifecycle() {
        this.vaultConfig = AppConfig.vault();
        this.vaultDataDir = Path.of(vaultConfig.dataDir()).toAbsolutePath();
        this.configFile = vaultDataDir.resolve("vault-config.hcl");
        this.unsealKeyFile = vaultDataDir.resolve(".unseal-key");
        this.tokenFile = vaultDataDir.resolve(".vault-token");
        this.logFile = vaultDataDir.resolve("vault.log");
    }

    /**
     * Ensures Vault is running, initialised, unsealed, and connected.
     * Call this once on application startup.
     *
     * @throws IOException if file operations fail
     * @throws InterruptedException if process operations are interrupted
     */
    public void ensureReady() throws IOException, InterruptedException {
        if (!vaultConfig.managed()) {
            connectClient();
            return;
        }
        ensureDirectories();
        ensureConfig();
        if (!isVaultRunning()) startVault();
        if (!isInitialised()) initialise();
        unseal();
        connectClient();
    }

    private void connectClient() throws IOException {
        try {
            String token = vaultConfig.managed()
                    ? Files.readString(tokenFile).trim()
                    : System.getenv("VAULT_TOKEN");

            var config = new VaultConfig()
                    .address(vaultConfig.address())
                    .token(token)
                    .engineVersion(2)
                    .build();
            this.vault = new Vault(config);
        } catch (VaultException e) {
            throw new RuntimeException("Failed to connect to Vault", e);
        }
    }

    private void ensureDirectories() throws IOException {
        Files.createDirectories(vaultDataDir);
    }

    private void ensureConfig() throws IOException {
        if (Files.exists(configFile)) return;

        String hcl = """
            storage "file" {
              path = "%s"
            }
            listener "tcp" {
              address     = "%s"
              tls_disable = %s
            }
            ui = %s
            disable_mlock = %s
            """.formatted(
                vaultDataDir,
                vaultConfig.listenerAddress(),
                vaultConfig.tlsDisable(),
                vaultConfig.uiEnabled(),
                vaultConfig.disableMlock()
        );

        Files.writeString(configFile, hcl);
    }

    private boolean isVaultRunning() {
        try {
            Process p = new ProcessBuilder("vault", "status",
                    "-address=" + vaultConfig.address(), "-format=json")
                    .redirectErrorStream(true).start();
            p.waitFor();
            return p.exitValue() == 0 || p.exitValue() == 2;
        } catch (Exception e) {
            return false;
        }
    }

    private void startVault() throws IOException, InterruptedException {
        System.out.println("Starting Vault...");
        new ProcessBuilder("vault", "server", "-config=" + configFile)
                .redirectOutput(logFile.toFile())
                .redirectErrorStream(true)
                .start();

        for (int i = 0; i < 10; i++) {
            Thread.sleep(500);
            if (isVaultRunning()) {
                System.out.println("Vault started.");
                return;
            }
        }
        throw new RuntimeException("Vault failed to start — check " + logFile);
    }

    private boolean isInitialised() {
        return Files.exists(unsealKeyFile) && Files.exists(tokenFile);
    }

    private void initialise() throws IOException, InterruptedException {
        System.out.println("Initialising Vault (first run)...");

        Process p = new ProcessBuilder("vault", "operator", "init",
                "-address=" + vaultConfig.address(),
                "-key-shares=1",
                "-key-threshold=1",
                "-format=json")
                .redirectErrorStream(true)
                .start();

        String output = new String(p.getInputStream().readAllBytes());
        p.waitFor();

        ObjectMapper mapper = new ObjectMapper();
        var json = mapper.readTree(output);
        String unsealKey = json.get("unseal_keys_b64").get(0).asText();
        String rootToken = json.get("root_token").asText();

        Files.writeString(unsealKeyFile, unsealKey);
        Files.writeString(tokenFile, rootToken);
        setOwnerOnly(unsealKeyFile);
        setOwnerOnly(tokenFile);

        System.out.println("Vault initialised.");
    }

    private void unseal() throws IOException, InterruptedException {
        String unsealKey = Files.readString(unsealKeyFile).trim();

        Process p = new ProcessBuilder("vault", "operator", "unseal",
                "-address=" + vaultConfig.address(),
                unsealKey)
                .redirectErrorStream(true)
                .start();
        p.waitFor();

        if (p.exitValue() != 0) {
            throw new RuntimeException("Failed to unseal Vault");
        }
        System.out.println("Vault unsealed and ready.");
    }

    private void setOwnerOnly(Path path) {
        File f = path.toFile();
        f.setReadable(false, false);
        f.setReadable(true, true);
        f.setWritable(false, false);
        f.setWritable(true, true);
    }

    /**
     * Write a secret to Vault.
     *
     * @param name the credential name (used as the Vault path suffix)
     * @param fields the key-value pairs to store
     */
    public void write(String name, Map<String, Object> fields) {
        try {
            vault.logical().write(vaultConfig.secretPath() + "/" + name, fields);
        } catch (VaultException e) {
            throw new RuntimeException("Failed to write to Vault: " + name, e);
        }
    }

    /**
     * Read a secret from Vault.
     *
     * @param name the credential name
     * @return the fields if present, empty otherwise
     */
    public Optional<Map<String, String>> read(String name) {
        try {
            LogicalResponse response = vault.logical()
                    .read(vaultConfig.secretPath() + "/" + name);
            if (response == null || response.getData() == null
                    || response.getData().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(response.getData());
        } catch (VaultException e) {
            return Optional.empty();
        }
    }

    /**
     * Delete a secret from Vault.
     *
     * @param name the credential name
     */
    public void delete(String name) {
        try {
            vault.logical().delete(vaultConfig.secretPath() + "/" + name);
        } catch (VaultException e) {
            throw new RuntimeException("Failed to delete from Vault: " + name, e);
        }
    }

    /**
     * Check if Vault is reachable and responding.
     *
     * @return true if Vault is healthy
     */
    public boolean isHealthy() {
        try {
            vault.logical().read(vaultConfig.secretPath());
            return true;
        } catch (VaultException e) {
            return false;
        }
    }
}
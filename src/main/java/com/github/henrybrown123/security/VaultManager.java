package com.github.henrybrown123.security;

import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.response.LogicalResponse;
import com.github.henrybrown123.configuration.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * Vault-backed implementation of {@link SecretProvider}.
 *
 * <p>Attempts to connect to Vault on construction. If Vault is not
 * reachable or {@code VAULT_TOKEN} is not set, the instance is
 * created in an unavailable state — {@link #isAvailable()} returns
 * false and the application can continue without credentials.
 *
 * <p>Vault lifecycle (starting, unsealing) is managed outside this
 * application — see {@code dev-vault.sh} for local development.
 */
public class VaultManager implements SecretProvider {
    private static final Logger log = LoggerFactory.getLogger(VaultManager.class);

    private final SecurityConfig config;
    private final Vault vault;
    private boolean available;

    public VaultManager() {
        this.config = AppConfig.vault();
        this.vault = buildClient();
        this.available = vault != null && checkConnection();
    }

    private VaultConfig vaultConfig() throws VaultException {
        return new VaultConfig()
                .address(config.address())
                .token(System.getenv("VAULT_TOKEN"))
                .engineVersion(2)
                .build();
    }

    private Vault buildClient() {
        String token = System.getenv("VAULT_TOKEN");
        if (token == null || token.isBlank()) {
            log.warn("VAULT_TOKEN not set — credential features unavailable");
            return null;
        }

        try {
            return new Vault(vaultConfig());
        } catch (VaultException e) {
            log.warn("Failed to build Vault client — credential features unavailable");
            return null;
        }
    }

    private boolean checkConnection() {
        try {
            vault.logical().read(config.secretPath());
            log.info("Connected to Vault at {}", config.address());
            return true;
        } catch (VaultException e) {
            log.warn("Could not reach Vault at {} — credential features unavailable",
                    config.address());
            return false;
        }
    }

    @Override
    public boolean isAvailable() {
        return isAvailable(false);
    }

    /**
     * Whether Vault is connected and usable.
     *
     * @param retry if true, re-checks connectivity rather than returning cached state
     */
    public boolean isAvailable(boolean retry) {
        if (vault == null) return false;
        if (retry) {
            this.available = checkConnection();
        }
        return available;
    }

    @Override
    public Optional<Map<String, String>> read(String name) {
        try {
            return Optional.ofNullable(vault.logical().read(config.secretPath() + "/" + name))
                    .map(LogicalResponse::getData)
                    .filter(data -> !data.isEmpty());
        } catch (VaultException e) {
            throw new SecretStoreException("Failed to read secret: " + name, e);
        }
    }

    @Override
    public void write(String name, Map<String, Object> fields) {
        try {
            vault.logical().write(config.secretPath() + "/" + name, fields);
        } catch (VaultException e) {
            throw new SecretStoreException("Failed to write secret: " + name, e);
        }
    }

    @Override
    public void delete(String name) {
        try {
            vault.logical().delete(config.secretPath() + "/" + name);
        } catch (VaultException e) {
            throw new SecretStoreException("Failed to delete secret: " + name, e);
        }
    }
}

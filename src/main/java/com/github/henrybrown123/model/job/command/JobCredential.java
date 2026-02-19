package com.github.henrybrown123.model.job.command;

import com.github.henrybrown123.security.SecretType;

public record JobCredentials(
        String name,
        SecretType type
) {}
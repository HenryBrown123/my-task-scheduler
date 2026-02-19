package com.github.henrybrown123.model.job.command;

import com.github.henrybrown123.security.ESecretType;

public record JobCredential(
        String name,
        ESecretType type
) {}
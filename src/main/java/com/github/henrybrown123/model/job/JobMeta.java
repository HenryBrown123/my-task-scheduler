package com.github.henrybrown123.model.job;

import java.util.List;

public record JobMeta(
        String id,
        String name,
        String description,
        String priority,
        List<String> tags
) {}

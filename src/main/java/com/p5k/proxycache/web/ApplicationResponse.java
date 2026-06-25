package com.p5k.proxycache.web;

import com.p5k.proxycache.rules.Application;

import java.time.Instant;

public record ApplicationResponse(
        Long id,
        String slug,
        String name,
        String baseUrl,
        String description,
        Long defaultTtlSeconds,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {

    public static ApplicationResponse from(Application app) {
        return new ApplicationResponse(app.getId(), app.getSlug(), app.getName(), app.getBaseUrl(),
                app.getDescription(), app.getDefaultTtlSeconds(), app.isEnabled(),
                app.getCreatedAt(), app.getUpdatedAt());
    }
}

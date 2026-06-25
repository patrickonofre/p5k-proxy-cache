package com.p5k.proxycache.web;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Set;

public record CacheRuleRequest(
        @NotNull Long applicationId,
        @NotBlank String pathPattern,
        @NotEmpty Set<String> methods,
        @Positive Long ttlSeconds,
        @Positive long maxSize,
        boolean enabled,
        String description) {
}

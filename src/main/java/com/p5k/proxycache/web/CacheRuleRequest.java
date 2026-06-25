package com.p5k.proxycache.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.Set;

public record CacheRuleRequest(
        @NotBlank String pathPattern,
        @NotEmpty Set<String> methods,
        @Positive long ttlSeconds,
        @Positive long maxSize,
        boolean enabled) {
}

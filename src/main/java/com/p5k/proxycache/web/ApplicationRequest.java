package com.p5k.proxycache.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record ApplicationRequest(
        @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]*$",
                message = "slug must be lowercase alphanumeric or hyphen, no slashes") String slug,
        @NotBlank String name,
        @NotBlank String baseUrl,
        String description,
        @Positive Long defaultTtlSeconds,
        boolean enabled) {
}

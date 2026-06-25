package com.p5k.proxycache.web;

import com.p5k.proxycache.rules.CacheRule;

import java.time.Instant;
import java.util.Set;

public record CacheRuleResponse(
        Long id,
        Long applicationId,
        String pathPattern,
        Set<String> methods,
        Long ttlSeconds,
        long maxSize,
        boolean enabled,
        String description,
        Instant createdAt,
        Instant updatedAt) {

    public static CacheRuleResponse from(CacheRule rule) {
        return new CacheRuleResponse(rule.getId(), rule.getApplicationId(), rule.getPathPattern(),
                rule.getMethods(), rule.getTtlSeconds(), rule.getMaxSize(), rule.isEnabled(),
                rule.getDescription(), rule.getCreatedAt(), rule.getUpdatedAt());
    }
}

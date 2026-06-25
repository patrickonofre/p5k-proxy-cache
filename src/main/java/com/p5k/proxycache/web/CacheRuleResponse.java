package com.p5k.proxycache.web;

import com.p5k.proxycache.rules.CacheRule;

import java.time.Instant;
import java.util.Set;

public record CacheRuleResponse(
        Long id,
        String pathPattern,
        Set<String> methods,
        long ttlSeconds,
        long maxSize,
        boolean enabled,
        Instant createdAt) {

    public static CacheRuleResponse from(CacheRule rule) {
        return new CacheRuleResponse(rule.getId(), rule.getPathPattern(), rule.getMethods(),
                rule.getTtlSeconds(), rule.getMaxSize(), rule.isEnabled(), rule.getCreatedAt());
    }
}

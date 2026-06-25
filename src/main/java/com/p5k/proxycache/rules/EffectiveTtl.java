package com.p5k.proxycache.rules;

import java.util.Optional;

/**
 * Resolves a rule's effective TTL: the rule's own {@code ttlSeconds} when set, otherwise the
 * application's {@code defaultTtlSeconds}. Empty when neither yields a positive value.
 */
public final class EffectiveTtl {

    private EffectiveTtl() {
    }

    public static Optional<Long> resolve(Long ruleTtlSeconds, Long appDefaultTtlSeconds) {
        Long ttl = ruleTtlSeconds != null ? ruleTtlSeconds : appDefaultTtlSeconds;
        return ttl != null && ttl > 0 ? Optional.of(ttl) : Optional.empty();
    }
}

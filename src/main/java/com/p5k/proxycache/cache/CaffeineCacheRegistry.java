package com.p5k.proxycache.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.p5k.proxycache.rules.CacheRule;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Owns one Caffeine cache per rule, each sized and expired by that rule's
 * {@code ttlSeconds} / {@code maxSize}. Caches are keyed by rule id.
 */
@Component
public class CaffeineCacheRegistry {

    private final ConcurrentMap<Long, Cache<String, CachedResponse>> caches = new ConcurrentHashMap<>();
    private final Ticker ticker;

    public CaffeineCacheRegistry() {
        this(Ticker.systemTicker());
    }

    CaffeineCacheRegistry(Ticker ticker) {
        this.ticker = ticker;
    }

    public Cache<String, CachedResponse> cacheFor(CacheRule rule, long effectiveTtlSeconds) {
        return caches.computeIfAbsent(rule.getId(), id -> build(rule, effectiveTtlSeconds));
    }

    public void rebuild(CacheRule rule, long effectiveTtlSeconds) {
        caches.put(rule.getId(), build(rule, effectiveTtlSeconds));
    }

    public void evictRule(Long ruleId) {
        caches.remove(ruleId);
    }

    public void clearAll() {
        caches.clear();
    }

    private Cache<String, CachedResponse> build(CacheRule rule, long effectiveTtlSeconds) {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(effectiveTtlSeconds))
                .maximumSize(rule.getMaxSize())
                .ticker(ticker)
                .build();
    }
}

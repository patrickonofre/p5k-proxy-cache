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

    public Cache<String, CachedResponse> cacheFor(CacheRule rule) {
        return caches.computeIfAbsent(rule.getId(), id -> build(rule));
    }

    public void rebuild(CacheRule rule) {
        caches.put(rule.getId(), build(rule));
    }

    public void evictRule(Long ruleId) {
        caches.remove(ruleId);
    }

    public void clearAll() {
        caches.clear();
    }

    private Cache<String, CachedResponse> build(CacheRule rule) {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(rule.getTtlSeconds()))
                .maximumSize(rule.getMaxSize())
                .ticker(ticker)
                .build();
    }
}

package com.p5k.proxycache.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Ticker;
import com.p5k.proxycache.rules.CacheRule;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class CaffeineCacheRegistryTest {

    private final AtomicLong clock = new AtomicLong(0);
    private final Ticker ticker = clock::get;
    private final CaffeineCacheRegistry registry = new CaffeineCacheRegistry(ticker);

    private static CacheRule rule(long maxSize) {
        return new CacheRule(1L, 1L, "/a", Set.of("GET"), null, maxSize, true, null);
    }

    private CachedResponse sample() {
        return CachedResponse.capture(200, "text/plain", Map.of(), "v".getBytes());
    }

    @Test
    void cacheForReturnsSameInstancePerRule() {
        CacheRule rule = rule(100);

        assertThat(registry.cacheFor(rule, 60)).isSameAs(registry.cacheFor(rule, 60));
    }

    @Test
    void entryExpiresAfterTtl() {
        CacheRule rule = rule(100);
        Cache<String, CachedResponse> cache = registry.cacheFor(rule, 10);
        cache.put("k", sample());

        clock.addAndGet(Duration.ofSeconds(11).toNanos());
        cache.cleanUp();

        assertThat(cache.getIfPresent("k")).isNull();
    }

    @Test
    void evictsBeyondMaxSize() {
        CacheRule rule = rule(2);
        Cache<String, CachedResponse> cache = registry.cacheFor(rule, 60);
        cache.put("k1", sample());
        cache.put("k2", sample());
        cache.put("k3", sample());
        cache.cleanUp();

        assertThat(cache.estimatedSize()).isLessThanOrEqualTo(2);
    }

    @Test
    void rebuildReplacesCacheInstance() {
        CacheRule rule = rule(100);
        Cache<String, CachedResponse> first = registry.cacheFor(rule, 60);

        registry.rebuild(rule, 60);

        assertThat(registry.cacheFor(rule, 60)).isNotSameAs(first);
    }
}

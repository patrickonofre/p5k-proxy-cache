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

    private CachedResponse sample() {
        return CachedResponse.capture(200, "text/plain", Map.of(), "v".getBytes());
    }

    @Test
    void cacheForReturnsSameInstancePerRule() {
        CacheRule rule = new CacheRule(1L, "/a", Set.of("GET"), 60, 100, true);

        assertThat(registry.cacheFor(rule)).isSameAs(registry.cacheFor(rule));
    }

    @Test
    void entryExpiresAfterTtl() {
        CacheRule rule = new CacheRule(1L, "/a", Set.of("GET"), 10, 100, true);
        Cache<String, CachedResponse> cache = registry.cacheFor(rule);
        cache.put("k", sample());

        clock.addAndGet(Duration.ofSeconds(11).toNanos());
        cache.cleanUp();

        assertThat(cache.getIfPresent("k")).isNull();
    }

    @Test
    void evictsBeyondMaxSize() {
        CacheRule rule = new CacheRule(1L, "/a", Set.of("GET"), 60, 2, true);
        Cache<String, CachedResponse> cache = registry.cacheFor(rule);
        cache.put("k1", sample());
        cache.put("k2", sample());
        cache.put("k3", sample());
        cache.cleanUp();

        assertThat(cache.estimatedSize()).isLessThanOrEqualTo(2);
    }

    @Test
    void rebuildReplacesCacheInstance() {
        CacheRule rule = new CacheRule(1L, "/a", Set.of("GET"), 60, 100, true);
        Cache<String, CachedResponse> first = registry.cacheFor(rule);

        registry.rebuild(rule);

        assertThat(registry.cacheFor(rule)).isNotSameAs(first);
    }
}

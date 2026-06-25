package com.p5k.proxycache.proxy;

import com.p5k.proxycache.rules.CacheRule;
import com.p5k.proxycache.rules.CacheRuleRepository;
import com.p5k.proxycache.rules.RuleRegistry;
import com.p5k.proxycache.cache.CaffeineCacheRegistry;
import com.p5k.proxycache.support.AbstractPostgresIT;
import com.p5k.proxycache.support.StubUpstream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CachingProxyFilterIT extends AbstractPostgresIT {

    static final StubUpstream UPSTREAM = new StubUpstream();

    @LocalServerPort
    int port;

    @Autowired
    CacheRuleRepository ruleRepository;

    @Autowired
    RuleRegistry ruleRegistry;

    @Autowired
    CaffeineCacheRegistry caches;

    @DynamicPropertySource
    static void upstreamProperties(DynamicPropertyRegistry registry) {
        UPSTREAM.start();
        registry.add("proxy.upstream.base-url", UPSTREAM::baseUrl);
    }

    @AfterAll
    static void tearDown() {
        UPSTREAM.stopQuietly();
    }

    @BeforeEach
    void reset() {
        ruleRepository.deleteAll();
        ruleRegistry.reload();
        caches.clearAll();
        UPSTREAM.resetCount();
    }

    private void seedRule(String pattern, Set<String> methods, boolean enabled) {
        ruleRepository.save(new CacheRule(pattern, methods, 300, 1000, enabled));
        ruleRegistry.reload();
    }

    private Resp get(String path) {
        return RestClient.create("http://localhost:" + port).get().uri(path)
                .exchange((req, res) -> new Resp(res.getStatusCode().value(),
                        res.bodyTo(String.class), res.getHeaders().getFirst("X-Cache")));
    }

    private Resp post(String path) {
        return RestClient.create("http://localhost:" + port).post().uri(path)
                .exchange((req, res) -> new Resp(res.getStatusCode().value(),
                        res.bodyTo(String.class), res.getHeaders().getFirst("X-Cache")));
    }

    @Test
    void missThenHitServesFromCacheWithZeroExtraUpstreamCalls() {
        seedRule("/api/**", Set.of("GET"), true);
        UPSTREAM.respond(200, "application/json", "{\"v\":1}");

        Resp first = get("/api/items");
        assertThat(first.status()).isEqualTo(200);
        assertThat(first.body()).isEqualTo("{\"v\":1}");
        assertThat(first.xCache()).isEqualTo("MISS");
        assertThat(UPSTREAM.requestCount()).isEqualTo(1);

        Resp second = get("/api/items");
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.body()).isEqualTo("{\"v\":1}");
        assertThat(second.xCache()).isEqualTo("HIT");
        assertThat(UPSTREAM.requestCount()).isEqualTo(1);
    }

    @Test
    void nonTwoXxResponsesAreNotCached() {
        seedRule("/api/**", Set.of("GET"), true);
        UPSTREAM.respond(500, "text/plain", "boom");

        Resp first = get("/api/x");
        assertThat(first.status()).isEqualTo(500);
        assertThat(UPSTREAM.requestCount()).isEqualTo(1);

        get("/api/x");
        assertThat(UPSTREAM.requestCount()).isEqualTo(2);
    }

    @Test
    void unmatchedRequestIsBypassed() {
        UPSTREAM.respond(200, "text/plain", "ok");

        Resp response = get("/not-mapped");

        assertThat(response.xCache()).isEqualTo("BYPASS");
        assertThat(UPSTREAM.requestCount()).isEqualTo(1);
    }

    @Test
    void nonGetMethodIsBypassedEvenWhenPathMatches() {
        seedRule("/api/**", Set.of("GET"), true);
        UPSTREAM.respond(200, "text/plain", "ok");

        Resp response = post("/api/items");

        assertThat(response.xCache()).isEqualTo("BYPASS");
        assertThat(UPSTREAM.requestCount()).isEqualTo(1);
    }

    @Test
    void disabledRuleIsBypassed() {
        seedRule("/api/**", Set.of("GET"), false);
        UPSTREAM.respond(200, "text/plain", "ok");

        Resp response = get("/api/items");

        assertThat(response.xCache()).isEqualTo("BYPASS");
        assertThat(UPSTREAM.requestCount()).isEqualTo(1);
    }

    @Test
    void reorderedQueryParamsHitTheSameEntry() {
        seedRule("/api/**", Set.of("GET"), true);
        UPSTREAM.respond(200, "text/plain", "data");

        Resp first = get("/api/items?a=1&b=2");
        assertThat(first.xCache()).isEqualTo("MISS");

        Resp second = get("/api/items?b=2&a=1");
        assertThat(second.xCache()).isEqualTo("HIT");
        assertThat(UPSTREAM.requestCount()).isEqualTo(1);
    }

    private record Resp(int status, String body, String xCache) {
    }
}

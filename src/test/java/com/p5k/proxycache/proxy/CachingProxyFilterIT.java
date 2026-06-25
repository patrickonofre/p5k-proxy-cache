package com.p5k.proxycache.proxy;

import com.p5k.proxycache.cache.CaffeineCacheRegistry;
import com.p5k.proxycache.rules.Application;
import com.p5k.proxycache.rules.ApplicationRegistry;
import com.p5k.proxycache.rules.ApplicationRepository;
import com.p5k.proxycache.rules.CacheRule;
import com.p5k.proxycache.rules.CacheRuleRepository;
import com.p5k.proxycache.rules.RuleRegistry;
import com.p5k.proxycache.support.AbstractPostgresIT;
import com.p5k.proxycache.support.StubUpstream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
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
    ApplicationRepository applications;

    @Autowired
    RuleRegistry ruleRegistry;

    @Autowired
    ApplicationRegistry appRegistry;

    @Autowired
    CaffeineCacheRegistry caches;

    Long appId;

    @BeforeAll
    static void startUpstream() {
        UPSTREAM.start();
    }

    @AfterAll
    static void tearDown() {
        UPSTREAM.stopQuietly();
    }

    @BeforeEach
    void reset() {
        ruleRepository.deleteAll();
        applications.deleteAll();
        appId = applications.save(new Application("app", "App", UPSTREAM.baseUrl(), null, null, true)).getId();
        ruleRegistry.reload();
        appRegistry.reload();
        caches.clearAll();
        UPSTREAM.resetCount();
    }

    private void seedRule(Long applicationId, String pattern, Set<String> methods, boolean enabled) {
        ruleRepository.save(new CacheRule(applicationId, pattern, methods, 300L, 1000, enabled, null));
        ruleRegistry.reload();
    }

    private void seedRule(String pattern, Set<String> methods, boolean enabled) {
        seedRule(appId, pattern, methods, enabled);
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

        Resp first = get("/app/api/items");
        assertThat(first.status()).isEqualTo(200);
        assertThat(first.body()).isEqualTo("{\"v\":1}");
        assertThat(first.xCache()).isEqualTo("MISS");
        assertThat(UPSTREAM.requestCount()).isEqualTo(1);
        assertThat(UPSTREAM.lastPath()).isEqualTo("/api/items"); // slug prefix stripped

        Resp second = get("/app/api/items");
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.body()).isEqualTo("{\"v\":1}");
        assertThat(second.xCache()).isEqualTo("HIT");
        assertThat(UPSTREAM.requestCount()).isEqualTo(1);
    }

    @Test
    void nonTwoXxResponsesAreNotCached() {
        seedRule("/api/**", Set.of("GET"), true);
        UPSTREAM.respond(500, "text/plain", "boom");

        Resp first = get("/app/api/x");
        assertThat(first.status()).isEqualTo(500);
        assertThat(UPSTREAM.requestCount()).isEqualTo(1);

        get("/app/api/x");
        assertThat(UPSTREAM.requestCount()).isEqualTo(2);
    }

    @Test
    void unmatchedRequestIsBypassed() {
        UPSTREAM.respond(200, "text/plain", "ok");

        Resp response = get("/app/not-mapped");

        assertThat(response.xCache()).isEqualTo("BYPASS");
        assertThat(UPSTREAM.requestCount()).isEqualTo(1);
        assertThat(UPSTREAM.lastPath()).isEqualTo("/not-mapped");
    }

    @Test
    void unknownSlugReturns404WithoutForwarding() {
        UPSTREAM.respond(200, "text/plain", "ok");

        Resp response = get("/nope/api/items");

        assertThat(response.status()).isEqualTo(404);
        assertThat(UPSTREAM.requestCount()).isZero();
    }

    @Test
    void nonGetMethodIsBypassedEvenWhenPathMatches() {
        seedRule("/api/**", Set.of("GET"), true);
        UPSTREAM.respond(200, "text/plain", "ok");

        Resp response = post("/app/api/items");

        assertThat(response.xCache()).isEqualTo("BYPASS");
        assertThat(UPSTREAM.requestCount()).isEqualTo(1);
    }

    @Test
    void disabledRuleIsBypassed() {
        seedRule("/api/**", Set.of("GET"), false);
        UPSTREAM.respond(200, "text/plain", "ok");

        Resp response = get("/app/api/items");

        assertThat(response.xCache()).isEqualTo("BYPASS");
        assertThat(UPSTREAM.requestCount()).isEqualTo(1);
    }

    @Test
    void reorderedQueryParamsHitTheSameEntry() {
        seedRule("/api/**", Set.of("GET"), true);
        UPSTREAM.respond(200, "text/plain", "data");

        Resp first = get("/app/api/items?a=1&b=2");
        assertThat(first.xCache()).isEqualTo("MISS");

        Resp second = get("/app/api/items?b=2&a=1");
        assertThat(second.xCache()).isEqualTo("HIT");
        assertThat(UPSTREAM.requestCount()).isEqualTo(1);
    }

    @Test
    void twoAppsWithSameRulePathCacheIndependently() {
        seedRule("/api/**", Set.of("GET"), true);
        Long app2 = applications.save(new Application("app2", "App2", UPSTREAM.baseUrl(), null, null, true)).getId();
        seedRule(app2, "/api/**", Set.of("GET"), true);
        appRegistry.reload();
        UPSTREAM.respond(200, "text/plain", "data");

        assertThat(get("/app/api/x").xCache()).isEqualTo("MISS");
        assertThat(UPSTREAM.requestCount()).isEqualTo(1);
        // same path, different app → separate cache → still a MISS, hits upstream again
        assertThat(get("/app2/api/x").xCache()).isEqualTo("MISS");
        assertThat(UPSTREAM.requestCount()).isEqualTo(2);
        // first app now served from its own cache
        assertThat(get("/app/api/x").xCache()).isEqualTo("HIT");
        assertThat(UPSTREAM.requestCount()).isEqualTo(2);
    }

    private record Resp(int status, String body, String xCache) {
    }
}

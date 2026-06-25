package com.p5k.proxycache.config;

import com.p5k.proxycache.rules.Application;
import com.p5k.proxycache.rules.ApplicationRegistry;
import com.p5k.proxycache.rules.ApplicationRepository;
import com.p5k.proxycache.rules.CacheRuleRepository;
import com.p5k.proxycache.support.AbstractPostgresIT;
import com.p5k.proxycache.support.StubUpstream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GatewayRouteIT extends AbstractPostgresIT {

    static final StubUpstream UPSTREAM = new StubUpstream();

    @LocalServerPort
    int port;

    @Autowired
    ApplicationRepository applications;

    @Autowired
    CacheRuleRepository rules;

    @Autowired
    ApplicationRegistry appRegistry;

    @BeforeAll
    static void startUpstream() {
        UPSTREAM.start();
    }

    @AfterAll
    static void tearDown() {
        UPSTREAM.stopQuietly();
    }

    @BeforeEach
    void seedApp() {
        rules.deleteAll();
        applications.deleteAll();
        applications.save(new Application("app", "App", UPSTREAM.baseUrl(), null, null, true));
        appRegistry.reload();
    }

    private Resp get(String path) {
        return RestClient.create("http://localhost:" + port).get().uri(path)
                .exchange((request, response) ->
                        new Resp(response.getStatusCode().value(), response.bodyTo(String.class)));
    }

    @Test
    @Order(1)
    void forwardsGetToUpstreamWithSlugStripped() {
        UPSTREAM.respond(200, "text/plain", "hello-from-upstream");

        Resp response = get("/app/anything");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("hello-from-upstream");
        assertThat(UPSTREAM.requestCount()).isEqualTo(1);
        assertThat(UPSTREAM.lastPath()).isEqualTo("/anything");
    }

    @Test
    @Order(2)
    void upstreamDownReturnsServerError() {
        UPSTREAM.stopQuietly();

        Resp response = get("/app/anything");

        assertThat(response.status()).isGreaterThanOrEqualTo(500);
    }

    private record Resp(int status, String body) {
    }
}

package com.p5k.proxycache.config;

import com.p5k.proxycache.support.AbstractPostgresIT;
import com.p5k.proxycache.support.StubUpstream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GatewayRouteIT extends AbstractPostgresIT {

    static final StubUpstream UPSTREAM = new StubUpstream();

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void upstreamProperties(DynamicPropertyRegistry registry) {
        UPSTREAM.start();
        registry.add("proxy.upstream.base-url", UPSTREAM::baseUrl);
    }

    @AfterAll
    static void tearDown() {
        UPSTREAM.stopQuietly();
    }

    private Resp get(String path) {
        return RestClient.create("http://localhost:" + port).get().uri(path)
                .exchange((request, response) ->
                        new Resp(response.getStatusCode().value(), response.bodyTo(String.class)));
    }

    @Test
    @Order(1)
    void forwardsGetToUpstreamVerbatim() {
        UPSTREAM.respond(200, "text/plain", "hello-from-upstream");

        Resp response = get("/anything");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("hello-from-upstream");
        assertThat(UPSTREAM.requestCount()).isEqualTo(1);
    }

    @Test
    @Order(2)
    void upstreamDownReturnsServerError() {
        UPSTREAM.stopQuietly();

        Resp response = get("/anything");

        assertThat(response.status()).isGreaterThanOrEqualTo(500);
    }

    private record Resp(int status, String body) {
    }
}

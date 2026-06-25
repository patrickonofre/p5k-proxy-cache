package com.p5k.proxycache.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

/**
 * Single catch-all route: every path is forwarded to the configured upstream. The
 * caching filter sits in front and short-circuits HITs before requests reach this route.
 */
@Configuration
public class GatewayConfig {

    @Bean
    public RouterFunction<ServerResponse> proxyRoute(
            @Value("${proxy.upstream.base-url}") String upstreamBaseUrl) {
        RequestPredicate proxyPaths = path("/**")
                .and(path("/admin/**").negate())
                .and(path("/actuator/**").negate());
        return route("proxy")
                .route(proxyPaths, http())
                .before(uri(upstreamBaseUrl))
                .build();
    }
}

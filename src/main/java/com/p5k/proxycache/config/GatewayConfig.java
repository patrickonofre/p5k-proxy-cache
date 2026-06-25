package com.p5k.proxycache.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.stripPrefix;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

/**
 * Catch-all proxy route. The upstream target is set per request by {@link
 * com.p5k.proxycache.proxy.CachingProxyFilter} (via {@code GATEWAY_REQUEST_URL_ATTR}) from the
 * resolved application's base URL — there is no single static upstream. The app slug (first
 * path segment) is stripped here so the upstream receives a backend-relative path. The caching
 * filter sits in front and short-circuits HITs before requests reach this route.
 */
@Configuration
public class GatewayConfig {

    @Bean
    public RouterFunction<ServerResponse> proxyRoute() {
        RequestPredicate proxyPaths = path("/**")
                .and(path("/admin/**").negate())
                .and(path("/actuator/**").negate());
        return route("proxy")
                .route(proxyPaths, http())
                .before(stripPrefix(1))
                .build();
    }
}

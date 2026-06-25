package com.p5k.proxycache.proxy;

import com.github.benmanes.caffeine.cache.Cache;
import com.p5k.proxycache.cache.CacheKeyFactory;
import com.p5k.proxycache.cache.CachedResponse;
import com.p5k.proxycache.cache.CaffeineCacheRegistry;
import com.p5k.proxycache.rules.Application;
import com.p5k.proxycache.rules.ApplicationRegistry;
import com.p5k.proxycache.rules.CacheRule;
import com.p5k.proxycache.rules.EffectiveTtl;
import com.p5k.proxycache.rules.RuleRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Caching + routing layer in front of the gateway route. Resolves the request's app slug
 * (first path segment) to an {@link Application}, sets the upstream target for the downstream
 * gateway route, and matches the backend-relative remainder against that app's rules. On a
 * cache HIT it writes the cached response without invoking the chain, so the request never
 * reaches the upstream. On a MISS it forwards (gateway strips the slug + forwards to the app
 * base URL), captures the upstream response, and stores 2xx. Resolved app + no rule → BYPASS
 * forward. Unknown/disabled slug → 404. Admin/actuator paths are handled locally.
 */
public class CachingProxyFilter extends OncePerRequestFilter {

    static final String CACHE_HEADER = "X-Cache";

    private final ApplicationRegistry applications;
    private final RuleRegistry rules;
    private final CaffeineCacheRegistry caches;
    private final CacheKeyFactory keys;

    public CachingProxyFilter(ApplicationRegistry applications, RuleRegistry rules,
                              CaffeineCacheRegistry caches, CacheKeyFactory keys) {
        this.applications = applications;
        this.rules = rules;
        this.caches = caches;
        this.keys = keys;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (isLocal(uri)) {
            chain.doFilter(request, response);
            return;
        }

        SlugRouting route = SlugRouting.of(uri);
        if (!route.hasSlug()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Optional<Application> appMatch;
        try {
            appMatch = applications.resolve(route.slug());
        } catch (RuntimeException ex) {
            appMatch = Optional.empty();
        }
        if (appMatch.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Application app = appMatch.get();
        // Target the downstream gateway route at this app's upstream (used on MISS / BYPASS).
        request.setAttribute(MvcUtils.GATEWAY_REQUEST_URL_ATTR, URI.create(app.getBaseUrl()));

        Optional<CacheRule> match;
        try {
            match = rules.match(app.getId(), request.getMethod(), route.remainder());
        } catch (RuntimeException ex) {
            bypass(request, response, chain);
            return;
        }
        if (match.isEmpty()) {
            bypass(request, response, chain);
            return;
        }

        CacheRule rule = match.get();
        Optional<Long> ttl = EffectiveTtl.resolve(rule.getTtlSeconds(), app.getDefaultTtlSeconds());
        if (ttl.isEmpty()) {
            // No resolvable TTL (should be prevented at admin time) — never cache, just forward.
            bypass(request, response, chain);
            return;
        }

        String key = keys.keyOf(request, route.remainder());

        Cache<String, CachedResponse> cache;
        CachedResponse cached;
        try {
            cache = caches.cacheFor(rule, ttl.get());
            cached = cache.getIfPresent(key);
        } catch (RuntimeException ex) {
            // Cache lookup failed — degrade to a plain forward, never fail the request.
            response.setHeader(CACHE_HEADER, "MISS");
            chain.doFilter(request, response);
            return;
        }

        if (cached != null) {
            writeFromCache(cached, response);
            return;
        }

        forwardAndCache(request, response, chain, cache, key);
    }

    private boolean isLocal(String uri) {
        return uri.equals("/admin") || uri.startsWith("/admin/")
                || uri.equals("/actuator") || uri.startsWith("/actuator/");
    }

    private void bypass(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(CACHE_HEADER, "BYPASS");
        chain.doFilter(request, response);
    }

    private void forwardAndCache(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
                                 Cache<String, CachedResponse> cache, String key)
            throws ServletException, IOException {
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, wrapper);
            int status = wrapper.getStatus();
            if (status >= 200 && status < 300) {
                try {
                    cache.put(key, snapshot(wrapper));
                } catch (RuntimeException ignored) {
                    // cache write is best-effort
                }
            }
            wrapper.setHeader(CACHE_HEADER, "MISS");
        } finally {
            wrapper.copyBodyToResponse();
        }
    }

    private CachedResponse snapshot(ContentCachingResponseWrapper wrapper) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : wrapper.getHeaderNames()) {
            headers.put(name, wrapper.getHeader(name));
        }
        return CachedResponse.capture(wrapper.getStatus(), wrapper.getContentType(),
                headers, wrapper.getContentAsByteArray());
    }

    private void writeFromCache(CachedResponse cached, HttpServletResponse response) throws IOException {
        response.setStatus(cached.status());
        if (cached.contentType() != null) {
            response.setContentType(cached.contentType());
        }
        cached.headers().forEach(response::setHeader);
        response.setHeader(CACHE_HEADER, "HIT");
        byte[] body = cached.body();
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }
}

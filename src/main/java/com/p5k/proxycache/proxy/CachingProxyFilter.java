package com.p5k.proxycache.proxy;

import com.github.benmanes.caffeine.cache.Cache;
import com.p5k.proxycache.cache.CacheKeyFactory;
import com.p5k.proxycache.cache.CachedResponse;
import com.p5k.proxycache.cache.CaffeineCacheRegistry;
import com.p5k.proxycache.rules.CacheRule;
import com.p5k.proxycache.rules.RuleRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Caching layer in front of the gateway route. On a cache HIT it writes the cached
 * response and returns without invoking the chain, so the request never reaches the
 * upstream. On a MISS it lets the request route, captures the upstream response, and
 * stores 2xx responses. Unmatched, non-GET, and disabled-rule requests pass through.
 */
public class CachingProxyFilter extends OncePerRequestFilter {

    static final String CACHE_HEADER = "X-Cache";

    private final RuleRegistry rules;
    private final CaffeineCacheRegistry caches;
    private final CacheKeyFactory keys;

    public CachingProxyFilter(RuleRegistry rules, CaffeineCacheRegistry caches, CacheKeyFactory keys) {
        this.rules = rules;
        this.caches = caches;
        this.keys = keys;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Optional<CacheRule> match;
        try {
            match = rules.match(request.getMethod(), request.getRequestURI());
        } catch (RuntimeException ex) {
            bypass(request, response, chain);
            return;
        }

        if (match.isEmpty()) {
            bypass(request, response, chain);
            return;
        }

        CacheRule rule = match.get();
        String key = keys.keyOf(request);

        Cache<String, CachedResponse> cache;
        CachedResponse cached;
        try {
            cache = caches.cacheFor(rule);
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

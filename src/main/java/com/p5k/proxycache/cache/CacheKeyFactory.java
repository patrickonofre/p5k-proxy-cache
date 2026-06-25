package com.p5k.proxycache.cache;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds a canonical cache key {@code METHOD /path?sortedQuery}. Query parameters are
 * sorted so order does not fragment the cache. Request body and headers are ignored.
 */
@Component
public class CacheKeyFactory {

    public String keyOf(HttpServletRequest request) {
        return keyOf(request, request.getRequestURI());
    }

    /**
     * Builds the key from an explicit, backend-relative {@code path} (the app slug prefix
     * already stripped) so the same upstream resource shares one entry regardless of slug.
     */
    public String keyOf(HttpServletRequest request, String path) {
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        String query = canonicalQuery(request.getParameterMap());
        return query.isEmpty() ? method + " " + path : method + " " + path + "?" + query;
    }

    private String canonicalQuery(Map<String, String[]> params) {
        return params.entrySet().stream()
                .flatMap(entry -> {
                    String[] values = entry.getValue();
                    if (values == null || values.length == 0) {
                        return Stream.of(new AbstractMap.SimpleEntry<>(entry.getKey(), ""));
                    }
                    return Arrays.stream(values)
                            .map(value -> new AbstractMap.SimpleEntry<>(entry.getKey(), value));
                })
                .sorted(Map.Entry.<String, String>comparingByKey()
                        .thenComparing(Map.Entry.comparingByValue()))
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }
}

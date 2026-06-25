package com.p5k.proxycache.cache;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Immutable snapshot of a cacheable upstream response. Hop-by-hop headers are stripped
 * at capture time so they are never replayed from cache.
 */
public record CachedResponse(int status, String contentType, Map<String, String> headers, byte[] body) {

    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "content-length");

    public static CachedResponse capture(int status, String contentType,
                                         Map<String, String> rawHeaders, byte[] body) {
        Map<String, String> safe = new LinkedHashMap<>();
        if (rawHeaders != null) {
            rawHeaders.forEach((name, value) -> {
                if (name != null && !HOP_BY_HOP.contains(name.toLowerCase(Locale.ROOT))) {
                    safe.put(name, value);
                }
            });
        }
        return new CachedResponse(status, contentType, Collections.unmodifiableMap(safe),
                body == null ? new byte[0] : body.clone());
    }
}

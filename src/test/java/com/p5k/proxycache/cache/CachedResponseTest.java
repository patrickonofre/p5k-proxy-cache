package com.p5k.proxycache.cache;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CachedResponseTest {

    @Test
    void stripsHopByHopHeadersButKeepsTheRest() {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("Content-Type", "application/json");
        raw.put("Connection", "keep-alive");
        raw.put("Transfer-Encoding", "chunked");
        raw.put("Content-Length", "123");
        raw.put("X-Custom", "v");

        CachedResponse response = CachedResponse.capture(200, "application/json", raw, "{}".getBytes());

        assertThat(response.headers()).containsKeys("Content-Type", "X-Custom");
        assertThat(response.headers()).doesNotContainKeys("Connection", "Transfer-Encoding", "Content-Length");
    }

    @Test
    void retainsStatusContentTypeAndBody() {
        CachedResponse response = CachedResponse.capture(200, "text/plain", Map.of(), "hi".getBytes());

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.contentType()).isEqualTo("text/plain");
        assertThat(new String(response.body())).isEqualTo("hi");
    }

    @Test
    void hopByHopStrippingIsCaseInsensitive() {
        CachedResponse response = CachedResponse.capture(
                200, null, Map.of("CONNECTION", "x", "content-length", "5"), new byte[0]);

        assertThat(response.headers()).isEmpty();
    }
}

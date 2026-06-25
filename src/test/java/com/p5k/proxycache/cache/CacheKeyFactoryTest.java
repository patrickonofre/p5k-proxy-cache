package com.p5k.proxycache.cache;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class CacheKeyFactoryTest {

    private final CacheKeyFactory factory = new CacheKeyFactory();

    @Test
    void includesMethodAndPath() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");

        assertThat(factory.keyOf(request)).isEqualTo("GET /api/products");
    }

    @Test
    void sortsQueryParamsSoOrderDoesNotMatter() {
        MockHttpServletRequest first = new MockHttpServletRequest("GET", "/p");
        first.addParameter("a", "1");
        first.addParameter("b", "2");

        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/p");
        second.addParameter("b", "2");
        second.addParameter("a", "1");

        assertThat(factory.keyOf(first)).isEqualTo("GET /p?a=1&b=2");
        assertThat(factory.keyOf(first)).isEqualTo(factory.keyOf(second));
    }

    @Test
    void differentQueryValuesProduceDifferentKeys() {
        MockHttpServletRequest first = new MockHttpServletRequest("GET", "/p");
        first.addParameter("a", "1");

        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/p");
        second.addParameter("a", "2");

        assertThat(factory.keyOf(first)).isNotEqualTo(factory.keyOf(second));
    }
}

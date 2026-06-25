package com.p5k.proxycache.proxy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugRoutingTest {

    @Test
    void splitsSlugAndRemainder() {
        SlugRouting route = SlugRouting.of("/billing/users/1");

        assertThat(route.slug()).isEqualTo("billing");
        assertThat(route.remainder()).isEqualTo("/users/1");
        assertThat(route.hasSlug()).isTrue();
    }

    @Test
    void slugWithoutRemainderYieldsRoot() {
        SlugRouting route = SlugRouting.of("/billing");

        assertThat(route.slug()).isEqualTo("billing");
        assertThat(route.remainder()).isEqualTo("/");
    }

    @Test
    void rootPathHasNoSlug() {
        SlugRouting route = SlugRouting.of("/");

        assertThat(route.slug()).isEmpty();
        assertThat(route.hasSlug()).isFalse();
    }
}

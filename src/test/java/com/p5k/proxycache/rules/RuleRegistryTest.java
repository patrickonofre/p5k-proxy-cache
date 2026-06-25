package com.p5k.proxycache.rules;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuleRegistryTest {

    private final CacheRuleRepository repository = mock(CacheRuleRepository.class);
    private final RuleRegistry registry = new RuleRegistry(repository);

    private static CacheRule rule(long applicationId, String path, boolean enabled) {
        return new CacheRule(applicationId, path, Set.of("GET"), 60L, 100, enabled, null);
    }

    private void load(CacheRule... rules) {
        when(repository.findAllByEnabledTrue()).thenReturn(List.of(rules));
        registry.reload();
    }

    @Test
    void nonGetMethodNeverMatches() {
        load(rule(1L, "/a", true));

        assertThat(registry.match(1L, "POST", "/a")).isEmpty();
    }

    @Test
    void disabledRuleIsExcluded() {
        load(rule(1L, "/a", false));

        assertThat(registry.match(1L, "GET", "/a")).isEmpty();
    }

    @Test
    void mostSpecificPatternWins() {
        load(rule(1L, "/**", true), rule(1L, "/api/products/**", true));

        assertThat(registry.match(1L, "GET", "/api/products/1"))
                .get()
                .extracting(CacheRule::getPathPattern)
                .isEqualTo("/api/products/**");
    }

    @Test
    void noPatternMatchReturnsEmpty() {
        load(rule(1L, "/api/products/**", true));

        assertThat(registry.match(1L, "GET", "/orders/1")).isEmpty();
    }

    @Test
    void rulesAreScopedToTheirApplication() {
        load(rule(1L, "/users/**", true), rule(2L, "/users/**", true));

        assertThat(registry.match(1L, "GET", "/users/1"))
                .get().extracting(CacheRule::getApplicationId).isEqualTo(1L);
        assertThat(registry.match(2L, "GET", "/users/1"))
                .get().extracting(CacheRule::getApplicationId).isEqualTo(2L);
        assertThat(registry.match(3L, "GET", "/users/1")).isEmpty();
    }
}

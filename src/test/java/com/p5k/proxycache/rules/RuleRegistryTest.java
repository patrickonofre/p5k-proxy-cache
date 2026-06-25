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

    private void load(CacheRule... rules) {
        when(repository.findAllByEnabledTrue()).thenReturn(List.of(rules));
        registry.reload();
    }

    @Test
    void nonGetMethodNeverMatches() {
        load(new CacheRule("/a", Set.of("GET"), 60, 100, true));

        assertThat(registry.match("POST", "/a")).isEmpty();
    }

    @Test
    void disabledRuleIsExcluded() {
        load(new CacheRule("/a", Set.of("GET"), 60, 100, false));

        assertThat(registry.match("GET", "/a")).isEmpty();
    }

    @Test
    void mostSpecificPatternWins() {
        CacheRule generic = new CacheRule("/**", Set.of("GET"), 60, 100, true);
        CacheRule specific = new CacheRule("/api/products/**", Set.of("GET"), 60, 100, true);
        load(generic, specific);

        assertThat(registry.match("GET", "/api/products/1"))
                .get()
                .extracting(CacheRule::getPathPattern)
                .isEqualTo("/api/products/**");
    }

    @Test
    void noPatternMatchReturnsEmpty() {
        load(new CacheRule("/api/products/**", Set.of("GET"), 60, 100, true));

        assertThat(registry.match("GET", "/orders/1")).isEmpty();
    }
}

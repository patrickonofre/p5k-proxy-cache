package com.p5k.proxycache.rules;

import com.p5k.proxycache.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CacheRuleRepositoryIT extends AbstractPostgresIT {

    @Autowired
    CacheRuleRepository repository;

    @Test
    void persistsAndReadsBack() {
        CacheRule saved = repository.save(
                new CacheRule("/api/products/**", Set.of("GET"), 60, 1000, true));

        assertThat(saved.getId()).isNotNull();

        CacheRule found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getPathPattern()).isEqualTo("/api/products/**");
        assertThat(found.getMethods()).containsExactly("GET");
        assertThat(found.getTtlSeconds()).isEqualTo(60);
        assertThat(found.getMaxSize()).isEqualTo(1000);
        assertThat(found.isEnabled()).isTrue();
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void findAllByEnabledTrueExcludesDisabled() {
        repository.save(new CacheRule("/enabled", Set.of("GET"), 60, 1000, true));
        repository.save(new CacheRule("/disabled", Set.of("GET"), 60, 1000, false));

        assertThat(repository.findAllByEnabledTrue())
                .extracting(CacheRule::getPathPattern)
                .contains("/enabled")
                .doesNotContain("/disabled");
    }
}

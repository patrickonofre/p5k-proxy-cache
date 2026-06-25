package com.p5k.proxycache.rules;

import com.p5k.proxycache.support.AbstractPostgresIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CacheRuleRepositoryIT extends AbstractPostgresIT {

    @Autowired
    CacheRuleRepository repository;

    @Autowired
    ApplicationRepository applications;

    @BeforeEach
    void reset() {
        repository.deleteAll();
        applications.deleteAll();
    }

    private Long appId() {
        return applications.save(new Application("app", "App", "http://localhost:1", null, null, true)).getId();
    }

    @Test
    void persistsAndReadsBack() {
        Long appId = appId();
        CacheRule saved = repository.save(
                new CacheRule(appId, "/api/products/**", Set.of("GET"), 60L, 1000, true, "products"));

        assertThat(saved.getId()).isNotNull();

        CacheRule found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getApplicationId()).isEqualTo(appId);
        assertThat(found.getPathPattern()).isEqualTo("/api/products/**");
        assertThat(found.getMethods()).containsExactly("GET");
        assertThat(found.getTtlSeconds()).isEqualTo(60L);
        assertThat(found.getMaxSize()).isEqualTo(1000);
        assertThat(found.isEnabled()).isTrue();
        assertThat(found.getDescription()).isEqualTo("products");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void findAllByEnabledTrueExcludesDisabled() {
        Long appId = appId();
        repository.save(new CacheRule(appId, "/enabled", Set.of("GET"), 60L, 1000, true, null));
        repository.save(new CacheRule(appId, "/disabled", Set.of("GET"), 60L, 1000, false, null));

        assertThat(repository.findAllByEnabledTrue())
                .extracting(CacheRule::getPathPattern)
                .contains("/enabled")
                .doesNotContain("/disabled");
    }
}

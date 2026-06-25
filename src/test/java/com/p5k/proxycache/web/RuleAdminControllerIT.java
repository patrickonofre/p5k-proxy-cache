package com.p5k.proxycache.web;

import com.p5k.proxycache.rules.CacheRule;
import com.p5k.proxycache.rules.CacheRuleRepository;
import com.p5k.proxycache.rules.RuleRegistry;
import com.p5k.proxycache.support.AbstractPostgresIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RuleAdminControllerIT extends AbstractPostgresIT {

    @LocalServerPort
    int port;

    @Autowired
    CacheRuleRepository repository;

    @Autowired
    RuleRegistry registry;

    @BeforeEach
    void reset() {
        repository.deleteAll();
        registry.reload();
    }

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    @Test
    void createPersistsAndIsLiveImmediately() {
        CacheRuleRequest request = new CacheRuleRequest("/api/**", Set.of("GET"), 120, 500, true);

        Created created = client().post().uri("/admin/rules")
                .contentType(MediaType.APPLICATION_JSON).body(request)
                .exchange((req, res) -> new Created(res.getStatusCode().value(), res.bodyTo(CacheRuleResponse.class)));

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().id()).isNotNull();
        assertThat(created.body().createdAt()).isNotNull();
        assertThat(repository.findById(created.body().id())).isPresent();
        assertThat(registry.match("GET", "/api/anything")).isPresent();
    }

    @Test
    void listReturnsAllRules() {
        repository.save(new CacheRule("/a/**", Set.of("GET"), 60, 100, true));
        repository.save(new CacheRule("/b/**", Set.of("GET"), 60, 100, false));

        List<CacheRuleResponse> rules = client().get().uri("/admin/rules")
                .retrieve().body(new ParameterizedTypeReference<>() {
                });

        assertThat(rules).extracting(CacheRuleResponse::pathPattern).containsExactlyInAnyOrder("/a/**", "/b/**");
    }

    @Test
    void getByIdReturnsRuleOr404() {
        CacheRule saved = repository.save(new CacheRule("/a/**", Set.of("GET"), 60, 100, true));

        int found = client().get().uri("/admin/rules/{id}", saved.getId())
                .exchange((req, res) -> res.getStatusCode().value());
        int missing = client().get().uri("/admin/rules/{id}", 999999)
                .exchange((req, res) -> res.getStatusCode().value());

        assertThat(found).isEqualTo(200);
        assertThat(missing).isEqualTo(404);
    }

    @Test
    void updateChangesFields() {
        CacheRule saved = repository.save(new CacheRule("/old/**", Set.of("GET"), 60, 100, true));
        CacheRuleRequest request = new CacheRuleRequest("/new/**", Set.of("GET"), 300, 200, false);

        int status = client().put().uri("/admin/rules/{id}", saved.getId())
                .contentType(MediaType.APPLICATION_JSON).body(request)
                .exchange((req, res) -> res.getStatusCode().value());

        assertThat(status).isEqualTo(200);
        CacheRule reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getPathPattern()).isEqualTo("/new/**");
        assertThat(reloaded.getTtlSeconds()).isEqualTo(300);
        assertThat(reloaded.isEnabled()).isFalse();
    }

    @Test
    void deleteRemovesRuleAndReloadsRegistry() {
        CacheRule saved = repository.save(new CacheRule("/api/**", Set.of("GET"), 60, 100, true));
        registry.reload();

        int status = client().delete().uri("/admin/rules/{id}", saved.getId())
                .exchange((req, res) -> res.getStatusCode().value());

        assertThat(status).isEqualTo(204);
        assertThat(repository.findById(saved.getId())).isEmpty();
        assertThat(registry.match("GET", "/api/x")).isEmpty();
    }

    @Test
    void invalidRequestIsRejectedWith400() {
        CacheRuleRequest invalid = new CacheRuleRequest("", Set.of(), 0, 0, true);

        int status = client().post().uri("/admin/rules")
                .contentType(MediaType.APPLICATION_JSON).body(invalid)
                .exchange((req, res) -> res.getStatusCode().value());

        assertThat(status).isEqualTo(400);
        assertThat(repository.count()).isZero();
    }

    private record Created(int status, CacheRuleResponse body) {
    }
}

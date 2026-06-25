package com.p5k.proxycache.web;

import com.p5k.proxycache.rules.Application;
import com.p5k.proxycache.rules.ApplicationRepository;
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
    ApplicationRepository applications;

    @Autowired
    RuleRegistry registry;

    private Long appId;

    @BeforeEach
    void reset() {
        repository.deleteAll();
        applications.deleteAll();
        appId = applications.save(new Application("app", "App", "http://localhost:1", null, null, true)).getId();
        registry.reload();
    }

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    private CacheRule seedRule(String pattern, boolean enabled) {
        return repository.save(new CacheRule(appId, pattern, Set.of("GET"), 60L, 100, enabled, null));
    }

    @Test
    void createPersistsAndIsLiveImmediately() {
        CacheRuleRequest request = new CacheRuleRequest(appId, "/api/**", Set.of("GET"), 120L, 500, true, "items");

        Created created = client().post().uri("/admin/rules")
                .contentType(MediaType.APPLICATION_JSON).body(request)
                .exchange((req, res) -> new Created(res.getStatusCode().value(), res.bodyTo(CacheRuleResponse.class)));

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().id()).isNotNull();
        assertThat(created.body().applicationId()).isEqualTo(appId);
        assertThat(created.body().description()).isEqualTo("items");
        assertThat(created.body().createdAt()).isNotNull();
        assertThat(repository.findById(created.body().id())).isPresent();
        assertThat(registry.match(appId, "GET", "/api/anything")).isPresent();
    }

    @Test
    void listReturnsAllRules() {
        seedRule("/a/**", true);
        seedRule("/b/**", false);

        List<CacheRuleResponse> rules = client().get().uri("/admin/rules")
                .retrieve().body(new ParameterizedTypeReference<>() {
                });

        assertThat(rules).extracting(CacheRuleResponse::pathPattern).containsExactlyInAnyOrder("/a/**", "/b/**");
    }

    @Test
    void getByIdReturnsRuleOr404() {
        CacheRule saved = seedRule("/a/**", true);

        int found = client().get().uri("/admin/rules/{id}", saved.getId())
                .exchange((req, res) -> res.getStatusCode().value());
        int missing = client().get().uri("/admin/rules/{id}", 999999)
                .exchange((req, res) -> res.getStatusCode().value());

        assertThat(found).isEqualTo(200);
        assertThat(missing).isEqualTo(404);
    }

    @Test
    void updateChangesFields() {
        CacheRule saved = seedRule("/old/**", true);
        CacheRuleRequest request = new CacheRuleRequest(appId, "/new/**", Set.of("GET"), 300L, 200, false, "renamed");

        int status = client().put().uri("/admin/rules/{id}", saved.getId())
                .contentType(MediaType.APPLICATION_JSON).body(request)
                .exchange((req, res) -> res.getStatusCode().value());

        assertThat(status).isEqualTo(200);
        CacheRule reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getPathPattern()).isEqualTo("/new/**");
        assertThat(reloaded.getTtlSeconds()).isEqualTo(300L);
        assertThat(reloaded.isEnabled()).isFalse();
        assertThat(reloaded.getDescription()).isEqualTo("renamed");
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void deleteRemovesRuleAndReloadsRegistry() {
        CacheRule saved = seedRule("/api/**", true);
        registry.reload();

        int status = client().delete().uri("/admin/rules/{id}", saved.getId())
                .exchange((req, res) -> res.getStatusCode().value());

        assertThat(status).isEqualTo(204);
        assertThat(repository.findById(saved.getId())).isEmpty();
        assertThat(registry.match(appId, "GET", "/api/x")).isEmpty();
    }

    @Test
    void invalidRequestIsRejectedWith400() {
        CacheRuleRequest invalid = new CacheRuleRequest(appId, "", Set.of(), 0L, 0, true, null);

        int status = client().post().uri("/admin/rules")
                .contentType(MediaType.APPLICATION_JSON).body(invalid)
                .exchange((req, res) -> res.getStatusCode().value());

        assertThat(status).isEqualTo(400);
        assertThat(repository.count()).isZero();
    }

    @Test
    void unknownApplicationIdRejectedWith400() {
        CacheRuleRequest request = new CacheRuleRequest(999999L, "/x/**", Set.of("GET"), 60L, 100, true, null);

        int status = client().post().uri("/admin/rules")
                .contentType(MediaType.APPLICATION_JSON).body(request)
                .exchange((req, res) -> res.getStatusCode().value());

        assertThat(status).isEqualTo(400);
        assertThat(repository.count()).isZero();
    }

    @Test
    void ttlInheritsApplicationDefaultWhenOmitted() {
        Long withDefault = applications.save(
                new Application("withdef", "WithDef", "http://localhost:1", null, 90L, true)).getId();

        CacheRuleRequest inherits = new CacheRuleRequest(withDefault, "/x/**", Set.of("GET"), null, 100, true, null);
        int ok = client().post().uri("/admin/rules")
                .contentType(MediaType.APPLICATION_JSON).body(inherits)
                .exchange((req, res) -> res.getStatusCode().value());
        assertThat(ok).isEqualTo(201);

        CacheRuleRequest noTtl = new CacheRuleRequest(appId, "/y/**", Set.of("GET"), null, 100, true, null);
        int rejected = client().post().uri("/admin/rules")
                .contentType(MediaType.APPLICATION_JSON).body(noTtl)
                .exchange((req, res) -> res.getStatusCode().value());
        assertThat(rejected).isEqualTo(400);
    }

    private record Created(int status, CacheRuleResponse body) {
    }
}

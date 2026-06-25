package com.p5k.proxycache.web;

import com.p5k.proxycache.rules.Application;
import com.p5k.proxycache.rules.ApplicationRegistry;
import com.p5k.proxycache.rules.ApplicationRepository;
import com.p5k.proxycache.rules.CacheRule;
import com.p5k.proxycache.rules.CacheRuleRepository;
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

class ApplicationAdminControllerIT extends AbstractPostgresIT {

    @LocalServerPort
    int port;

    @Autowired
    ApplicationRepository applications;

    @Autowired
    CacheRuleRepository rules;

    @Autowired
    ApplicationRegistry appRegistry;

    @BeforeEach
    void reset() {
        rules.deleteAll();
        applications.deleteAll();
        appRegistry.reload();
    }

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    private static ApplicationRequest valid(String slug) {
        return new ApplicationRequest(slug, "Billing", "http://localhost:9", "billing api", 60L, true);
    }

    @Test
    void createPersistsAndIsResolvable() {
        Created created = client().post().uri("/admin/applications")
                .contentType(MediaType.APPLICATION_JSON).body(valid("billing"))
                .exchange((req, res) -> new Created(res.getStatusCode().value(),
                        res.bodyTo(ApplicationResponse.class)));

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().id()).isNotNull();
        assertThat(created.body().slug()).isEqualTo("billing");
        assertThat(created.body().createdAt()).isNotNull();
        assertThat(applications.findBySlug("billing")).isPresent();
        assertThat(appRegistry.resolve("billing")).isPresent();
    }

    @Test
    void listReturnsAll() {
        applications.save(new Application("a", "A", "http://localhost:1", null, null, true));
        applications.save(new Application("b", "B", "http://localhost:2", null, null, false));

        List<ApplicationResponse> apps = client().get().uri("/admin/applications")
                .retrieve().body(new ParameterizedTypeReference<>() {
                });

        assertThat(apps).extracting(ApplicationResponse::slug).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void getByIdReturnsAppOr404() {
        Application saved = applications.save(new Application("a", "A", "http://localhost:1", null, null, true));

        int found = client().get().uri("/admin/applications/{id}", saved.getId())
                .exchange((req, res) -> res.getStatusCode().value());
        int missing = client().get().uri("/admin/applications/{id}", 999999)
                .exchange((req, res) -> res.getStatusCode().value());

        assertThat(found).isEqualTo(200);
        assertThat(missing).isEqualTo(404);
    }

    @Test
    void updateChangesFieldsAndSetsUpdatedAt() {
        Application saved = applications.save(new Application("a", "A", "http://localhost:1", null, null, true));
        ApplicationRequest request = new ApplicationRequest("a", "Renamed", "http://localhost:2", "now", 30L, false);

        int status = client().put().uri("/admin/applications/{id}", saved.getId())
                .contentType(MediaType.APPLICATION_JSON).body(request)
                .exchange((req, res) -> res.getStatusCode().value());

        assertThat(status).isEqualTo(200);
        Application reloaded = applications.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Renamed");
        assertThat(reloaded.getBaseUrl()).isEqualTo("http://localhost:2");
        assertThat(reloaded.getDefaultTtlSeconds()).isEqualTo(30L);
        assertThat(reloaded.isEnabled()).isFalse();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void deleteCascadesRulesAndReturns204() {
        Application saved = applications.save(new Application("a", "A", "http://localhost:1", null, null, true));
        rules.save(new CacheRule(saved.getId(), "/x/**", Set.of("GET"), 60L, 100, true, null));

        int status = client().delete().uri("/admin/applications/{id}", saved.getId())
                .exchange((req, res) -> res.getStatusCode().value());

        assertThat(status).isEqualTo(204);
        assertThat(applications.findById(saved.getId())).isEmpty();
        assertThat(rules.count()).isZero(); // FK ON DELETE CASCADE
    }

    @Test
    void deleteMissingReturns404() {
        int status = client().delete().uri("/admin/applications/{id}", 999999)
                .exchange((req, res) -> res.getStatusCode().value());

        assertThat(status).isEqualTo(404);
    }

    @Test
    void invalidRequestRejectedWith400() {
        ApplicationRequest badSlug = new ApplicationRequest("Bad Slug", "A", "http://localhost:1", null, null, true);

        int status = client().post().uri("/admin/applications")
                .contentType(MediaType.APPLICATION_JSON).body(badSlug)
                .exchange((req, res) -> res.getStatusCode().value());

        assertThat(status).isEqualTo(400);
        assertThat(applications.count()).isZero();
    }

    @Test
    void duplicateSlugReturns409() {
        applications.save(new Application("dup", "A", "http://localhost:1", null, null, true));

        int status = client().post().uri("/admin/applications")
                .contentType(MediaType.APPLICATION_JSON).body(valid("dup"))
                .exchange((req, res) -> res.getStatusCode().value());

        assertThat(status).isEqualTo(409);
    }

    private record Created(int status, ApplicationResponse body) {
    }
}

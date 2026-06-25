package com.p5k.proxycache.rules;

import com.p5k.proxycache.cache.CaffeineCacheRegistry;
import com.p5k.proxycache.web.ApplicationRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/**
 * Runtime management of applications. Mutations reload {@link ApplicationRegistry} (and
 * {@link RuleRegistry} when rule visibility may change). Deleting an application cascades to
 * its rules (DB FK) and evicts their Caffeine caches. Slugs are unique (409 on conflict).
 */
@Service
public class ApplicationAdminService {

    private final ApplicationRepository applications;
    private final CacheRuleRepository rules;
    private final ApplicationRegistry appRegistry;
    private final RuleRegistry ruleRegistry;
    private final CaffeineCacheRegistry caches;

    public ApplicationAdminService(ApplicationRepository applications, CacheRuleRepository rules,
                                   ApplicationRegistry appRegistry, RuleRegistry ruleRegistry,
                                   CaffeineCacheRegistry caches) {
        this.applications = applications;
        this.rules = rules;
        this.appRegistry = appRegistry;
        this.ruleRegistry = ruleRegistry;
        this.caches = caches;
    }

    @Transactional
    public Application create(ApplicationRequest request) {
        if (applications.existsBySlug(request.slug())) {
            throw conflict(request.slug());
        }
        Application saved = applications.save(new Application(request.slug(), request.name(),
                request.baseUrl(), request.description(), request.defaultTtlSeconds(), request.enabled()));
        appRegistry.reload();
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Application> list() {
        return applications.findAll();
    }

    @Transactional(readOnly = true)
    public Application get(Long id) {
        return applications.findById(id).orElseThrow(() -> notFound(id));
    }

    @Transactional
    public Application update(Long id, ApplicationRequest request) {
        Application app = applications.findById(id).orElseThrow(() -> notFound(id));
        if (!app.getSlug().equals(request.slug()) && applications.existsBySlug(request.slug())) {
            throw conflict(request.slug());
        }
        app.setSlug(request.slug());
        app.setName(request.name());
        app.setBaseUrl(request.baseUrl());
        app.setDescription(request.description());
        app.setDefaultTtlSeconds(request.defaultTtlSeconds());
        app.setEnabled(request.enabled());
        app.setUpdatedAt(Instant.now());
        Application saved = applications.save(app);
        // base_url / default_ttl / enabled changes affect routing and effective TTL → drop stale caches.
        evictAppRuleCaches(id);
        appRegistry.reload();
        ruleRegistry.reload();
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        if (!applications.existsById(id)) {
            throw notFound(id);
        }
        evictAppRuleCaches(id);
        applications.deleteById(id); // FK ON DELETE CASCADE removes the app's rules
        appRegistry.reload();
        ruleRegistry.reload();
    }

    private void evictAppRuleCaches(Long applicationId) {
        rules.findByApplicationId(applicationId).forEach(rule -> caches.evictRule(rule.getId()));
    }

    private ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found: " + id);
    }

    private ResponseStatusException conflict(String slug) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Application slug already exists: " + slug);
    }
}

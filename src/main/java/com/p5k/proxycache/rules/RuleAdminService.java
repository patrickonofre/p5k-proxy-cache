package com.p5k.proxycache.rules;

import com.p5k.proxycache.cache.CaffeineCacheRegistry;
import com.p5k.proxycache.web.CacheRuleRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/**
 * Runtime management of cache rules. Every mutation reloads {@link RuleRegistry} and
 * evicts the affected rule's Caffeine cache so changes take effect immediately and no
 * stale entries are served. Each rule must reference an existing {@link Application} and
 * resolve to a positive effective TTL.
 */
@Service
public class RuleAdminService {

    private final CacheRuleRepository repository;
    private final ApplicationRepository applications;
    private final RuleRegistry registry;
    private final CaffeineCacheRegistry caches;

    public RuleAdminService(CacheRuleRepository repository, ApplicationRepository applications,
                            RuleRegistry registry, CaffeineCacheRegistry caches) {
        this.repository = repository;
        this.applications = applications;
        this.registry = registry;
        this.caches = caches;
    }

    @Transactional
    public CacheRule create(CacheRuleRequest request) {
        Application app = requireApplication(request.applicationId());
        requireEffectiveTtl(request.ttlSeconds(), app);
        CacheRule saved = repository.save(new CacheRule(app.getId(), request.pathPattern(), request.methods(),
                request.ttlSeconds(), request.maxSize(), request.enabled(), request.description()));
        registry.reload();
        return saved;
    }

    @Transactional(readOnly = true)
    public List<CacheRule> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public CacheRule get(Long id) {
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    @Transactional
    public CacheRule update(Long id, CacheRuleRequest request) {
        CacheRule rule = repository.findById(id).orElseThrow(() -> notFound(id));
        Application app = requireApplication(request.applicationId());
        requireEffectiveTtl(request.ttlSeconds(), app);
        rule.setApplicationId(app.getId());
        rule.setPathPattern(request.pathPattern());
        rule.setMethods(request.methods());
        rule.setTtlSeconds(request.ttlSeconds());
        rule.setMaxSize(request.maxSize());
        rule.setEnabled(request.enabled());
        rule.setDescription(request.description());
        rule.setUpdatedAt(Instant.now());
        CacheRule saved = repository.save(rule);
        caches.evictRule(id);
        registry.reload();
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw notFound(id);
        }
        repository.deleteById(id);
        caches.evictRule(id);
        registry.reload();
    }

    private Application requireApplication(Long applicationId) {
        return applications.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown applicationId: " + applicationId));
    }

    private void requireEffectiveTtl(Long ruleTtlSeconds, Application app) {
        if (EffectiveTtl.resolve(ruleTtlSeconds, app.getDefaultTtlSeconds()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ttlSeconds is required when the application has no default_ttl_seconds");
        }
    }

    private ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Cache rule not found: " + id);
    }
}

package com.p5k.proxycache.rules;

import com.p5k.proxycache.cache.CaffeineCacheRegistry;
import com.p5k.proxycache.web.CacheRuleRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Runtime management of cache rules. Every mutation reloads {@link RuleRegistry} and
 * evicts the affected rule's Caffeine cache so changes take effect immediately and no
 * stale entries are served.
 */
@Service
public class RuleAdminService {

    private final CacheRuleRepository repository;
    private final RuleRegistry registry;
    private final CaffeineCacheRegistry caches;

    public RuleAdminService(CacheRuleRepository repository, RuleRegistry registry, CaffeineCacheRegistry caches) {
        this.repository = repository;
        this.registry = registry;
        this.caches = caches;
    }

    @Transactional
    public CacheRule create(CacheRuleRequest request) {
        CacheRule saved = repository.save(new CacheRule(request.pathPattern(), request.methods(),
                request.ttlSeconds(), request.maxSize(), request.enabled()));
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
        rule.setPathPattern(request.pathPattern());
        rule.setMethods(request.methods());
        rule.setTtlSeconds(request.ttlSeconds());
        rule.setMaxSize(request.maxSize());
        rule.setEnabled(request.enabled());
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

    private ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Cache rule not found: " + id);
    }
}

package com.p5k.proxycache.rules;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * In-memory snapshot of enabled cache rules. Matches a request to the most-specific
 * enabled GET rule. {@link #reload()} swaps the snapshot atomically.
 */
@Component
public class RuleRegistry {

    private final CacheRuleRepository repository;
    private final AntPathMatcher matcher = new AntPathMatcher();
    private volatile List<CacheRule> snapshot = List.of();

    public RuleRegistry(CacheRuleRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void reload() {
        this.snapshot = List.copyOf(repository.findAllByEnabledTrue());
    }

    public Optional<CacheRule> match(String method, String path) {
        if (method == null || !method.equalsIgnoreCase("GET")) {
            return Optional.empty();
        }
        Comparator<String> bySpecificity = matcher.getPatternComparator(path);
        return snapshot.stream()
                .filter(CacheRule::isEnabled)
                .filter(rule -> ruleAllowsGet(rule))
                .filter(rule -> matcher.match(rule.getPathPattern(), path))
                .min((a, b) -> bySpecificity.compare(a.getPathPattern(), b.getPathPattern()));
    }

    private boolean ruleAllowsGet(CacheRule rule) {
        return rule.getMethods().stream().anyMatch(m -> m.equalsIgnoreCase("GET"));
    }
}

package com.p5k.proxycache.rules;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * In-memory snapshot of enabled applications, keyed by slug. {@link #reload()} swaps the
 * snapshot atomically. {@link #resolve(String)} maps a request's slug to its application.
 */
@Component
public class ApplicationRegistry {

    private final ApplicationRepository repository;
    private volatile Map<String, Application> bySlug = Map.of();

    public ApplicationRegistry(ApplicationRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void reload() {
        this.bySlug = repository.findAllByEnabledTrue().stream()
                .collect(Collectors.toUnmodifiableMap(Application::getSlug, Function.identity()));
    }

    public Optional<Application> resolve(String slug) {
        if (slug == null || slug.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(bySlug.get(slug));
    }
}

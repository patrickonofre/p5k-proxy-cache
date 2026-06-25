package com.p5k.proxycache.rules;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Set;

/**
 * A cache rule: which requests are cacheable and how. Persisted in {@code cache_rule}.
 */
@Entity
@Table(name = "cache_rule")
public class CacheRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "path_pattern", nullable = false)
    private String pathPattern;

    @Convert(converter = StringSetConverter.class)
    @Column(name = "methods", nullable = false)
    private Set<String> methods;

    @Column(name = "ttl_seconds", nullable = false)
    private long ttlSeconds;

    @Column(name = "max_size", nullable = false)
    private long maxSize;

    @Column(nullable = false)
    private boolean enabled;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CacheRule() {
        // for JPA
    }

    public CacheRule(String pathPattern, Set<String> methods, long ttlSeconds, long maxSize, boolean enabled) {
        this.pathPattern = pathPattern;
        this.methods = methods;
        this.ttlSeconds = ttlSeconds;
        this.maxSize = maxSize;
        this.enabled = enabled;
    }

    public CacheRule(Long id, String pathPattern, Set<String> methods, long ttlSeconds, long maxSize, boolean enabled) {
        this(pathPattern, methods, ttlSeconds, maxSize, enabled);
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public String getPathPattern() {
        return pathPattern;
    }

    public void setPathPattern(String pathPattern) {
        this.pathPattern = pathPattern;
    }

    public Set<String> getMethods() {
        return methods;
    }

    public void setMethods(Set<String> methods) {
        this.methods = methods;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public long getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(long maxSize) {
        this.maxSize = maxSize;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

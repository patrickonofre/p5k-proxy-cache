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
 * A cache rule: which requests are cacheable and how, for one {@link Application}. The
 * {@code pathPattern} is backend-relative (the app slug prefix is stripped before matching).
 * {@code ttlSeconds} is optional — when null the application's {@code defaultTtlSeconds} applies.
 * Persisted in {@code cache_rule}.
 */
@Entity
@Table(name = "cache_rule")
public class CacheRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "path_pattern", nullable = false)
    private String pathPattern;

    @Convert(converter = StringSetConverter.class)
    @Column(name = "methods", nullable = false)
    private Set<String> methods;

    @Column(name = "ttl_seconds")
    private Long ttlSeconds;

    @Column(name = "max_size", nullable = false)
    private long maxSize;

    @Column(nullable = false)
    private boolean enabled;

    @Column
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected CacheRule() {
        // for JPA
    }

    public CacheRule(Long applicationId, String pathPattern, Set<String> methods, Long ttlSeconds,
                     long maxSize, boolean enabled, String description) {
        this.applicationId = applicationId;
        this.pathPattern = pathPattern;
        this.methods = methods;
        this.ttlSeconds = ttlSeconds;
        this.maxSize = maxSize;
        this.enabled = enabled;
        this.description = description;
    }

    public CacheRule(Long id, Long applicationId, String pathPattern, Set<String> methods, Long ttlSeconds,
                     long maxSize, boolean enabled, String description) {
        this(applicationId, pathPattern, methods, ttlSeconds, maxSize, enabled, description);
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
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

    public Long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(Long ttlSeconds) {
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

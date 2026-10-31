package com.example.gateway.apis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/** JPA entity for the apis table. */
@Entity
@Table(name = "apis")
public class Api {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "slug", length = 50, nullable = false, unique = true)
    private String slug;

    @Column(name = "upstream_base_url", length = 500, nullable = false)
    private String upstreamBaseUrl;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", columnDefinition = "DATETIME(3)", nullable = false)
    private Instant createdAt;

    protected Api() {
        // For JPA.
    }

    public Api(Long id, Long ownerId, String name, String slug, String upstreamBaseUrl,
               int timeoutMs, boolean enabled, Instant createdAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.slug = slug;
        this.upstreamBaseUrl = upstreamBaseUrl;
        this.timeoutMs = timeoutMs;
        this.enabled = enabled;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getUpstreamBaseUrl() { return upstreamBaseUrl; }
    public int getTimeoutMs() { return timeoutMs; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setUpstreamBaseUrl(String upstreamBaseUrl) { this.upstreamBaseUrl = upstreamBaseUrl; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Api api)) return false;
        // Entity equality is by identity (id). New (unsaved) entities are only
        // equal to themselves.
        if (id == null) return false;
        return Objects.equals(id, api.id);
    }

    @Override
    public int hashCode() {
        // Constant while transient; JPA-friendly once persisted (id-based).
        return id == null ? System.identityHashCode(this) : Objects.hash(id);
    }
}

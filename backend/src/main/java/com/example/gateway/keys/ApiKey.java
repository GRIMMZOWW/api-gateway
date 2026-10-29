package com.example.gateway.keys;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity for the {@code api_keys} table (Flyway V1 schema).
 *
 * <p>The full key is never persisted: only {@code key_prefix} (for display,
 * e.g. {@code "gk_ab12cd34..."}) and {@code key_hash} (SHA-256 hex, for lookup)
 * are stored. {@code ddl-auto=validate} checks this mapping against the Flyway
 * schema on startup, so every {@link Instant} column declares
 * {@code columnDefinition="DATETIME(3)"} and {@code key_hash} declares
 * {@code CHAR(64)} to match the schema exactly.
 */
@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "api_id", nullable = false)
    private Long apiId;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "key_prefix", length = 16, nullable = false)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, unique = true, columnDefinition = "CHAR(64)")
    private String keyHash;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "refill_per_sec", nullable = false, precision = 8, scale = 2)
    private BigDecimal refillPerSec;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ApiKeyStatus status;

    @Column(name = "expires_at", columnDefinition = "DATETIME(3)")
    private Instant expiresAt;

    @Column(name = "last_used_at", columnDefinition = "DATETIME(3)")
    private Instant lastUsedAt;

    @Column(name = "created_at", columnDefinition = "DATETIME(3)", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at", columnDefinition = "DATETIME(3)")
    private Instant revokedAt;

    protected ApiKey() {
        // For JPA.
    }

    public ApiKey(Long id, Long apiId, String name, String keyPrefix, String keyHash,
                  int capacity, BigDecimal refillPerSec, ApiKeyStatus status,
                  Instant expiresAt, Instant lastUsedAt, Instant createdAt, Instant revokedAt) {
        this.id = id;
        this.apiId = apiId;
        this.name = name;
        this.keyPrefix = keyPrefix;
        this.keyHash = keyHash;
        this.capacity = capacity;
        this.refillPerSec = refillPerSec;
        this.status = status;
        this.expiresAt = expiresAt;
        this.lastUsedAt = lastUsedAt;
        this.createdAt = createdAt;
        this.revokedAt = revokedAt;
    }

    public Long getId() { return id; }
    public Long getApiId() { return apiId; }
    public String getName() { return name; }
    public String getKeyPrefix() { return keyPrefix; }
    public String getKeyHash() { return keyHash; }
    public int getCapacity() { return capacity; }
    public BigDecimal getRefillPerSec() { return refillPerSec; }
    public ApiKeyStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRevokedAt() { return revokedAt; }

    public void setName(String name) { this.name = name; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public void setRefillPerSec(BigDecimal refillPerSec) { this.refillPerSec = refillPerSec; }
    public void setStatus(ApiKeyStatus status) { this.status = status; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApiKey other)) return false;
        // Identity is by PK only; transient entities (null id) are never equal.
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

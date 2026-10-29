package com.example.gateway.keys;

import com.example.gateway.apis.Api;
import com.example.gateway.keys.ApiKey;
import com.example.gateway.keys.ApiKeyStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The cached view of an API key plus the API it belongs to, keyed by {@code key_hash} in
 * the {@link KeyLookupService} Caffeine cache (spec core logic #3). One lookup per gateway
 * request instead of a key query plus an API query.
 */
public record CachedKey(
    Long keyId,
    Long apiId,
    boolean active,
    Instant expiresAt,
    int capacity,
    BigDecimal refillPerSec,
    String slug,
    String upstreamBaseUrl,
    int timeoutMs,
    boolean apiEnabled
) {

    /** Builds the cached view from the key row and its parent API row. */
    public static CachedKey of(ApiKey key, Api api) {
        return new CachedKey(
            key.getId(),
            key.getApiId(),
            key.getStatus() == ApiKeyStatus.ACTIVE,
            key.getExpiresAt(),
            key.getCapacity(),
            key.getRefillPerSec(),
            api.getSlug(),
            api.getUpstreamBaseUrl(),
            api.getTimeoutMs(),
            api.isEnabled());
    }
}

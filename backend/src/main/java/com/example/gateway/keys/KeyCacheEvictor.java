package com.example.gateway.keys;

/**
 * Hook for evicting cached key lookups when a key changes. The real Caffeine-backed
 * implementation arrives in Phase 5; until then no implementation is registered,
 * so {@link KeyService} is wired with an empty {@link java.util.Optional} and
 * the eviction calls are no-ops.
 *
 * <p>Implemented by the cache layer, called by the key service on:
 * <ul>
 *   <li>revoke — the key must immediately stop authenticating requests.</li>
 *   <li>PATCH that touches {@code capacity}/{@code refillPerSec} — the cached
 *       rate-limit entry must reload the new limits.</li>
 *   <li>API delete — every key under the API is gone (not invoked from this phase).</li>
 * </ul>
 */
public interface KeyCacheEvictor {

    /**
     * Drop the cached entry for a single key (looked up by its hash).
     */
    void evictKey(String keyHash);

    /**
     * Drop every cached entry whose key belongs to the given API.
     */
    void evictApi(Long apiId);
}

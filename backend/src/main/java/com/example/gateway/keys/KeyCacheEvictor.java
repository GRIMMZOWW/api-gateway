package com.example.gateway.keys;

/** Hook for evicting cached key lookups when a key changes. */
public interface KeyCacheEvictor {

    /** Drop the cached entry for a single key (looked up by its hash). */
    void evictKey(String keyHash);

    /** Drop every cached entry whose key belongs to the given API. */
    void evictApi(Long apiId);
}

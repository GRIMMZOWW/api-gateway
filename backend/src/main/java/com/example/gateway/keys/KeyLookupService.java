package com.example.gateway.keys;

import com.example.gateway.apis.Api;
import com.example.gateway.apis.ApiRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Caffeine-backed lookup of API keys by their SHA-256 hash. */
@Component
public class KeyLookupService implements KeyCacheEvictor {

    private final ApiKeyRepository apiKeyRepository;
    private final ApiRepository apiRepository;
    private final Cache<String, CachedKey> cache;

    public KeyLookupService(ApiKeyRepository apiKeyRepository, ApiRepository apiRepository,
                            com.example.gateway.common.GatewayProperties properties) {
        this.apiKeyRepository = apiKeyRepository;
        this.apiRepository = apiRepository;
        this.cache = Caffeine.newBuilder()
            .maximumSize(properties.cacheMaxEntries())
            .expireAfterWrite(properties.cacheTtlSeconds(), TimeUnit.SECONDS)
            .build();
    }

    public Optional<CachedKey> find(String keyHash) {
        CachedKey cached = cache.getIfPresent(keyHash);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<ApiKey> keyOpt = apiKeyRepository.findByKeyHash(keyHash);
        if (keyOpt.isEmpty()) {
            // Do not cache misses.
            return Optional.empty();
        }
        ApiKey key = keyOpt.get();
        Api api = apiRepository.findById(key.getApiId()).orElse(null);
        if (api == null) {
            // Key exists but its API was deleted (cascade should have removed the key too;
            // treat as invalid just in case).
            return Optional.empty();
        }
        CachedKey view = CachedKey.of(key, api);
        cache.put(keyHash, view);
        return Optional.of(view);
    }

    @Override
    public void evictKey(String keyHash) {
        cache.invalidate(keyHash);
    }

    @Override
    public void evictApi(Long apiId) {
        // No reverse index; iterate the (bounded) cache. Fine for <= 10,000 entries.
        cache.asMap().entrySet().removeIf(e -> apiId.equals(e.getValue().apiId()));
    }
}

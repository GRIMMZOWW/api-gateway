package com.example.gateway.keys;

import com.example.gateway.apis.ApiService;
import com.example.gateway.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Application service for API keys. */
@Service
public class KeyService {

    /** At most this many active keys per API. */
    static final int MAX_ACTIVE_KEYS_PER_API = 20;

    private static final int DEFAULT_CAPACITY = 10;
    private static final BigDecimal DEFAULT_REFILL = new BigDecimal("5.00");
    private static final int MIN_CAPACITY = 1;
    private static final int MAX_CAPACITY = 1000;
    private static final BigDecimal MIN_REFILL = new BigDecimal("0.10");
    private static final BigDecimal MAX_REFILL = new BigDecimal("1000");

    private final ApiKeyRepository apiKeyRepository;
    private final KeyGenerator keyGenerator;
    private final ApiService apiService;
    private final Optional<KeyCacheEvictor> cacheEvictor;

    public KeyService(ApiKeyRepository apiKeyRepository, KeyGenerator keyGenerator,
                      ApiService apiService, Optional<KeyCacheEvictor> cacheEvictor) {
        this.apiKeyRepository = apiKeyRepository;
        this.keyGenerator = keyGenerator;
        this.apiService = apiService;
        this.cacheEvictor = cacheEvictor;
    }

    /** Create a new key under apiId. */
    @Transactional
    public CreateKeyResponse create(Long userId, Long apiId, CreateKeyRequest request) {
        // Scope: throws NOT_FOUND if the caller does not own the API.
        apiService.getOwnedApi(userId, apiId);

        int capacity = request.capacity() != null ? request.capacity() : DEFAULT_CAPACITY;
        BigDecimal refill = request.refillPerSec() != null ? request.refillPerSec() : DEFAULT_REFILL;
        validateLimits(capacity, refill);

        long activeCount = apiKeyRepository.countByApiIdAndStatus(apiId, ApiKeyStatus.ACTIVE);
        if (activeCount >= MAX_ACTIVE_KEYS_PER_API) {
            throw new ApiException("KEY_LIMIT_REACHED",
                "This API already has " + MAX_ACTIVE_KEYS_PER_API + " active keys.",
                HttpStatus.BAD_REQUEST);
        }

        KeyGenerator.GeneratedKey generated = keyGenerator.generate();
        Instant now = Instant.now();
        ApiKey entity = new ApiKey(null, apiId, request.name(), generated.prefix(), generated.hash(),
            capacity, refill, ApiKeyStatus.ACTIVE, request.expiresAt(), null, now, null);
        entity = apiKeyRepository.save(entity);
        return new CreateKeyResponse(toResponse(entity), generated.fullKey());
    }

    /** List all keys under apiId. */
    @Transactional(readOnly = true)
    public List<KeyResponse> list(Long userId, Long apiId) {
        apiService.getOwnedApi(userId, apiId);
        return apiKeyRepository.findByApiId(apiId).stream()
            .map(this::toResponse)
            .toList();
    }

    /** Partial update of name/capacity/refillPerSec. */
    @Transactional
    public KeyResponse update(Long userId, Long keyId, UpdateKeyRequest request) {
        ApiKey key = mustOwnKey(userId, keyId);

        Map<String, String> fields = new LinkedHashMap<>();
        boolean limitsTouched = false;

        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty() || name.length() > 100) {
                fields.put("name", "name must be between 1 and 100 characters.");
            } else {
                key.setName(name);
            }
        }
        if (request.capacity() != null) {
            if (request.capacity() < MIN_CAPACITY || request.capacity() > MAX_CAPACITY) {
                fields.put("capacity", "capacity must be between " + MIN_CAPACITY + " and " + MAX_CAPACITY + ".");
            } else {
                key.setCapacity(request.capacity());
                limitsTouched = true;
            }
        }
        if (request.refillPerSec() != null) {
            BigDecimal refill = request.refillPerSec();
            if (refill.compareTo(MIN_REFILL) < 0 || refill.compareTo(MAX_REFILL) > 0) {
                fields.put("refillPerSec", "refillPerSec must be between " + MIN_REFILL + " and " + MAX_REFILL + ".");
            } else {
                key.setRefillPerSec(refill);
                limitsTouched = true;
            }
        }

        if (!fields.isEmpty()) {
            throw new ApiException("VALIDATION_ERROR", "Request validation failed.",
                HttpStatus.BAD_REQUEST, fields);
        }

        ApiKey saved = apiKeyRepository.save(key);

        // Evict so any cached rate-limit entry reloads the new capacity/refill.
        if (limitsTouched) {
            String keyHash = saved.getKeyHash();
            cacheEvictor.ifPresent(e -> e.evictKey(keyHash));
        }
        return toResponse(saved);
    }

    /** Permanently revoke the key. */
    @Transactional
    public void revoke(Long userId, Long keyId) {
        ApiKey key = mustOwnKey(userId, keyId);
        key.setStatus(ApiKeyStatus.REVOKED);
        key.setRevokedAt(Instant.now());
        apiKeyRepository.save(key);
        cacheEvictor.ifPresent(e -> e.evictKey(key.getKeyHash()));
    }

    /** Load a key by id and verify the caller owns its parent API. */
    private ApiKey mustOwnKey(Long userId, Long keyId) {
        ApiKey key = apiKeyRepository.findById(keyId)
            .orElseThrow(this::notFound);
        // Scope via the parent API; throws NOT_FOUND if the caller does not own it.
        apiService.getOwnedApi(userId, key.getApiId());
        return key;
    }

    private void validateLimits(int capacity, BigDecimal refill) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (capacity < MIN_CAPACITY || capacity > MAX_CAPACITY) {
            fields.put("capacity", "capacity must be between " + MIN_CAPACITY + " and " + MAX_CAPACITY + ".");
        }
        if (refill.compareTo(MIN_REFILL) < 0 || refill.compareTo(MAX_REFILL) > 0) {
            fields.put("refillPerSec", "refillPerSec must be between " + MIN_REFILL + " and " + MAX_REFILL + ".");
        }
        if (!fields.isEmpty()) {
            throw new ApiException("VALIDATION_ERROR", "Request validation failed.",
                HttpStatus.BAD_REQUEST, fields);
        }
    }

    private ApiException notFound() {
        return new ApiException("NOT_FOUND", "API key not found.", HttpStatus.NOT_FOUND);
    }

    private KeyResponse toResponse(ApiKey key) {
        return new KeyResponse(key.getId(), key.getName(), key.getKeyPrefix(),
            key.getCapacity(), key.getRefillPerSec(), key.getStatus().name(),
            key.getExpiresAt(), key.getLastUsedAt(), key.getCreatedAt());
    }
}

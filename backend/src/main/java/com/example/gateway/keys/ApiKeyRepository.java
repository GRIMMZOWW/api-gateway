package com.example.gateway.keys;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link ApiKey}.
 *
 * <ul>
 *   <li>{@link #findByKeyHash(String)} — used by the gateway (Phase 4) on every
 *       authenticated request to resolve the key.</li>
 *   <li>{@link #findByApiId(Long)} / {@link #findByApiIdAndStatus(Long, ApiKeyStatus)}
 *       — back the list endpoint.</li>
 *   <li>{@link #countByApiIdAndStatus(Long, ApiKeyStatus)} — backs the
 *       20-active-keys-per-API cap.</li>
 * </ul>
 */
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findByApiId(Long apiId);

    List<ApiKey> findByApiIdAndStatus(Long apiId, ApiKeyStatus status);

    long countByApiIdAndStatus(Long apiId, ApiKeyStatus status);
}

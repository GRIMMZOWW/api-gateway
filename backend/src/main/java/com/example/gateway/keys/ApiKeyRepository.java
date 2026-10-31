package com.example.gateway.keys;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** JPA repository for ApiKey. */
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findByApiId(Long apiId);

    List<ApiKey> findByApiIdAndStatus(Long apiId, ApiKeyStatus status);

    long countByApiIdAndStatus(Long apiId, ApiKeyStatus status);
}

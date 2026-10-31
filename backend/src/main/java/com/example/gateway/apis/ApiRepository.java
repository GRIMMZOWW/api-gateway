package com.example.gateway.apis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Repository for Api. */
public interface ApiRepository extends JpaRepository<Api, Long> {

    Optional<Api> findBySlug(String slug);

    List<Api> findByOwnerId(Long ownerId);

    boolean existsBySlug(String slug);

    long countByOwnerId(Long ownerId);
}

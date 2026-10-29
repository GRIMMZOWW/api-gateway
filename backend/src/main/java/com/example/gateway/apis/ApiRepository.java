package com.example.gateway.apis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Api}. All query methods are derived queries so no
 * {@code @Query} is needed.
 *
 * <ul>
 *   <li>{@link #findBySlug(String)} — uniqueness is enforced by the DB
 *       ({@code uk_apis_slug}); the method returns {@code Optional} for safety.</li>
 *   <li>{@link #findByOwnerId(Long)} — backed by {@code idx_apis_owner}.</li>
 *   <li>{@link #existsBySlug(String)} — used for create-time uniqueness check.</li>
 *   <li>{@link #countByOwnerId(Long)} — used for the per-user API limit
 *       (spec security requirement #9: max 10 APIs per user).</li>
 * </ul>
 */
public interface ApiRepository extends JpaRepository<Api, Long> {

    Optional<Api> findBySlug(String slug);

    List<Api> findByOwnerId(Long ownerId);

    boolean existsBySlug(String slug);

    long countByOwnerId(Long ownerId);
}

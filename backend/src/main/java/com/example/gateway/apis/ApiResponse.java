package com.example.gateway.apis;

import java.time.Instant;

/**
 * API resource as returned by the REST endpoints. Excludes {@code ownerId} so a
 * caller never sees another user's id. {@code createdAt} is serialized as an
 * ISO-8601 instant by Spring Boot's default Jackson config.
 */
public record ApiResponse(
    Long id,
    String name,
    String slug,
    String upstreamBaseUrl,
    int timeoutMs,
    boolean enabled,
    Instant createdAt
) {}

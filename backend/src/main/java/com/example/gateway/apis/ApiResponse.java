package com.example.gateway.apis;

import java.time.Instant;

/** API resource as returned by the REST endpoints. */
public record ApiResponse(
    Long id,
    String name,
    String slug,
    String upstreamBaseUrl,
    int timeoutMs,
    boolean enabled,
    Instant createdAt
) {}

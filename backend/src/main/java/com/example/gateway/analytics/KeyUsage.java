package com.example.gateway.analytics;

import java.time.Instant;

/** Per-API-key usage rollup over a time range. */
public record KeyUsage(
    Long keyId,
    String name,
    String prefix,
    long total,
    long rateLimited,
    Instant lastUsedAt
) {
}

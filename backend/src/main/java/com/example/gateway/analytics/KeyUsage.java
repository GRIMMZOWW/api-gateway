package com.example.gateway.analytics;

import java.time.Instant;

/**
 * Per-API-key usage rollup over a time range. Only keys that were actually used in the
 * range appear (rows with a {@code NULL api_key_id} are skipped, per spec).
 *
 * @param keyId       {@code api_keys.id}.
 * @param name        {@code api_keys.name}.
 * @param prefix      {@code api_keys.key_prefix}.
 * @param total       total requests for the key in the range.
 * @param rateLimited requests for the key with {@code outcome = RATE_LIMITED}.
 * @param lastUsedAt  most recent {@code usage_logs.created_at} for the key within the range.
 */
public record KeyUsage(
    Long keyId,
    String name,
    String prefix,
    long total,
    long rateLimited,
    Instant lastUsedAt
) {
}

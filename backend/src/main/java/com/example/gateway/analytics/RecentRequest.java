package com.example.gateway.analytics;

import java.time.Instant;

/**
 * The newest usage_log rows for an API over a time range (newest first).
 *
 * @param id         {@code usage_logs.id}.
 * @param method     HTTP method.
 * @param path       request path.
 * @param statusCode upstream response status code.
 * @param outcome    one of SUCCESS, RATE_LIMITED, UNAUTHORIZED, UPSTREAM_ERROR,
 *                   UPSTREAM_TIMEOUT, REJECTED.
 * @param latencyMs  request latency in milliseconds.
 * @param createdAt  {@code usage_logs.created_at} (UTC).
 * @param keyId      {@code usage_logs.api_key_id} (nullable when no key was used).
 */
public record RecentRequest(
    Long id,
    String method,
    String path,
    int statusCode,
    String outcome,
    int latencyMs,
    Instant createdAt,
    Long keyId
) {
}

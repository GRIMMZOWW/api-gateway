package com.example.gateway.analytics;

import java.time.Instant;

/** The newest usage_log rows for an API over a time range (newest first). */
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

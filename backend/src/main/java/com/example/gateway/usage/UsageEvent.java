package com.example.gateway.usage;

import java.time.Instant;

/**
 * An in-memory record of one gateway request, queued for batch insertion into
 * {@code usage_logs} by {@link AsyncUsageRecorder}. Created on the request thread,
 * written to the database by a single background thread.
 */
public record UsageEvent(
    Long apiId,
    Long apiKeyId,
    String requestId,
    String method,
    String path,
    int statusCode,
    int latencyMs,
    UsageOutcome outcome,
    Instant createdAt
) {}

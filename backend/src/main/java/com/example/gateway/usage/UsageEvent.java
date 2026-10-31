package com.example.gateway.usage;

import java.time.Instant;

/** An in-memory record of one gateway request, queued for batch insertion into usage_logs by AsyncUsageRecorder. */
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

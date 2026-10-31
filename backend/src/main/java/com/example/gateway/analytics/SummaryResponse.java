package com.example.gateway.analytics;

/** Aggregate counts for one API over a time range. */
public record SummaryResponse(
    long totalRequests,
    long successCount,
    long rateLimitedCount,
    long unauthorizedCount,
    long errorCount,
    double successRate,
    double avgLatencyMs
) {
}

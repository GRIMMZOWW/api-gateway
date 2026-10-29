package com.example.gateway.analytics;

/**
 * Aggregate counts for one API over a time range.
 *
 * <p>{@code successRate} is {@code successCount / totalRequests * 100} rounded to one
 * decimal; {@code 0.0} when there are no requests. {@code avgLatencyMs} is the average
 * {@code latency_ms} over every row in the range (rounded to one decimal; {@code 0.0}
 * when there are no rows).
 *
 * @param totalRequests     every usage_log row in the range for the API.
 * @param successCount      rows with {@code outcome = SUCCESS}.
 * @param rateLimitedCount  rows with {@code outcome = RATE_LIMITED}.
 * @param unauthorizedCount rows with {@code outcome = UNAUTHORIZED}.
 * @param errorCount        rows with {@code outcome IN (UPSTREAM_ERROR, UPSTREAM_TIMEOUT, REJECTED)}.
 * @param successRate       successCount / totalRequests * 100 (one decimal).
 * @param avgLatencyMs      AVG(latency_ms) over the range (one decimal).
 */
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

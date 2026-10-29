package com.example.gateway.analytics;

/**
 * Aggregated usage for a single {@code (path, method)} pair over a time range.
 *
 * @param path         the request path.
 * @param method       the HTTP method.
 * @param count        number of requests for the pair in the range.
 * @param avgLatencyMs average {@code latency_ms} for the pair (one decimal).
 */
public record TopPath(
    String path,
    String method,
    long count,
    double avgLatencyMs
) {
}

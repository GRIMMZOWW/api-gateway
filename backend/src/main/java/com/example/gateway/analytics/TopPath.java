package com.example.gateway.analytics;

/** Aggregated usage for a single (path, method) pair over a time range. */
public record TopPath(
    String path,
    String method,
    long count,
    double avgLatencyMs
) {
}

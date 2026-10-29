package com.example.gateway.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound to the {@code gateway.*} properties in {@code application.yml}.
 * Used from Phase 4 onward (forwarding limits, cache, usage queue, SSRF toggle).
 */
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
    int maxBodyBytes,
    int defaultTimeoutMs,
    int cacheTtlSeconds,
    int cacheMaxEntries,
    int usageQueueCapacity,
    long usageFlushIntervalMs,
    int usageBatchSize,
    boolean allowPrivateUpstreams
) {}

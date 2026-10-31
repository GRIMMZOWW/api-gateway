package com.example.gateway.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound to the gateway.* properties in application.yml. */
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

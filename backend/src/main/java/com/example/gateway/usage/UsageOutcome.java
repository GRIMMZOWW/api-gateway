package com.example.gateway.usage;

/**
 * The outcome of a gateway request, stored on every {@code usage_logs} row (spec data model).
 */
public enum UsageOutcome {
    SUCCESS,
    RATE_LIMITED,
    UNAUTHORIZED,
    UPSTREAM_ERROR,
    UPSTREAM_TIMEOUT,
    REJECTED
}

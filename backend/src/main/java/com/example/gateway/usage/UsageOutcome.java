package com.example.gateway.usage;

/** The outcome of a gateway request, stored on every usage_logs row. */
public enum UsageOutcome {
    SUCCESS,
    RATE_LIMITED,
    UNAUTHORIZED,
    UPSTREAM_ERROR,
    UPSTREAM_TIMEOUT,
    REJECTED
}

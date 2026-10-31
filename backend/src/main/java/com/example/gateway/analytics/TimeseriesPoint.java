package com.example.gateway.analytics;

import java.time.Instant;

/** One bucket of a time series. */
public record TimeseriesPoint(
    Instant bucketStart,
    long total,
    long success,
    long rateLimited,
    long errors
) {
}

package com.example.gateway.analytics;

import java.time.Instant;

/**
 * One bucket of a time series. Empty buckets are filled with zeros in Java so the
 * caller always receives a contiguous sequence of points from {@code from} to {@code to}.
 *
 * @param bucketStart  the (UTC) start of the bucket.
 * @param total        total requests in the bucket.
 * @param success      requests with {@code outcome = SUCCESS} in the bucket.
 * @param rateLimited  requests with {@code outcome = RATE_LIMITED} in the bucket.
 * @param errors       requests with {@code outcome IN (UPSTREAM_ERROR, UPSTREAM_TIMEOUT, REJECTED)} in the bucket.
 */
public record TimeseriesPoint(
    Instant bucketStart,
    long total,
    long success,
    long rateLimited,
    long errors
) {
}

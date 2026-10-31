package com.example.gateway.analytics;

import com.example.gateway.common.ApiException;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;

/** Resolved, validated query range for an analytics request. */
public record AnalyticsQueryRange(Long apiId, Instant from, Instant to) {

    private static final Duration MAX_RANGE = Duration.ofDays(90);
    private static final Duration DEFAULT_WINDOW = Duration.ofHours(24);

    /** Build a range applying defaults and the 90-day maximum. */
    public static AnalyticsQueryRange of(Long apiId, Instant from, Instant to) {
        Instant end = (to != null) ? to : Instant.now();
        Instant start = (from != null) ? from : end.minus(DEFAULT_WINDOW);
        if (Duration.between(start, end).compareTo(MAX_RANGE) > 0) {
            throw new ApiException(
                "RANGE_TOO_WIDE",
                "The requested range exceeds 90 days.",
                HttpStatus.BAD_REQUEST);
        }
        return new AnalyticsQueryRange(apiId, start, end);
    }
}

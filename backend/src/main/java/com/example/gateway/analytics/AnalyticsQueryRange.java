package com.example.gateway.analytics;

import com.example.gateway.common.ApiException;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;

/**
 * Resolved, validated query range for an analytics request.
 *
 * <p>The {@link #of(Long, Instant, Instant)} factory applies the spec defaults
 * ({@code to = now} when null; {@code from = to - 24h} when null) and the hard
 * maximum of 90 days: a range strictly longer than 90 days throws
 * {@code RANGE_TOO_WIDE} (HTTP 400).
 *
 * @param apiId the API the analytics are scoped to.
 * @param from  inclusive lower bound (UTC instant).
 * @param to    exclusive upper bound (UTC instant).
 */
public record AnalyticsQueryRange(Long apiId, Instant from, Instant to) {

    private static final Duration MAX_RANGE = Duration.ofDays(90);
    private static final Duration DEFAULT_WINDOW = Duration.ofHours(24);

    /**
     * Build a range applying defaults and the 90-day maximum.
     *
     * @param apiId the API id (carried through for downstream scoping).
     * @param from  the requested start, or {@code null} for "to minus 24 hours".
     * @param to    the requested end, or {@code null} for "now".
     * @return a resolved range.
     * @throws ApiException {@code RANGE_TOO_WIDE} (400) if {@code to - from} exceeds 90 days.
     */
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

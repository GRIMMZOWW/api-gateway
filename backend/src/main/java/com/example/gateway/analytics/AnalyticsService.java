package com.example.gateway.analytics;

import com.example.gateway.apis.ApiService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only analytics queries over the {@code usage_logs} table.
 *
 * <p>Every method first scopes the request to the caller's ownership of {@code apiId}
 * via {@link ApiService#getOwnedApi(Long, Long)} (an API owned by someone else resolves
 * to {@code NOT_FOUND} / 404, never 403), then builds an {@link AnalyticsQueryRange}
 * (defaults + 90-day maximum), clamps any {@code limit}, and runs a single
 * <em>parameterized</em> JdbcTemplate query — every bind value is a {@code ?}
 * placeholder, never string concatenation.
 *
 * <p>The time series is grouped by a {@code DATE_FORMAT} bucket in SQL and then
 * gap-filled in Java so the returned list is always contiguous from {@code from} to
 * {@code to} with zero-valued buckets where there were no rows.
 */
@Service
public class AnalyticsService {

    /** Parses the {@code DATE_FORMAT} bucket strings back into UTC {@link Instant}s. */
    private static final DateTimeFormatter BUCKET_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static final int TOP_PATHS_DEFAULT = 10;
    static final int TOP_PATHS_MAX = 50;
    static final int RECENT_DEFAULT = 50;
    static final int RECENT_MAX = 200;

    private final JdbcTemplate jdbcTemplate;
    private final ApiService apiService;

    public AnalyticsService(JdbcTemplate jdbcTemplate, ApiService apiService) {
        this.jdbcTemplate = jdbcTemplate;
        this.apiService = apiService;
    }

    /**
     * Aggregate counts for one API over a range.
     */
    public SummaryResponse summary(Long userId, Long apiId, Instant from, Instant to) {
        apiService.getOwnedApi(userId, apiId);
        AnalyticsQueryRange range = AnalyticsQueryRange.of(apiId, from, to);

        String sql =
            "SELECT COUNT(*) AS total_requests, " +
            "SUM(CASE WHEN outcome = 'SUCCESS' THEN 1 ELSE 0 END) AS success_count, " +
            "SUM(CASE WHEN outcome = 'RATE_LIMITED' THEN 1 ELSE 0 END) AS rate_limited_count, " +
            "SUM(CASE WHEN outcome = 'UNAUTHORIZED' THEN 1 ELSE 0 END) AS unauthorized_count, " +
            "SUM(CASE WHEN outcome IN ('UPSTREAM_ERROR','UPSTREAM_TIMEOUT','REJECTED') THEN 1 ELSE 0 END) AS error_count, " +
            "AVG(latency_ms) AS avg_latency_ms " +
            "FROM usage_logs " +
            "WHERE api_id = ? AND created_at >= ? AND created_at < ?";

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            long total = rs.getLong("total_requests");
            long success = rs.getLong("success_count");
            long rateLimited = rs.getLong("rate_limited_count");
            long unauthorized = rs.getLong("unauthorized_count");
            long errors = rs.getLong("error_count");
            // getDouble/getLong return 0 when the SQL value is NULL (no rows), which is
            // exactly the spec's "total=0 => successRate=0.0, avgLatencyMs=0" rule.
            double avgLatency = rs.getDouble("avg_latency_ms");
            double successRate = total > 0 ? round1(success * 100.0 / total) : 0.0;
            return new SummaryResponse(total, success, rateLimited, unauthorized, errors,
                successRate, round1(avgLatency));
        }, apiId, Timestamp.from(range.from()), Timestamp.from(range.to()));
    }

    /**
     * Hourly (or daily) time series with gap filling.
     */
    public List<TimeseriesPoint> timeseries(Long userId, Long apiId, Instant from,
                                            Instant to, String bucket) {
        apiService.getOwnedApi(userId, apiId);
        AnalyticsQueryRange range = AnalyticsQueryRange.of(apiId, from, to);

        boolean daily = "day".equalsIgnoreCase(bucket);
        ChronoUnit unit = daily ? ChronoUnit.DAYS : ChronoUnit.HOURS;

        // Two fully literal SQL strings (no concatenation) selected by bucket type.
        String sql;
        if (daily) {
            sql =
                "SELECT DATE_FORMAT(created_at, '%Y-%m-%d 00:00:00') AS bucket, " +
                "COUNT(*) AS total, " +
                "SUM(CASE WHEN outcome = 'SUCCESS' THEN 1 ELSE 0 END) AS success, " +
                "SUM(CASE WHEN outcome = 'RATE_LIMITED' THEN 1 ELSE 0 END) AS rate_limited, " +
                "SUM(CASE WHEN outcome IN ('UPSTREAM_ERROR','UPSTREAM_TIMEOUT','REJECTED') THEN 1 ELSE 0 END) AS errors " +
                "FROM usage_logs " +
                "WHERE api_id = ? AND created_at >= ? AND created_at < ? " +
                "GROUP BY bucket ORDER BY bucket";
        } else {
            sql =
                "SELECT DATE_FORMAT(created_at, '%Y-%m-%d %H:00:00') AS bucket, " +
                "COUNT(*) AS total, " +
                "SUM(CASE WHEN outcome = 'SUCCESS' THEN 1 ELSE 0 END) AS success, " +
                "SUM(CASE WHEN outcome = 'RATE_LIMITED' THEN 1 ELSE 0 END) AS rate_limited, " +
                "SUM(CASE WHEN outcome IN ('UPSTREAM_ERROR','UPSTREAM_TIMEOUT','REJECTED') THEN 1 ELSE 0 END) AS errors " +
                "FROM usage_logs " +
                "WHERE api_id = ? AND created_at >= ? AND created_at < ? " +
                "GROUP BY bucket ORDER BY bucket";
        }

        List<TimeseriesPoint> rows = jdbcTemplate.query(sql, (rs, rowNum) ->
            new TimeseriesPoint(
                parseBucket(rs.getString("bucket")),
                rs.getLong("total"),
                rs.getLong("success"),
                rs.getLong("rate_limited"),
                rs.getLong("errors")
            ), apiId, Timestamp.from(range.from()), Timestamp.from(range.to()));

        // Index the SQL rows by bucket start so we can fill missing buckets in order.
        Map<Instant, TimeseriesPoint> byBucket = new LinkedHashMap<>();
        for (TimeseriesPoint p : rows) {
            byBucket.put(p.bucketStart(), p);
        }

        List<TimeseriesPoint> filled = new ArrayList<>();
        Instant cursor = range.from().truncatedTo(unit);
        Instant end = range.to();
        while (cursor.isBefore(end)) {
            TimeseriesPoint p = byBucket.getOrDefault(cursor,
                new TimeseriesPoint(cursor, 0L, 0L, 0L, 0L));
            filled.add(p);
            cursor = cursor.plus(1, unit);
        }
        return filled;
    }

    /**
     * Top request paths by volume.
     */
    public List<TopPath> topPaths(Long userId, Long apiId, Instant from, Instant to, int limit) {
        apiService.getOwnedApi(userId, apiId);
        AnalyticsQueryRange range = AnalyticsQueryRange.of(apiId, from, to);
        int clamped = clamp(limit, TOP_PATHS_DEFAULT, TOP_PATHS_MAX);

        String sql =
            "SELECT path, method, COUNT(*) AS cnt, AVG(latency_ms) AS avg_latency_ms " +
            "FROM usage_logs " +
            "WHERE api_id = ? AND created_at >= ? AND created_at < ? " +
            "GROUP BY path, method " +
            "ORDER BY cnt DESC, path ASC, method ASC " +
            "LIMIT ?";

        return jdbcTemplate.query(sql, (rs, rowNum) -> new TopPath(
            rs.getString("path"),
            rs.getString("method"),
            rs.getLong("cnt"),
            round1(rs.getDouble("avg_latency_ms"))
        ), apiId, Timestamp.from(range.from()), Timestamp.from(range.to()), clamped);
    }

    /**
     * Per-API-key usage rollup. Rows with a NULL {@code api_key_id} are skipped
     * (an inner JOIN naturally excludes them).
     */
    public List<KeyUsage> keys(Long userId, Long apiId, Instant from, Instant to) {
        apiService.getOwnedApi(userId, apiId);
        AnalyticsQueryRange range = AnalyticsQueryRange.of(apiId, from, to);

        String sql =
            "SELECT k.id AS key_id, k.name AS name, k.key_prefix AS prefix, " +
            "COUNT(*) AS total, " +
            "SUM(CASE WHEN u.outcome = 'RATE_LIMITED' THEN 1 ELSE 0 END) AS rate_limited, " +
            "MAX(u.created_at) AS last_used_at " +
            "FROM usage_logs u " +
            "JOIN api_keys k ON k.id = u.api_key_id " +
            "WHERE u.api_id = ? AND u.created_at >= ? AND u.created_at < ? " +
            "GROUP BY k.id, k.name, k.key_prefix " +
            "ORDER BY total DESC, k.id ASC";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            long keyIdVal = rs.getLong("key_id");
            Long keyId = rs.wasNull() ? null : keyIdVal;
            Timestamp ts = rs.getTimestamp("last_used_at");
            return new KeyUsage(
                keyId,
                rs.getString("name"),
                rs.getString("prefix"),
                rs.getLong("total"),
                rs.getLong("rate_limited"),
                ts == null ? null : ts.toInstant()
            );
        }, apiId, Timestamp.from(range.from()), Timestamp.from(range.to()));
    }

    /**
     * Newest usage rows (newest first).
     */
    public List<RecentRequest> recent(Long userId, Long apiId, Instant from, Instant to, int limit) {
        apiService.getOwnedApi(userId, apiId);
        AnalyticsQueryRange range = AnalyticsQueryRange.of(apiId, from, to);
        int clamped = clamp(limit, RECENT_DEFAULT, RECENT_MAX);

        String sql =
            "SELECT id, method, path, status_code, outcome, latency_ms, created_at, api_key_id " +
            "FROM usage_logs " +
            "WHERE api_id = ? AND created_at >= ? AND created_at < ? " +
            "ORDER BY created_at DESC, id DESC " +
            "LIMIT ?";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            long keyIdVal = rs.getLong("api_key_id");
            Long keyId = rs.wasNull() ? null : keyIdVal;
            return new RecentRequest(
                rs.getLong("id"),
                rs.getString("method"),
                rs.getString("path"),
                rs.getInt("status_code"),
                rs.getString("outcome"),
                rs.getInt("latency_ms"),
                rs.getTimestamp("created_at").toInstant(),
                keyId
            );
        }, apiId, Timestamp.from(range.from()), Timestamp.from(range.to()), clamped);
    }

    /** Round to one decimal place (half-up). */
    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    /** Clamp {@code limit} into {@code [1, max]}; a non-positive value falls back to {@code def}. */
    private static int clamp(int limit, int def, int max) {
        if (limit <= 0) {
            return def;
        }
        return Math.min(limit, max);
    }

    private static Instant parseBucket(String s) {
        // The DATE_FORMAT string is a UTC wall-clock value with no offset.
        return LocalDateTime.parse(s, BUCKET_FORMATTER).toInstant(ZoneOffset.UTC);
    }
}

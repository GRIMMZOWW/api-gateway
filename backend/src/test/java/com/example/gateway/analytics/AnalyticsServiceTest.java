package com.example.gateway.analytics;

import com.example.gateway.apis.Api;
import com.example.gateway.apis.ApiService;
import com.example.gateway.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit test of {@link AnalyticsService}. {@link JdbcTemplate} and
 * {@link ApiService} are mocked with Mockito ({@link MockitoExtension}); no Spring
 * context and no database.
 *
 * <p>The summary test drives the real {@link RowMapper} against a stubbed
 * {@link ResultSet} so the {@code successRate}/{@code avgLatencyMs} rounding is
 * exercised end to end. The time-series test stubs {@code query(...)} to return rows
 * for only two of the three expected hourly buckets and asserts the missing bucket is
 * gap-filled with zeros in Java.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long API_ID = 7L;
    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-01-02T00:00:00Z");

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ApiService apiService;

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsService(jdbcTemplate, apiService);
        // Every analytics method scopes by ownership first; an owned API returns a real
        // entity so the call proceeds to the JDBC layer.
        when(apiService.getOwnedApi(any(), any())).thenReturn(new Api(
            API_ID, USER_ID, "Demo", "demo", "https://upstream.example.com",
            10_000, true, Instant.parse("2026-01-01T00:00:00Z")));
    }

    @Test
    void summaryComputesCountsAndSuccessRate() throws Exception {
        // A stubbed usage_logs aggregate row: 5 total, 4 SUCCESS, 0 RATE_LIMITED,
        // 0 UNAUTHORIZED, 1 error, average latency 123.456 ms.
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("total_requests")).thenReturn(5L);
        when(rs.getLong("success_count")).thenReturn(4L);
        when(rs.getLong("rate_limited_count")).thenReturn(0L);
        when(rs.getLong("unauthorized_count")).thenReturn(0L);
        when(rs.getLong("error_count")).thenReturn(1L);
        when(rs.getDouble("avg_latency_ms")).thenReturn(123.456);

        // Hand the real RowMapper the stubbed ResultSet so the rounding math runs.
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenAnswer(inv -> {
                RowMapper<?> mapper = inv.getArgument(1);
                return mapper.mapRow(rs, 0);
            });

        SummaryResponse s = service.summary(USER_ID, API_ID, FROM, TO);

        assertThat(s.totalRequests()).isEqualTo(5L);
        assertThat(s.successCount()).isEqualTo(4L);
        assertThat(s.rateLimitedCount()).isZero();
        assertThat(s.unauthorizedCount()).isZero();
        assertThat(s.errorCount()).isEqualTo(1L);
        assertThat(s.successRate()).isEqualTo(80.0); // 4 / 5 * 100, one decimal
        assertThat(s.avgLatencyMs()).isEqualTo(123.5); // 123.456 -> 123.5
    }

    @Test
    void timeseriesFillsMissingBucketsWithZeros() {
        // Three hourly buckets expected (00:00, 01:00, 02:00) for from=00:00, to=03:00.
        // The JDBC layer only returns data for the first two; the third must be zeros.
        Instant b0 = Instant.parse("2026-01-01T00:00:00Z");
        Instant b1 = Instant.parse("2026-01-01T01:00:00Z");
        Instant b2 = Instant.parse("2026-01-01T02:00:00Z");

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(
                new TimeseriesPoint(b0, 5L, 4L, 1L, 0L),
                new TimeseriesPoint(b1, 3L, 3L, 0L, 0L)
            ));

        List<TimeseriesPoint> points =
            service.timeseries(USER_ID, API_ID, b0, Instant.parse("2026-01-01T03:00:00Z"), "hour");

        assertThat(points).hasSize(3);
        assertThat(points.get(0).bucketStart()).isEqualTo(b0);
        assertThat(points.get(0).total()).isEqualTo(5L);
        assertThat(points.get(1).bucketStart()).isEqualTo(b1);
        assertThat(points.get(1).total()).isEqualTo(3L);
        // Gap-filled bucket.
        assertThat(points.get(2).bucketStart()).isEqualTo(b2);
        assertThat(points.get(2).total()).isZero();
        assertThat(points.get(2).success()).isZero();
        assertThat(points.get(2).rateLimited()).isZero();
        assertThat(points.get(2).errors()).isZero();
    }

    @Test
    void rangeTooWideThrowsRangeTooWide() {
        Instant to = Instant.parse("2026-04-01T00:00:00Z");
        Instant from = to.minus(Duration.ofDays(91)); // 91 days > 90-day maximum

        ApiException thrown = catchThrowableOfType(
            () -> service.summary(USER_ID, API_ID, from, to), ApiException.class);

        assertThat(thrown).isNotNull();
        assertThat(thrown.code()).isEqualTo("RANGE_TOO_WIDE");
        assertThat(thrown.status()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void topPathsClampsLimitToMax() {
        // The canned empty list lets us focus on the clamped bind argument.
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.<TopPath>of());

        service.topPaths(USER_ID, API_ID, FROM, TO, 999); // over the 50 cap

        org.mockito.ArgumentCaptor<Object[]> captor =
            org.mockito.ArgumentCaptor.forClass(Object[].class);
        org.mockito.Mockito.verify(jdbcTemplate)
            .query(anyString(), any(RowMapper.class), captor.capture());

        Object[] args = captor.getValue();
        // apiId, from-Timestamp, to-Timestamp, clamped-limit
        assertThat(args).hasSize(4);
        assertThat(((Integer) args[3])).isEqualTo(AnalyticsService.TOP_PATHS_MAX);
    }
}

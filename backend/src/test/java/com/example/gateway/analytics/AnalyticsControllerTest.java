package com.example.gateway.analytics;

import com.example.gateway.common.ApiException;
import com.example.gateway.auth.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice test of {@link AnalyticsController}. Security filters are disabled so
 * the controller is reached directly; {@link AnalyticsService} is mocked. The
 * {@code @WebMvcTest} slice picks up {@code GlobalExceptionHandler}
 * ({@code @RestControllerAdvice}) so {@link ApiException}s surface with their HTTP
 * status and spec error body.
 */
@WebMvcTest(AnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsService analyticsService;

    // The @WebMvcTest slice component-scans JwtAuthFilter (a Filter) but does not scan
    // @Service JwtService, so the filter cannot be constructed. Providing a mock
    // satisfies the dependency; filters are disabled anyway via addFilters=false.
    @MockBean
    private JwtService jwtService;

    @Test
    void summaryReturns200WithFields() throws Exception {
        when(analyticsService.summary(any(), any(), any(), any()))
            .thenReturn(new SummaryResponse(5L, 4L, 0L, 0L, 1L, 80.0, 123.5));

        mockMvc.perform(get("/api/analytics/summary")
                .param("apiId", "7")
                .param("from", "2026-01-01T00:00:00Z")
                .param("to", "2026-01-02T00:00:00Z"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRequests").value(5))
            .andExpect(jsonPath("$.successCount").value(4))
            .andExpect(jsonPath("$.rateLimitedCount").value(0))
            .andExpect(jsonPath("$.unauthorizedCount").value(0))
            .andExpect(jsonPath("$.errorCount").value(1))
            .andExpect(jsonPath("$.successRate").value(80.0))
            .andExpect(jsonPath("$.avgLatencyMs").value(123.5));
    }

    @Test
    void timeseriesReturns200List() throws Exception {
        when(analyticsService.timeseries(any(), any(), any(), any(), any()))
            .thenReturn(List.of(
                new TimeseriesPoint(Instant.parse("2026-01-01T00:00:00Z"), 5L, 4L, 1L, 0L),
                new TimeseriesPoint(Instant.parse("2026-01-01T01:00:00Z"), 3L, 3L, 0L, 0L)
            ));

        mockMvc.perform(get("/api/analytics/timeseries")
                .param("apiId", "7")
                .param("bucket", "hour"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].total").value(5))
            .andExpect(jsonPath("$[0].success").value(4))
            .andExpect(jsonPath("$[1].total").value(3));
    }

    @Test
    void notOwnedApiReturns404() throws Exception {
        when(analyticsService.summary(any(), any(), any(), any()))
            .thenThrow(new ApiException("NOT_FOUND", "API not found.", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/analytics/summary").param("apiId", "999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void missingApiIdReturns400() throws Exception {
        // No apiId query param: the controller rejects it with BAD_REQUEST (400) before
        // the service is invoked, so no stub is needed here.
        mockMvc.perform(get("/api/analytics/summary"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }
}

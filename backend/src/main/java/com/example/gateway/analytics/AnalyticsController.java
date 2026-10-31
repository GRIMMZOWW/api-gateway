package com.example.gateway.analytics;

import com.example.gateway.common.ApiException;
import com.example.gateway.common.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** REST endpoints for usage analytics. */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    public SummaryResponse summary(@RequestParam(required = false) Long apiId,
                                   @RequestParam(required = false) Instant from,
                                   @RequestParam(required = false) Instant to) {
        requireApiId(apiId);
        return analyticsService.summary(SecurityUtils.currentUserId(), apiId, from, to);
    }

    @GetMapping("/timeseries")
    public List<TimeseriesPoint> timeseries(@RequestParam(required = false) Long apiId,
                                            @RequestParam(required = false) Instant from,
                                            @RequestParam(required = false) Instant to,
                                            @RequestParam(defaultValue = "hour") String bucket) {
        requireApiId(apiId);
        return analyticsService.timeseries(SecurityUtils.currentUserId(), apiId, from, to, bucket);
    }

    @GetMapping("/top-paths")
    public List<TopPath> topPaths(@RequestParam(required = false) Long apiId,
                                 @RequestParam(required = false) Instant from,
                                 @RequestParam(required = false) Instant to,
                                 @RequestParam(defaultValue = "10") int limit) {
        requireApiId(apiId);
        return analyticsService.topPaths(SecurityUtils.currentUserId(), apiId, from, to, limit);
    }

    @GetMapping("/keys")
    public List<KeyUsage> keys(@RequestParam(required = false) Long apiId,
                               @RequestParam(required = false) Instant from,
                               @RequestParam(required = false) Instant to) {
        requireApiId(apiId);
        return analyticsService.keys(SecurityUtils.currentUserId(), apiId, from, to);
    }

    @GetMapping("/recent")
    public List<RecentRequest> recent(@RequestParam(required = false) Long apiId,
                                     @RequestParam(required = false) Instant from,
                                     @RequestParam(required = false) Instant to,
                                     @RequestParam(defaultValue = "50") int limit) {
        requireApiId(apiId);
        return analyticsService.recent(SecurityUtils.currentUserId(), apiId, from, to, limit);
    }

    /** Reject a missing apiId up front with a 400 rather than letting it reach the service as null (which would otherwise surface as a generic error). */
    private void requireApiId(Long apiId) {
        if (apiId == null) {
            throw new ApiException(
                "BAD_REQUEST",
                "Missing required query parameter 'apiId'.",
                HttpStatus.BAD_REQUEST);
        }
    }
}

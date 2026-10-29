package com.example.gateway.gateway;

import com.example.gateway.apis.Api;
import com.example.gateway.apis.ApiRepository;
import com.example.gateway.common.ApiException;
import com.example.gateway.common.GatewayProperties;
import com.example.gateway.keys.CachedKey;
import com.example.gateway.keys.KeyHasher;
import com.example.gateway.keys.KeyLookupService;
import com.example.gateway.ratelimit.RateLimiterRegistry;
import com.example.gateway.ratelimit.TokenBucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc test of the gateway flow (Phase 5: cache-backed key lookup, per-key token-bucket
 * rate limit, {@code X-RateLimit-*} + {@code Retry-After} headers). No usage logging yet.
 * No DB; all collaborators mocked.
 */
@WebMvcTest(GatewayController.class)
@AutoConfigureMockMvc(addFilters = false)
class GatewayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private ApiRepository apiRepository;
    @MockBean private KeyLookupService keyLookupService;
    @MockBean private KeyHasher keyHasher;
    @MockBean private RateLimiterRegistry rateLimiterRegistry;
    @MockBean private Forwarder forwarder;
    @MockBean private GatewayProperties properties;
    @MockBean private com.example.gateway.auth.JwtService jwtService;

    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");
    private static final String KEY = "gk_ab12cd34ef56gh78ij90kl12mn34op56";
    private static final String HASH = "deadbeef".repeat(8); // 64 hex chars

    private Api demoApi;
    private CachedKey demoCachedKey;
    private TokenBucket.Decision allowed;

    @BeforeEach
    void setUp() {
        demoApi = new Api(10L, 1L, "Demo", "demo", "https://httpbin.org", 10000, true, NOW);
        demoCachedKey = new CachedKey(20L, 10L, true, null, 10,
            new BigDecimal("5.00"), "demo", "https://httpbin.org", 10000, true);
        allowed = new TokenBucket.Decision(true, 9, 0);

        when(properties.maxBodyBytes()).thenReturn(1048576);
        when(keyHasher.hash(KEY)).thenReturn(HASH);
        when(rateLimiterRegistry.tryConsume(anyLong(), anyInt(), anyDouble())).thenReturn(allowed);
    }

    @Test
    void missingKeyReturns401() throws Exception {
        when(apiRepository.findBySlug("demo")).thenReturn(Optional.of(demoApi));

        mockMvc.perform(get("/gw/demo/get"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("MISSING_API_KEY"))
            .andExpect(header().exists("X-Request-Id"))
            .andExpect(header().doesNotExist("X-RateLimit-Limit"));
    }

    @Test
    void badKeyReturns401() throws Exception {
        when(apiRepository.findBySlug("demo")).thenReturn(Optional.of(demoApi));
        when(keyLookupService.find(HASH)).thenReturn(Optional.empty());

        mockMvc.perform(get("/gw/demo/get").header("X-API-Key", KEY))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_API_KEY"));
    }

    @Test
    void revokedKeyReturns401() throws Exception {
        when(apiRepository.findBySlug("demo")).thenReturn(Optional.of(demoApi));
        CachedKey revoked = new CachedKey(20L, 10L, false, null, 10,
            new BigDecimal("5.00"), "demo", "https://httpbin.org", 10000, true);
        when(keyLookupService.find(HASH)).thenReturn(Optional.of(revoked));

        mockMvc.perform(get("/gw/demo/get").header("X-API-Key", KEY))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_API_KEY"));
    }

    @Test
    void expiredKeyReturns401() throws Exception {
        when(apiRepository.findBySlug("demo")).thenReturn(Optional.of(demoApi));
        CachedKey expired = new CachedKey(20L, 10L, true, NOW.minusSeconds(60), 10,
            new BigDecimal("5.00"), "demo", "https://httpbin.org", 10000, true);
        when(keyLookupService.find(HASH)).thenReturn(Optional.of(expired));

        mockMvc.perform(get("/gw/demo/get").header("X-API-Key", KEY))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_API_KEY"));
    }

    @Test
    void keyForDifferentApiReturns403() throws Exception {
        when(apiRepository.findBySlug("demo")).thenReturn(Optional.of(demoApi));
        CachedKey otherApi = new CachedKey(20L, 99L, true, null, 10,
            new BigDecimal("5.00"), "other", "https://httpbin.org", 10000, true);
        when(keyLookupService.find(HASH)).thenReturn(Optional.of(otherApi));

        mockMvc.perform(get("/gw/demo/get").header("X-API-Key", KEY))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("KEY_NOT_FOR_API"));
    }

    @Test
    void disabledApiReturns503() throws Exception {
        Api disabled = new Api(10L, 1L, "Demo", "demo", "https://httpbin.org", 10000, false, NOW);
        when(apiRepository.findBySlug("demo")).thenReturn(Optional.of(disabled));
        when(keyLookupService.find(HASH)).thenReturn(Optional.of(demoCachedKey));

        mockMvc.perform(get("/gw/demo/get").header("X-API-Key", KEY))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error.code").value("API_DISABLED"));
    }

    @Test
    void rateLimitedReturns429WithRetryAfterAndRateLimitHeaders() throws Exception {
        when(apiRepository.findBySlug("demo")).thenReturn(Optional.of(demoApi));
        when(keyLookupService.find(HASH)).thenReturn(Optional.of(demoCachedKey));
        when(rateLimiterRegistry.tryConsume(anyLong(), anyInt(), anyDouble()))
            .thenReturn(new TokenBucket.Decision(false, 0, 10));

        mockMvc.perform(get("/gw/demo/get").header("X-API-Key", KEY))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"))
            .andExpect(header().string("Retry-After", "10"))
            .andExpect(header().string("X-RateLimit-Limit", "10"))
            .andExpect(header().string("X-RateLimit-Remaining", "0"));
    }

    @Test
    void bodyTooLargeReturns413WithRateLimitHeaders() throws Exception {
        when(properties.maxBodyBytes()).thenReturn(10);
        when(apiRepository.findBySlug("demo")).thenReturn(Optional.of(demoApi));
        when(keyLookupService.find(HASH)).thenReturn(Optional.of(demoCachedKey));

        mockMvc.perform(post("/gw/demo/post").header("X-API-Key", KEY).content(new byte[20]))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.error.code").value("BODY_TOO_LARGE"))
            .andExpect(header().string("X-RateLimit-Limit", "10"));
    }

    @Test
    void upstreamSuccessReturnsUpstreamBodyAndRateLimitHeaders() throws Exception {
        when(apiRepository.findBySlug("demo")).thenReturn(Optional.of(demoApi));
        when(keyLookupService.find(HASH)).thenReturn(Optional.of(demoCachedKey));
        HttpHeaders upstreamHeaders = new HttpHeaders();
        upstreamHeaders.add("X-Custom", "hello");
        when(forwarder.forward(anyString(), anyString(), any(), any(), anyInt()))
            .thenReturn(new ForwardResult(200, upstreamHeaders, "response-body".getBytes()));

        mockMvc.perform(get("/gw/demo/get").header("X-API-Key", KEY))
            .andExpect(status().isOk())
            .andExpect(content().bytes("response-body".getBytes()))
            .andExpect(header().string("X-Custom", "hello"))
            .andExpect(header().exists("X-Request-Id"))
            .andExpect(header().string("X-RateLimit-Limit", "10"))
            .andExpect(header().string("X-RateLimit-Remaining", "9"));
    }

    @Test
    void xApiKeyHeaderIsNotForwarded() throws Exception {
        when(apiRepository.findBySlug("demo")).thenReturn(Optional.of(demoApi));
        when(keyLookupService.find(HASH)).thenReturn(Optional.of(demoCachedKey));
        when(forwarder.forward(anyString(), anyString(), any(), any(), anyInt()))
            .thenReturn(new ForwardResult(200, new HttpHeaders(), new byte[0]));

        mockMvc.perform(get("/gw/demo/get")
                .header("X-API-Key", KEY)
                .header("X-Secret", "keep-me"))
            .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<HttpHeaders> captor =
            org.mockito.ArgumentCaptor.forClass(HttpHeaders.class);
        verify(forwarder).forward(anyString(), anyString(), any(), captor.capture(), anyInt());
        HttpHeaders forwarded = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(forwarded.getFirst("X-API-Key")).isNull();
        org.assertj.core.api.Assertions.assertThat(forwarded.getFirst("Host")).isNull();
        org.assertj.core.api.Assertions.assertThat(forwarded.getFirst("X-Secret")).isEqualTo("keep-me");
        org.assertj.core.api.Assertions.assertThat(forwarded.getFirst("X-Request-Id")).isNotNull();
        org.assertj.core.api.Assertions.assertThat(forwarded.getFirst("X-Forwarded-For")).isNotNull();
    }

    @Test
    void upstreamErrorReturns502() throws Exception {
        when(apiRepository.findBySlug("demo")).thenReturn(Optional.of(demoApi));
        when(keyLookupService.find(HASH)).thenReturn(Optional.of(demoCachedKey));
        when(forwarder.forward(anyString(), anyString(), any(), any(), anyInt()))
            .thenThrow(new ApiException("UPSTREAM_ERROR", "Could not connect to the upstream API.",
                HttpStatus.BAD_GATEWAY));

        mockMvc.perform(get("/gw/demo/get").header("X-API-Key", KEY))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.error.code").value("UPSTREAM_ERROR"))
            .andExpect(header().string("X-RateLimit-Limit", "10"));
    }

    @Test
    void upstreamTimeoutReturns504() throws Exception {
        when(apiRepository.findBySlug("demo")).thenReturn(Optional.of(demoApi));
        when(keyLookupService.find(HASH)).thenReturn(Optional.of(demoCachedKey));
        when(forwarder.forward(anyString(), anyString(), any(), any(), anyInt()))
            .thenThrow(new ApiException("UPSTREAM_TIMEOUT",
                "Upstream did not respond within the configured timeout.",
                HttpStatus.GATEWAY_TIMEOUT));

        mockMvc.perform(get("/gw/demo/get").header("X-API-Key", KEY))
            .andExpect(status().isGatewayTimeout())
            .andExpect(jsonPath("$.error.code").value("UPSTREAM_TIMEOUT"));
    }

    @Test
    void upstream4xxIsPassedThrough() throws Exception {
        when(apiRepository.findBySlug("demo")).thenReturn(Optional.of(demoApi));
        when(keyLookupService.find(HASH)).thenReturn(Optional.of(demoCachedKey));
        when(forwarder.forward(anyString(), anyString(), any(), any(), anyInt()))
            .thenReturn(new ForwardResult(404, new HttpHeaders(), "not found".getBytes()));

        mockMvc.perform(get("/gw/demo/missing").header("X-API-Key", KEY))
            .andExpect(status().isNotFound())
            .andExpect(content().bytes("not found".getBytes()));
    }

    @Test
    void unknownSlugWithValidKeyReturns403() throws Exception {
        when(apiRepository.findBySlug("nope")).thenReturn(Optional.empty());
        when(keyLookupService.find(HASH)).thenReturn(Optional.of(demoCachedKey));

        mockMvc.perform(get("/gw/nope/get").header("X-API-Key", KEY))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("KEY_NOT_FOR_API"));
    }
}

package com.example.gateway.gateway;

import com.example.gateway.apis.Api;
import com.example.gateway.apis.ApiRepository;
import com.example.gateway.common.ApiException;
import com.example.gateway.common.ErrorBody;
import com.example.gateway.common.ErrorResponse;
import com.example.gateway.common.GatewayProperties;
import com.example.gateway.keys.CachedKey;
import com.example.gateway.keys.KeyHasher;
import com.example.gateway.keys.KeyLookupService;
import com.example.gateway.ratelimit.RateLimiterRegistry;
import com.example.gateway.ratelimit.TokenBucket;
import com.example.gateway.usage.UsageEvent;
import com.example.gateway.usage.UsageOutcome;
import com.example.gateway.usage.UsageRecorder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** The gateway endpoint /gw/{slug}/**. */
@RestController
public class GatewayController {

    static final Set<String> HOP_BY_HOP = Set.of(
        "x-api-key", "host", "connection", "keep-alive",
        "proxy-authenticate", "proxy-authorization", "te", "trailer",
        "transfer-encoding", "upgrade");

    private static final int MAX_PATH_LENGTH = 500;

    private final ApiRepository apiRepository;
    private final KeyLookupService keyLookupService;
    private final KeyHasher keyHasher;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final Forwarder forwarder;
    private final GatewayProperties properties;
    private final ObjectMapper objectMapper;
    private final UsageRecorder usageRecorder;

    public GatewayController(ApiRepository apiRepository, KeyLookupService keyLookupService,
                             KeyHasher keyHasher, RateLimiterRegistry rateLimiterRegistry,
                             Forwarder forwarder, GatewayProperties properties,
                             ObjectMapper objectMapper, UsageRecorder usageRecorder) {
        this.apiRepository = apiRepository;
        this.keyLookupService = keyLookupService;
        this.keyHasher = keyHasher;
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.forwarder = forwarder;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.usageRecorder = usageRecorder;
    }

    @RequestMapping(value = "/gw/{slug}/**",
                    method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                              RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.HEAD,
                              RequestMethod.OPTIONS})
    public ResponseEntity<byte[]> handle(@PathVariable String slug, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        long start = System.nanoTime();
        String method = request.getMethod();
        String path = pathAfter(slug, request);

        Optional<Api> apiOpt = apiRepository.findBySlug(slug);
        Api api = apiOpt.orElse(null);
        Long apiId = api != null ? api.getId() : null;
        Long apiKeyId = null; // filled in once a valid key is resolved

        // Step 2: read X-API-Key.
        String key = request.getHeader("X-API-Key");
        if (key == null || key.isBlank()) {
            return error(requestId, apiId, null, 401, "MISSING_API_KEY",
                "Missing X-API-Key header.", UsageOutcome.UNAUTHORIZED, method, path, start, request, null);
        }

        // Step 3: hash and look up the key (Caffeine cache, then MySQL).
        String keyHash = keyHasher.hash(key);
        Optional<CachedKey> keyOpt = keyLookupService.find(keyHash);
        if (keyOpt.isEmpty()) {
            return error(requestId, apiId, null, 401, "INVALID_API_KEY",
                "Invalid API key.", UsageOutcome.UNAUTHORIZED, method, path, start, request, null);
        }
        CachedKey cachedKey = keyOpt.get();
        apiKeyId = cachedKey.keyId();
        if (!cachedKey.active()) {
            return error(requestId, apiId, apiKeyId, 401, "INVALID_API_KEY",
                "Invalid API key.", UsageOutcome.UNAUTHORIZED, method, path, start, request, null);
        }
        if (cachedKey.expiresAt() != null && cachedKey.expiresAt().isBefore(Instant.now())) {
            return error(requestId, apiId, apiKeyId, 401, "INVALID_API_KEY",
                "Invalid API key.", UsageOutcome.UNAUTHORIZED, method, path, start, request, null);
        }

        // Step 4: the key must belong to this slug's API. Unknown slug -> 403, not logged.
        if (api == null) {
            return error(requestId, null, apiKeyId, 403, "KEY_NOT_FOR_API",
                "The API key is not valid for this API.", UsageOutcome.UNAUTHORIZED,
                method, path, start, request, null);
        }
        if (!cachedKey.apiId().equals(api.getId())) {
            return error(requestId, apiId, apiKeyId, 403, "KEY_NOT_FOR_API",
                "The API key is not valid for this API.", UsageOutcome.UNAUTHORIZED,
                method, path, start, request, null);
        }

        // Step 5: API disabled.
        if (!api.isEnabled()) {
            return error(requestId, apiId, apiKeyId, 503, "API_DISABLED",
                "This API is currently disabled.", UsageOutcome.REJECTED, method, path, start, request, null);
        }

        // Step 6: per-key token bucket.
        TokenBucket.Decision decision = rateLimiterRegistry.tryConsume(
            cachedKey.keyId(), cachedKey.capacity(), cachedKey.refillPerSec().doubleValue());
        RateLimitHeaders rl = new RateLimitHeaders(cachedKey.capacity(), decision.remaining(), 0);
        if (!decision.allowed()) {
            return error(requestId, apiId, apiKeyId, 429, "RATE_LIMITED",
                "Rate limit exceeded. Retry later.", UsageOutcome.RATE_LIMITED, method, path, start, request,
                new RateLimitHeaders(cachedKey.capacity(), 0, decision.retryAfterSeconds()));
        }

        // Step 7: read the request body, maximum maxBodyBytes.
        byte[] body;
        try {
            body = readBody(request, properties.maxBodyBytes());
        } catch (BodyTooLargeException e) {
            return error(requestId, apiId, apiKeyId, 413, "BODY_TOO_LARGE",
                "Request body exceeds the maximum allowed size.", UsageOutcome.REJECTED, method, path, start, request, rl);
        } catch (IOException e) {
            return error(requestId, apiId, apiKeyId, 400, "BAD_REQUEST",
                "Could not read the request body.", UsageOutcome.REJECTED, method, path, start, request, rl);
        }

        // Step 7: forward to the upstream.
        String targetUrl = buildTargetUrl(api, slug, request, path);
        HttpHeaders forwardHeaders = readForwardHeaders(request, requestId);
        ForwardResult result;
        try {
            result = forwarder.forward(method, targetUrl, body, forwardHeaders, api.getTimeoutMs());
        } catch (ApiException ex) {
            UsageOutcome outcome = "UPSTREAM_TIMEOUT".equals(ex.code())
                ? UsageOutcome.UPSTREAM_TIMEOUT : UsageOutcome.UPSTREAM_ERROR;
            return error(requestId, apiId, apiKeyId, ex.status().value(), ex.code(), ex.getMessage(),
                outcome, method, path, start, request, rl);
        }

        // Step 8: copy upstream status, headers and body; add rate-limit + request-id headers.
        int latencyMs = (int) ((System.nanoTime() - start) / 1_000_000);
        usageRecorder.record(new UsageEvent(apiId, apiKeyId, requestId, method, path,
            result.statusCode(), latencyMs, UsageOutcome.SUCCESS, Instant.now()));
        return successResponse(result, requestId, rl);
    }

    private ResponseEntity<byte[]> error(String requestId, Long apiId, Long apiKeyId, int status,
                                         String code, String message, UsageOutcome outcome,
                                         String method, String path, long start,
                                         HttpServletRequest request, RateLimitHeaders rl) {
        int latencyMs = (int) ((System.nanoTime() - start) / 1_000_000);
        // Only requests for an existing API are logged.
        if (apiId != null) {
            usageRecorder.record(new UsageEvent(apiId, apiKeyId, requestId, method, path,
                status, latencyMs, outcome, Instant.now()));
        }
        byte[] json;
        try {
            json = objectMapper.writeValueAsBytes(new ErrorResponse(ErrorBody.of(code, message, requestId)));
        } catch (JsonProcessingException e) {
            json = ("{\"error\":{\"code\":\"INTERNAL_ERROR\",\"requestId\":\"" + requestId + "\"}}")
                .getBytes();
        }
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Request-Id", requestId)
            .header("X-Content-Type-Options", "nosniff");
        if (rl != null) {
            builder.header("X-RateLimit-Limit", String.valueOf(rl.capacity()));
            builder.header("X-RateLimit-Remaining", String.valueOf(rl.remaining()));
            if (rl.retryAfterSeconds() > 0) {
                builder.header("Retry-After", String.valueOf(rl.retryAfterSeconds()));
            }
        }
        return builder.body(json);
    }

    private ResponseEntity<byte[]> successResponse(ForwardResult result, String requestId,
                                                   RateLimitHeaders rl) {
        HttpHeaders out = new HttpHeaders();
        if (result.headers() != null) {
            result.headers().forEach((name, values) -> {
                String lower = name.toLowerCase(Locale.ROOT);
                if (!HOP_BY_HOP.contains(lower) && !"content-length".equals(lower)) {
                    out.put(name, values);
                }
            });
        }
        out.add("X-Request-Id", requestId);
        out.add("X-RateLimit-Limit", String.valueOf(rl.capacity()));
        out.add("X-RateLimit-Remaining", String.valueOf(rl.remaining()));
        return ResponseEntity.status(result.statusCode()).headers(out).body(result.body());
    }

    /** Path after /gw/{slug}, query string removed, cut at 500 characters. */
    private String pathAfter(String slug, HttpServletRequest request) {
        String uri = request.getRequestURI();
        String prefix = "/gw/" + slug;
        String after = uri.length() > prefix.length() ? uri.substring(prefix.length()) : "";
        if (after.length() > MAX_PATH_LENGTH) {
            after = after.substring(0, MAX_PATH_LENGTH);
        }
        return after;
    }

    private String buildTargetUrl(Api api, String slug, HttpServletRequest request, String pathAfter) {
        String base = api.getUpstreamBaseUrl().replaceAll("/+$", "");
        StringBuilder url = new StringBuilder(base).append(pathAfter);
        String query = request.getQueryString();
        if (query != null && !query.isBlank()) {
            url.append('?').append(query);
        }
        return url.toString();
    }

    private HttpHeaders readForwardHeaders(HttpServletRequest request, String requestId) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            if (!HOP_BY_HOP.contains(name.toLowerCase(Locale.ROOT))) {
                headers.addAll(name, Collections.list(request.getHeaders(name)));
            }
        }
        headers.set("X-Request-Id", requestId);
        headers.set("X-Forwarded-For", clientIp(request));
        return headers;
    }

    private byte[] readBody(HttpServletRequest request, int maxBytes) throws IOException, BodyTooLargeException {
        InputStream in = request.getInputStream();
        if (in == null) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        int total = 0;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new BodyTooLargeException();
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    record RateLimitHeaders(int capacity, int remaining, int retryAfterSeconds) {}

    static final class BodyTooLargeException extends RuntimeException {}
}

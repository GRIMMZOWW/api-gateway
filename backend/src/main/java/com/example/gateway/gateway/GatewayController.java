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

/**
 * The gateway endpoint {@code /gw/{slug}/**}. Implements the request flow from the spec:
 * request id; resolve slug; read and validate {@code X-API-Key} (cache-backed lookup);
 * check the key belongs to the slug's API; check the API is enabled; per-key token-bucket
 * rate limit with {@code X-RateLimit-*} headers and {@code Retry-After} on {@code 429};
 * read the body (max 1 MB); forward; copy the upstream response back; (usage logging
 * arrives in Phase 6).
 *
 * <p>Gateway errors are built here with the request's own id so it matches any usage row.
 */
@RestController
public class GatewayController {

    /** Hop-by-hop headers removed from both the forwarded request and the returned response. */
    static final Set<String> HOP_BY_HOP = Set.of(
        "x-api-key", "host", "connection", "keep-alive",
        "proxy-authenticate", "proxy-authorization", "te", "trailer",
        "transfer-encoding", "upgrade");

    private final ApiRepository apiRepository;
    private final KeyLookupService keyLookupService;
    private final KeyHasher keyHasher;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final Forwarder forwarder;
    private final GatewayProperties properties;
    private final ObjectMapper objectMapper;

    public GatewayController(ApiRepository apiRepository, KeyLookupService keyLookupService,
                             KeyHasher keyHasher, RateLimiterRegistry rateLimiterRegistry,
                             Forwarder forwarder, GatewayProperties properties,
                             ObjectMapper objectMapper) {
        this.apiRepository = apiRepository;
        this.keyLookupService = keyLookupService;
        this.keyHasher = keyHasher;
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.forwarder = forwarder;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @RequestMapping(value = "/gw/{slug}/**",
                    method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                              RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.HEAD,
                              RequestMethod.OPTIONS})
    public ResponseEntity<byte[]> handle(@PathVariable String slug, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        long start = System.nanoTime();

        Optional<Api> apiOpt = apiRepository.findBySlug(slug);
        Api api = apiOpt.orElse(null);
        Long apiId = api != null ? api.getId() : null;

        // Step 2: read X-API-Key.
        String key = request.getHeader("X-API-Key");
        if (key == null || key.isBlank()) {
            return error(requestId, apiId, 401, "MISSING_API_KEY",
                "Missing X-API-Key header.", null);
        }

        // Step 3: hash and look up the key (Caffeine cache, then MySQL).
        String keyHash = keyHasher.hash(key);
        Optional<CachedKey> keyOpt = keyLookupService.find(keyHash);
        if (keyOpt.isEmpty()) {
            return error(requestId, apiId, 401, "INVALID_API_KEY", "Invalid API key.", null);
        }
        CachedKey cachedKey = keyOpt.get();
        if (!cachedKey.active()) {
            return error(requestId, apiId, 401, "INVALID_API_KEY", "Invalid API key.", null);
        }
        if (cachedKey.expiresAt() != null && cachedKey.expiresAt().isBefore(Instant.now())) {
            return error(requestId, apiId, 401, "INVALID_API_KEY", "Invalid API key.", null);
        }

        // Step 4: the key must belong to this slug's API. Unknown slug -> 403, not logged.
        if (api == null) {
            return error(requestId, null, 403, "KEY_NOT_FOR_API",
                "The API key is not valid for this API.", null);
        }
        if (!cachedKey.apiId().equals(api.getId())) {
            return error(requestId, apiId, 403, "KEY_NOT_FOR_API",
                "The API key is not valid for this API.", null);
        }

        // Step 5: API disabled.
        if (!api.isEnabled()) {
            return error(requestId, apiId, 503, "API_DISABLED",
                "This API is currently disabled.", null);
        }

        // Step 6: per-key token bucket.
        TokenBucket.Decision decision = rateLimiterRegistry.tryConsume(
            cachedKey.keyId(), cachedKey.capacity(), cachedKey.refillPerSec().doubleValue());
        RateLimitHeaders rl = new RateLimitHeaders(cachedKey.capacity(), decision.remaining(), 0);
        if (!decision.allowed()) {
            return error(requestId, apiId, 429, "RATE_LIMITED",
                "Rate limit exceeded. Retry later.",
                new RateLimitHeaders(cachedKey.capacity(), 0, decision.retryAfterSeconds()));
        }

        // Step 7: read the request body, maximum maxBodyBytes.
        byte[] body;
        try {
            body = readBody(request, properties.maxBodyBytes());
        } catch (BodyTooLargeException e) {
            return error(requestId, apiId, 413, "BODY_TOO_LARGE",
                "Request body exceeds the maximum allowed size.", rl);
        } catch (IOException e) {
            return error(requestId, apiId, 400, "BAD_REQUEST",
                "Could not read the request body.", rl);
        }

        // Step 7: forward to the upstream.
        String targetUrl = buildTargetUrl(api, slug, request);
        HttpHeaders forwardHeaders = readForwardHeaders(request, requestId);
        ForwardResult result;
        try {
            result = forwarder.forward(request.getMethod(), targetUrl, body,
                forwardHeaders, api.getTimeoutMs());
        } catch (ApiException ex) {
            return error(requestId, apiId, ex.status().value(), ex.code(), ex.getMessage(), rl);
        }

        // Step 8: copy upstream status, headers and body; add rate-limit + request-id headers.
        return successResponse(result, requestId, rl);
    }

    private ResponseEntity<byte[]> error(String requestId, Long apiId, int status,
                                         String code, String message, RateLimitHeaders rl) {
        // Usage logging arrives in Phase 6; apiId is computed here for that purpose.
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

    private String buildTargetUrl(Api api, String slug, HttpServletRequest request) {
        String base = api.getUpstreamBaseUrl().replaceAll("/+$", "");
        String uri = request.getRequestURI();
        String prefix = "/gw/" + slug;
        String pathAfter = uri.length() > prefix.length() ? uri.substring(prefix.length()) : "";
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

    /** Rate-limit headers to attach to a response, or {@code null} before the bucket step. */
    record RateLimitHeaders(int capacity, int remaining, int retryAfterSeconds) {}

    /** Signals the request body exceeded {@code gateway.max-body-bytes}. */
    static final class BodyTooLargeException extends RuntimeException {}
}

package com.example.gateway.gateway;

import com.example.gateway.apis.Api;
import com.example.gateway.apis.ApiRepository;
import com.example.gateway.common.ApiException;
import com.example.gateway.common.ErrorBody;
import com.example.gateway.common.ErrorResponse;
import com.example.gateway.common.GatewayProperties;
import com.example.gateway.keys.ApiKey;
import com.example.gateway.keys.ApiKeyRepository;
import com.example.gateway.keys.ApiKeyStatus;
import com.example.gateway.keys.KeyHasher;
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
 * generate a request id, resolve the slug, read and validate {@code X-API-Key}, check the
 * key belongs to the slug's API, check the API is enabled, (rate limit comes in Phase 5),
 * read the body (max 1 MB), forward, copy the upstream response back, and (usage logging
 * comes in Phase 6).
 *
 * <p>Gateway errors are built here with the request's own id (not via GlobalExceptionHandler)
 * so the id is consistent across the response body and any usage log row.
 */
@RestController
public class GatewayController {

    /** Hop-by-hop headers removed from both the forwarded request and the returned response. */
    static final Set<String> HOP_BY_HOP = Set.of(
        "x-api-key", "host", "connection", "keep-alive",
        "proxy-authenticate", "proxy-authorization", "te", "trailer",
        "transfer-encoding", "upgrade");

    private final ApiRepository apiRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final KeyHasher keyHasher;
    private final Forwarder forwarder;
    private final GatewayProperties properties;
    private final ObjectMapper objectMapper;

    public GatewayController(ApiRepository apiRepository, ApiKeyRepository apiKeyRepository,
                             KeyHasher keyHasher, Forwarder forwarder,
                             GatewayProperties properties, ObjectMapper objectMapper) {
        this.apiRepository = apiRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.keyHasher = keyHasher;
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
                "Missing X-API-Key header.", start, request);
        }

        // Step 3: hash and look up the key (cache comes in Phase 5; for now, straight from MySQL).
        String keyHash = keyHasher.hash(key);
        Optional<ApiKey> keyOpt = apiKeyRepository.findByKeyHash(keyHash);
        if (keyOpt.isEmpty()) {
            return error(requestId, apiId, 401, "INVALID_API_KEY", "Invalid API key.", start, request);
        }
        ApiKey apiKey = keyOpt.get();
        if (apiKey.getStatus() == ApiKeyStatus.REVOKED) {
            return error(requestId, apiId, 401, "INVALID_API_KEY", "Invalid API key.", start, request);
        }
        if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(Instant.now())) {
            return error(requestId, apiId, 401, "INVALID_API_KEY", "Invalid API key.", start, request);
        }

        // Step 4: the key must belong to this slug's API. Unknown slug -> 403, not logged.
        if (api == null) {
            return error(requestId, null, 403, "KEY_NOT_FOR_API",
                "The API key is not valid for this API.", start, request);
        }
        if (!apiKey.getApiId().equals(api.getId())) {
            return error(requestId, apiId, 403, "KEY_NOT_FOR_API",
                "The API key is not valid for this API.", start, request);
        }

        // Step 5: API disabled.
        if (!api.isEnabled()) {
            return error(requestId, apiId, 503, "API_DISABLED",
                "This API is currently disabled.", start, request);
        }

        // Step 6: rate limiting (Phase 5 inserts the token-bucket check here).

        // Step 7: read the request body, maximum maxBodyBytes.
        byte[] body;
        try {
            body = readBody(request, properties.maxBodyBytes());
        } catch (BodyTooLargeException e) {
            return error(requestId, apiId, 413, "BODY_TOO_LARGE",
                "Request body exceeds the maximum allowed size.", start, request);
        } catch (IOException e) {
            return error(requestId, apiId, 400, "BAD_REQUEST",
                "Could not read the request body.", start, request);
        }

        // Step 7: forward to the upstream.
        String targetUrl = buildTargetUrl(api, slug, request);
        HttpHeaders forwardHeaders = readForwardHeaders(request, requestId);
        ForwardResult result;
        try {
            result = forwarder.forward(request.getMethod(), targetUrl, body,
                forwardHeaders, api.getTimeoutMs());
        } catch (ApiException ex) {
            // Forwarder maps connection/timeout failures to UPSTREAM_ERROR / UPSTREAM_TIMEOUT.
            int status = ex.status().value();
            return error(requestId, apiId, status, ex.code(), ex.getMessage(), start, request);
        }

        // Step 8: copy upstream status, headers and body; add X-Request-Id.
        // (Rate-limit headers are added in Phase 5.)
        return successResponse(result, requestId);
    }

    private ResponseEntity<byte[]> error(String requestId, Long apiId, int status,
                                         String code, String message, long start,
                                         HttpServletRequest request) {
        int latencyMs = (int) ((System.nanoTime() - start) / 1_000_000);
        // Usage logging arrives in Phase 6; for now apiId/latency are computed but unused.
        if (apiId == null || apiId < 0) {
            // not logged (unknown slug)
        }
        byte[] json;
        try {
            json = objectMapper.writeValueAsBytes(new ErrorResponse(ErrorBody.of(code, message, requestId)));
        } catch (JsonProcessingException e) {
            json = ("{\"error\":{\"code\":\"INTERNAL_ERROR\",\"requestId\":\"" + requestId + "\"}}")
                .getBytes();
        }
        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Request-Id", requestId)
            .header("X-Content-Type-Options", "nosniff")
            .body(json);
    }

    private ResponseEntity<byte[]> successResponse(ForwardResult result, String requestId) {
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

    /** Signals the request body exceeded {@code gateway.max-body-bytes}. */
    static final class BodyTooLargeException extends RuntimeException {}
}

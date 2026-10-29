package com.example.gateway.apis;

import com.example.gateway.common.ApiException;
import com.example.gateway.common.GatewayProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Business logic for the {@code /api/apis} endpoints.
 *
 * <p>Enforces (per spec):
 * <ul>
 *   <li>Slug format + reserved-word check (security #8) → {@code VALIDATION_ERROR}
 *       with {@code fields.slug}.</li>
 *   <li>Timeout range 500–30000 ms → {@code VALIDATION_ERROR} with
 *       {@code fields.timeoutMs}.</li>
 *   <li>SSRF validation of {@code upstreamBaseUrl} (security #5) →
 *       {@code SSRF_BLOCKED}.</li>
 *   <li>Max 10 APIs per user (security #9) → {@code API_LIMIT_REACHED}.</li>
 *   <li>Owner scoping: an API owned by another user is returned as
 *       {@code NOT_FOUND} (HTTP 404, never 403).</li>
 * </ul>
 */
@Service
public class ApiService {

    static final int MAX_APIS_PER_USER = 10;
    static final int MIN_TIMEOUT_MS = 500;
    static final int MAX_TIMEOUT_MS = 30_000;

    private final ApiRepository apiRepository;
    private final SlugValidator slugValidator;
    private final SsrfValidator ssrfValidator;
    private final GatewayProperties gatewayProperties;

    public ApiService(ApiRepository apiRepository, SlugValidator slugValidator,
                      SsrfValidator ssrfValidator, GatewayProperties gatewayProperties) {
        this.apiRepository = apiRepository;
        this.slugValidator = slugValidator;
        this.ssrfValidator = ssrfValidator;
        this.gatewayProperties = gatewayProperties;
    }

    @Transactional
    public ApiResponse create(Long userId, CreateApiRequest request) {
        if (!slugValidator.isValid(request.slug())) {
            throw validationError(Map.of(
                "slug", "Slug must be 3-50 chars, lowercase alphanumerics or hyphens, and not reserved."));
        }
        int timeoutMs = request.timeoutMs() != null
            ? request.timeoutMs()
            : gatewayProperties.defaultTimeoutMs();
        validateTimeout(timeoutMs);

        ssrfValidator.validate(request.upstreamBaseUrl());

        if (apiRepository.existsBySlug(request.slug())) {
            throw validationError(Map.of("slug", "Slug already in use."));
        }
        if (apiRepository.countByOwnerId(userId) >= MAX_APIS_PER_USER) {
            throw new ApiException("API_LIMIT_REACHED",
                "You have reached the maximum of " + MAX_APIS_PER_USER + " APIs.",
                HttpStatus.BAD_REQUEST);
        }

        Api api = new Api(null, userId, request.name(), request.slug(),
            request.upstreamBaseUrl(), timeoutMs, true, Instant.now());
        api = apiRepository.save(api);
        return toResponse(api);
    }

    @Transactional(readOnly = true)
    public List<ApiResponse> list(Long userId) {
        return apiRepository.findByOwnerId(userId).stream()
            .map(this::toResponse)
            .toList();
    }

    /**
     * Returns the owned {@link Api} entity for internal use by other services
     * (keys, analytics). Throws {@code NOT_FOUND} (HTTP 404) if the API does
     * not exist or is owned by another user — never 403, per spec.
     */
    @Transactional(readOnly = true)
    public Api getOwnedApi(Long userId, Long apiId) {
        if (apiId == null) {
            throw notFound();
        }
        Api api = apiRepository.findById(apiId)
            .orElseThrow(ApiService::notFound);
        if (!api.getOwnerId().equals(userId)) {
            throw notFound();
        }
        return api;
    }

    @Transactional
    public ApiResponse update(Long userId, Long apiId, UpdateApiRequest request) {
        Api api = getOwnedApi(userId, apiId);

        if (request.name() != null) {
            api.setName(request.name());
        }
        if (request.upstreamBaseUrl() != null) {
            ssrfValidator.validate(request.upstreamBaseUrl());
            api.setUpstreamBaseUrl(request.upstreamBaseUrl());
        }
        if (request.timeoutMs() != null) {
            validateTimeout(request.timeoutMs());
            api.setTimeoutMs(request.timeoutMs());
        }
        if (request.enabled() != null) {
            api.setEnabled(request.enabled());
        }

        api = apiRepository.save(api);
        return toResponse(api);
    }

    @Transactional
    public void delete(Long userId, Long apiId) {
        Api api = getOwnedApi(userId, apiId);
        // ON DELETE CASCADE in V1__init.sql removes api_keys + usage_logs.
        apiRepository.delete(api);
        apiRepository.flush();
    }

    private void validateTimeout(int timeoutMs) {
        if (timeoutMs < MIN_TIMEOUT_MS || timeoutMs > MAX_TIMEOUT_MS) {
            throw validationError(Map.of(
                "timeoutMs", "timeoutMs must be between " + MIN_TIMEOUT_MS + " and " + MAX_TIMEOUT_MS + "."));
        }
    }

    private ApiResponse toResponse(Api api) {
        return new ApiResponse(api.getId(), api.getName(), api.getSlug(),
            api.getUpstreamBaseUrl(), api.getTimeoutMs(), api.isEnabled(), api.getCreatedAt());
    }

    private static ApiException validationError(Map<String, String> fields) {
        // LinkedHashMap so field insertion order is stable in the JSON output.
        return new ApiException("VALIDATION_ERROR", "Request validation failed.",
            HttpStatus.BAD_REQUEST, new LinkedHashMap<>(fields));
    }

    private static ApiException notFound() {
        return new ApiException("NOT_FOUND", "API not found.", HttpStatus.NOT_FOUND);
    }
}

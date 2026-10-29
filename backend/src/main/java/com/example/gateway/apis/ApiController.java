package com.example.gateway.apis;

import com.example.gateway.common.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for the {@code /api/apis} endpoints (spec Phase 2).
 * Authentication is enforced by {@code SecurityConfig}; this controller reads
 * the caller's id from {@link SecurityUtils#currentUserId()} for owner scoping.
 *
 * <p>The {@code GET /{id}} endpoint calls {@link ApiService#getOwnedApi} which
 * returns the entity (for reuse by other services); the controller converts it
 * to an {@link ApiResponse} so the entity never reaches the wire.
 */
@RestController
@RequestMapping("/api/apis")
public class ApiController {

    private final ApiService apiService;

    public ApiController(ApiService apiService) {
        this.apiService = apiService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse> create(@Valid @RequestBody CreateApiRequest request) {
        ApiResponse response = apiService.create(SecurityUtils.currentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<ApiResponse> list() {
        return apiService.list(SecurityUtils.currentUserId());
    }

    @GetMapping("/{id}")
    public ApiResponse get(@PathVariable Long id) {
        Api api = apiService.getOwnedApi(SecurityUtils.currentUserId(), id);
        return toResponse(api);
    }

    @PatchMapping("/{id}")
    public ApiResponse update(@PathVariable Long id, @Valid @RequestBody UpdateApiRequest request) {
        return apiService.update(SecurityUtils.currentUserId(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        apiService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    private ApiResponse toResponse(Api api) {
        return new ApiResponse(api.getId(), api.getName(), api.getSlug(),
            api.getUpstreamBaseUrl(), api.getTimeoutMs(), api.isEnabled(), api.getCreatedAt());
    }
}

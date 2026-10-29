package com.example.gateway.keys;

import com.example.gateway.common.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for API keys.
 *
 * <ul>
 *   <li>{@code POST   /api/apis/{apiId}/keys}        — create key (201, full key shown once).</li>
 *   <li>{@code GET    /api/apis/{apiId}/keys}        — list keys (never the full key).</li>
 *   <li>{@code PATCH  /api/keys/{keyId} }            — update name/capacity/refillPerSec.</li>
 *   <li>{@code POST   /api/keys/{keyId}/revoke}      — revoke (204).</li>
 * </ul>
 *
 * <p>Every endpoint is scoped to the authenticated user via
 * {@link SecurityUtils#currentUserId()}; ownership of the parent API is
 * enforced in {@link KeyService} (404 — never 403 — on mismatch).
 */
@RestController
public class KeyController {

    private final KeyService keyService;

    public KeyController(KeyService keyService) {
        this.keyService = keyService;
    }

    @PostMapping("/api/apis/{apiId}/keys")
    public ResponseEntity<CreateKeyResponse> create(@PathVariable Long apiId,
                                                     @Valid @RequestBody CreateKeyRequest request) {
        CreateKeyResponse response = keyService.create(SecurityUtils.currentUserId(), apiId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/apis/{apiId}/keys")
    public List<KeyResponse> list(@PathVariable Long apiId) {
        return keyService.list(SecurityUtils.currentUserId(), apiId);
    }

    @PatchMapping("/api/keys/{keyId}")
    public KeyResponse update(@PathVariable Long keyId, @RequestBody UpdateKeyRequest request) {
        return keyService.update(SecurityUtils.currentUserId(), keyId, request);
    }

    @PostMapping("/api/keys/{keyId}/revoke")
    public ResponseEntity<Void> revoke(@PathVariable Long keyId) {
        keyService.revoke(SecurityUtils.currentUserId(), keyId);
        return ResponseEntity.noContent().build();
    }
}

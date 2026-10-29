package com.example.gateway.keys;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Request body for {@code POST /api/apis/{apiId}/keys}.
 *
 * <p>{@code name} is the only required field (1–100 chars, enforced by bean
 * validation). {@code capacity} defaults to {@code 10} and {@code refillPerSec}
 * defaults to {@code 5.00} server-side when omitted; range validation
 * (capacity 1–1000, refill 0.10–1000) runs in {@link KeyService} so the same
 * rules apply to PATCH. {@code expiresAt} is an optional ISO-8601 instant.
 */
public record CreateKeyRequest(
    @NotBlank @Size(max = 100) String name,
    Integer capacity,
    BigDecimal refillPerSec,
    Instant expiresAt
) {}

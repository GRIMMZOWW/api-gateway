package com.example.gateway.keys;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/** Request body for POST /api/apis/{apiId}/keys. */
public record CreateKeyRequest(
    @NotBlank @Size(max = 100) String name,
    Integer capacity,
    BigDecimal refillPerSec,
    Instant expiresAt
) {}

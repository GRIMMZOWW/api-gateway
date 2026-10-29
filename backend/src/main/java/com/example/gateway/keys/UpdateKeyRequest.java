package com.example.gateway.keys;

import java.math.BigDecimal;

/**
 * Request body for {@code PATCH /api/keys/{keyId}}.
 *
 * <p>All fields are optional; only the provided ones are applied. {@code status}
 * and {@code expiresAt} are intentionally not editable here — use the revoke
 * endpoint for status. Range validation (name 1–100, capacity 1–1000,
 * refill 0.10–1000) runs in {@link KeyService}.
 */
public record UpdateKeyRequest(
    String name,
    Integer capacity,
    BigDecimal refillPerSec
) {}

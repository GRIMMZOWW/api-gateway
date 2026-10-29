package com.example.gateway.keys;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Public representation of an API key. Exposes the display {@code prefix}, the
 * rate-limit settings, the status, and timestamps — but never the full key and
 * never the {@code key_hash}. Returned by list/update and embedded in
 * {@link CreateKeyResponse}.
 */
public record KeyResponse(
    Long id,
    String name,
    String prefix,
    int capacity,
    BigDecimal refillPerSec,
    String status,
    Instant expiresAt,
    Instant lastUsedAt,
    Instant createdAt
) {}

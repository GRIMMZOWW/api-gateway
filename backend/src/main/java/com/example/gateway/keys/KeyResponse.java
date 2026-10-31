package com.example.gateway.keys;

import java.math.BigDecimal;
import java.time.Instant;

/** Public representation of an API key. */
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

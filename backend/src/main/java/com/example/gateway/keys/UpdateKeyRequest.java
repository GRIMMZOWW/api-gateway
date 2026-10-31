package com.example.gateway.keys;

import java.math.BigDecimal;

/** Request body for PATCH /api/keys/{keyId}. */
public record UpdateKeyRequest(
    String name,
    Integer capacity,
    BigDecimal refillPerSec
) {}

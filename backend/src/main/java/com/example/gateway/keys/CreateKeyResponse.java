package com.example.gateway.keys;

/** Response for POST /api/apis/{apiId}/keys: the key record plus the full key string, shown exactly once. */
public record CreateKeyResponse(
    KeyResponse key,
    String fullKey
) {}

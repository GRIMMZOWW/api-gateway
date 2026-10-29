package com.example.gateway.keys;

/**
 * Response for {@code POST /api/apis/{apiId}/keys}: the key record plus the full
 * key string, shown exactly once.
 *
 * <p>The field is named {@code fullKey} (not {@code key}) to avoid colliding
 * with the {@link KeyResponse} field of the same name during JSON serialization
 * — Jackson cannot deserialize two same-named accessors on a record.
 *
 * <p>Persist {@code fullKey} client-side immediately; it cannot be recovered
 * from the stored {@code key_hash}.
 */
public record CreateKeyResponse(
    KeyResponse key,
    String fullKey
) {}

package com.example.gateway.auth;

import java.time.Instant;

/**
 * Public user shape returned from every auth response. Never exposes the password hash.
 */
public record UserResponse(Long id, String email, String displayName, String role, Instant createdAt) {}

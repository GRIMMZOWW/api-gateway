package com.example.gateway.auth;

import java.time.Instant;

/** Public user shape returned from every auth response. */
public record UserResponse(Long id, String email, String displayName, String role, Instant createdAt) {}

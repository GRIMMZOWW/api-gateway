package com.example.gateway.apis;

/** Request body for PATCH /api/apis/{id}. */
public record UpdateApiRequest(
    String name,
    String upstreamBaseUrl,
    Integer timeoutMs,
    Boolean enabled
) {}

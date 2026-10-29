package com.example.gateway.apis;

/**
 * Request body for {@code PATCH /api/apis/{id}}. All fields optional: only
 * non-null fields are applied. Business validation (timeout range, SSRF) runs
 * for any non-null {@code timeoutMs}/{@code upstreamBaseUrl}.
 */
public record UpdateApiRequest(
    String name,
    String upstreamBaseUrl,
    Integer timeoutMs,
    Boolean enabled
) {}

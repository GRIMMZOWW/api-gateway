package com.example.gateway.apis;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for POST /api/apis. */
public record CreateApiRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Size(max = 50) String slug,
    @NotBlank @Size(max = 500) String upstreamBaseUrl,
    Integer timeoutMs
) {}

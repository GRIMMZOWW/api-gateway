package com.example.gateway.apis;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/apis}. Bean Validation covers the basic
 * presence/length rules; business rules (slug format, timeout range, SSRF) are
 * enforced in {@link ApiService}.
 *
 * @param name            1–100 chars.
 * @param slug            3–50 chars, lowercase alnum/hyphen, not reserved.
 * @param upstreamBaseUrl http(s) URL, ≤500 chars.
 * @param timeoutMs       optional; 500–30000, defaults to {@code gateway.default-timeout-ms}.
 */
public record CreateApiRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Size(max = 50) String slug,
    @NotBlank @Size(max = 500) String upstreamBaseUrl,
    Integer timeoutMs
) {}

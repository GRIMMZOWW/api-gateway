package com.example.gateway.gateway;

import org.springframework.http.HttpHeaders;

/**
 * The result of forwarding a request to the upstream: the upstream's HTTP status,
 * response headers, and response body. The {@link GatewayController} copies these back
 * to the client (stripping hop-by-hop headers and {@code Content-Length}).
 *
 * <p>Any upstream status (including 4xx and 5xx) is a {@code SUCCESS} outcome — only
 * connection failures and timeouts are mapped to {@code 502}/{@code 504}.
 */
public record ForwardResult(int statusCode, HttpHeaders headers, byte[] body) {}

package com.example.gateway.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound to {@code cors.allowed-origin}. The single allowed frontend origin.
 */
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(String allowedOrigin) {

    public CorsProperties {
        if (allowedOrigin == null || allowedOrigin.isBlank()) {
            allowedOrigin = "http://localhost:5173";
        }
    }
}

package com.example.gateway.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;

/**
 * Bound to {@code jwt.*}. The compact constructor fails fast at application startup if
 * {@code JWT_SECRET} is missing or shorter than 32 bytes (spec security requirement #2).
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, int ttlMinutes) {

    public JwtProperties {
        if (ttlMinutes <= 0) {
            ttlMinutes = 60;
        }
        byte[] bytes = (secret == null ? "" : secret).getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                "jwt.secret (env JWT_SECRET) must be at least 32 bytes; got " + bytes.length
                + ". Generate one with: [Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))");
        }
    }
}

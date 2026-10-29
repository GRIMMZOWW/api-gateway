package com.example.gateway.keys;

/**
 * Lifecycle status of an {@link ApiKey}. {@code ACTIVE} keys may authenticate
 * gateway requests; {@code REVOKED} keys are permanently rejected and never
 * re-activated (a new key must be issued instead).
 */
public enum ApiKeyStatus {
    ACTIVE,
    REVOKED
}

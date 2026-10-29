package com.example.gateway.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtPropertiesTest {

    private static final String VALID = "0123456789abcdef0123456789abcdef0123456789abcdef"; // 48 bytes

    @Test
    void validSecretConstructs() {
        assertThatCode(() -> new JwtProperties(VALID, 60)).doesNotThrowAnyException();
    }

    @Test
    void shortSecretThrows() {
        assertThatThrownBy(() -> new JwtProperties("short", 60))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nullSecretThrows() {
        assertThatThrownBy(() -> new JwtProperties(null, 60))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void zeroTtlDefaultsTo60() {
        JwtProperties p = new JwtProperties(VALID, 0);
        assertThat(p.ttlMinutes()).isEqualTo(60);
    }
}

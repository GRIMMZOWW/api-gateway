package com.example.gateway.auth;

import com.example.gateway.common.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef";

    private JwtService service;

    @BeforeEach
    void setUp() {
        service = new JwtService(new JwtProperties(SECRET, 60));
    }

    @Test
    void roundTripPreservesUserIdAndRole() {
        String token = service.generate(42L, UserRole.USER);
        JwtService.ParsedToken parsed = service.parse(token);
        assertThat(parsed).isNotNull();
        assertThat(parsed.userId()).isEqualTo(42L);
        assertThat(parsed.role()).isEqualTo(UserRole.USER);
    }

    @Test
    void garbageTokenReturnsNull() {
        assertThat(service.parse("not-a-jwt")).isNull();
    }

    @Test
    void tokenSignedWithDifferentSecretReturnsNull() {
        JwtService other = new JwtService(new JwtProperties(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 60));
        String token = other.generate(1L, UserRole.USER);
        assertThat(service.parse(token)).isNull();
    }
}

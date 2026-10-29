package com.example.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 0 smoke test. Deliberately does NOT load the Spring context, so it runs
 * without MySQL running. Real @SpringBootTest integration tests arrive in Phase 1.
 */
class GatewayApplicationTests {

    @Test
    void applicationClassIsAnnotatedAsSpringBootApplication() {
        assertThat(GatewayApplication.class.getAnnotation(SpringBootApplication.class))
            .as("GatewayApplication must be annotated with @SpringBootApplication")
            .isNotNull();
    }
}

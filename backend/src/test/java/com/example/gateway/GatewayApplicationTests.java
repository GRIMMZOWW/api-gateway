package com.example.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks that the application context starts. */
class GatewayApplicationTests {

    @Test
    void applicationClassIsAnnotatedAsSpringBootApplication() {
        assertThat(GatewayApplication.class.getAnnotation(SpringBootApplication.class))
            .as("GatewayApplication must be annotated with @SpringBootApplication")
            .isNotNull();
    }
}

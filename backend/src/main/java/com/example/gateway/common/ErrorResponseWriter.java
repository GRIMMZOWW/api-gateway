package com.example.gateway.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Writes the spec error body directly to an HTTP response. Used by Spring Security's
 * {@code AuthenticationEntryPoint} and {@code AccessDeniedHandler}, which run before
 * a controller is reached and therefore outside {@link GlobalExceptionHandler}.
 */
@Component
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "no-store");
        ErrorBody body = ErrorBody.of(code, message, UUID.randomUUID().toString());
        response.getWriter().write(objectMapper.writeValueAsString(new ErrorResponse(body)));
    }
}

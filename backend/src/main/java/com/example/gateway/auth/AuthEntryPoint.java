package com.example.gateway.auth;

import com.example.gateway.common.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Returns 401 with the error body when a management endpoint is reached without a valid Bearer token. */
@Component
public class AuthEntryPoint implements AuthenticationEntryPoint {

    private final ErrorResponseWriter writer;

    public AuthEntryPoint(ErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        writer.write(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required.");
    }
}

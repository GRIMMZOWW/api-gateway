package com.example.gateway.auth;

import com.example.gateway.common.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns {@code 403} with the spec error body when an authenticated user lacks access.
 * IDOR (a resource owned by another user) is handled in services as {@code 404 NOT_FOUND},
 * per spec, so this handler only fires for genuinely forbidden actions.
 */
@Component
public class ForbiddenHandler implements AccessDeniedHandler {

    private final ErrorResponseWriter writer;

    public ForbiddenHandler(ErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        writer.write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this resource.");
    }
}

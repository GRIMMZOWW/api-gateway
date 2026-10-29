package com.example.gateway.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Helper to read the authenticated user id from the {@link SecurityContextHolder}.
 * The JWT filter stores the user id (Long) as the authentication principal.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof Long id) {
            return id;
        }
        return null;
    }
}

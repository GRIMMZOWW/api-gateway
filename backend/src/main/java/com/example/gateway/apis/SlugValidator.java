package com.example.gateway.apis;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates API slugs per spec security requirement #8.
 *
 * <p>Regex: {@code ^[a-z0-9]([a-z0-9-]{1,48}[a-z0-9])$} — 3 to 50 chars,
 * lowercase alphanumeric and internal hyphens; no leading/trailing hyphen.
 *
 * <p>Reserved slugs (rejected): {@code api}, {@code admin}, {@code gw},
 * {@code health}, {@code actuator}. These collide with gateway/management routes.
 *
 * <p>Pure component (no dependencies) so it can be unit-tested directly.
 */
@Component
public class SlugValidator {

    private static final Pattern SLUG_PATTERN =
        Pattern.compile("^[a-z0-9]([a-z0-9-]{1,48}[a-z0-9])$");

    private static final Set<String> RESERVED =
        Set.of("api", "admin", "gw", "health", "actuator");

    public boolean isValid(String slug) {
        if (slug == null || slug.isEmpty()) {
            return false;
        }
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            return false;
        }
        return !RESERVED.contains(slug);
    }
}

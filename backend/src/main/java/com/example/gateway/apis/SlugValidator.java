package com.example.gateway.apis;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/** Validates API slugs per. */
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

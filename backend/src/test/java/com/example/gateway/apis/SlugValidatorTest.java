package com.example.gateway.apis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit test for {@link SlugValidator}. No Spring context, no DB.
 */
class SlugValidatorTest {

    private final SlugValidator validator = new SlugValidator();

    @Test
    void acceptsValidSlugs() {
        assertTrue(validator.isValid("demo"));
        assertTrue(validator.isValid("my-api-1"));
        assertTrue(validator.isValid("abc"));
        assertTrue(validator.isValid("a1b2c3"));
        // 50 chars: lower bound of first char (1) + middle {1,48} max (48) + final (1) = 50.
        assertTrue(validator.isValid("a".repeat(49) + "z"));
    }

    @Test
    void rejectsUppercase() {
        assertFalse(validator.isValid("Demo"));
        assertFalse(validator.isValid("my-API"));
        assertFalse(validator.isValid("API"));
    }

    @Test
    void rejectsSpacesAndSymbols() {
        assertFalse(validator.isValid("my api"));
        assertFalse(validator.isValid("my_api"));
        assertFalse(validator.isValid("my.api"));
    }

    @Test
    void rejectsLeadingOrTrailingHyphen() {
        assertFalse(validator.isValid("-abc"));
        assertFalse(validator.isValid("abc-"));
        assertFalse(validator.isValid("-abc-"));
    }

    @Test
    void rejectsTooShort() {
        assertFalse(validator.isValid("ab"));
        assertFalse(validator.isValid("a"));
        assertFalse(validator.isValid(""));
    }

    @Test
    void rejectsTooLong() {
        // 51 chars: one past the max (50 = 1 + 48 middle + 1 final).
        assertFalse(validator.isValid("a".repeat(50) + "z"));
    }

    @Test
    void rejectsReservedSlugs() {
        assertFalse(validator.isValid("api"));
        assertFalse(validator.isValid("gw"));
        assertFalse(validator.isValid("health"));
        assertFalse(validator.isValid("admin"));
        assertFalse(validator.isValid("actuator"));
    }

    @Test
    void rejectsNull() {
        assertFalse(validator.isValid(null));
    }
}

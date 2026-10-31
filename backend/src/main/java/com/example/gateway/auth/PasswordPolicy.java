package com.example.gateway.auth;

import com.example.gateway.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Enforces the password policy: minimum 8 characters, at least one letter and one digit. */
@Component
public class PasswordPolicy {

    public void validate(String password) {
        if (password == null || password.length() < 8) {
            fail("password", "Password must be at least 8 characters long.");
        }
        if (password != null && !password.matches(".*[A-Za-z].*")) {
            fail("password", "Password must contain at least one letter.");
        }
        if (password != null && !password.matches(".*\\d.*")) {
            fail("password", "Password must contain at least one digit.");
        }
    }

    private void fail(String field, String message) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(field, message);
        throw new ApiException("VALIDATION_ERROR", "Request validation failed.", HttpStatus.BAD_REQUEST, fields);
    }
}

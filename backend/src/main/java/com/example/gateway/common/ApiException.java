package com.example.gateway.common;

import org.springframework.http.HttpStatus;

import java.util.Map;

/** An error with a code, an HTTP status, and optionally field messages or a Retry-After value. */
public class ApiException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final Map<String, String> fields;
    private final int retryAfterSeconds;

    public ApiException(String code, String message, HttpStatus status) {
        this(code, message, status, null, 0);
    }

    public ApiException(String code, String message, HttpStatus status, Map<String, String> fields) {
        this(code, message, status, fields, 0);
    }

    public ApiException(String code, String message, HttpStatus status, int retryAfterSeconds) {
        this(code, message, status, null, retryAfterSeconds);
    }

    public ApiException(String code, String message, HttpStatus status,
                        Map<String, String> fields, int retryAfterSeconds) {
        super(message);
        this.code = code;
        this.status = status;
        this.fields = fields;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String code() { return code; }
    public HttpStatus status() { return status; }
    public Map<String, String> fields() { return fields; }
    public int retryAfterSeconds() { return retryAfterSeconds; }
}

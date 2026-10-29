package com.example.gateway.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Single {@code @RestControllerAdvice} that maps every exception to the spec error body
 * {@code {"error":{"code","message","requestId","fields"}}}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        ErrorBody body = ex.fields() != null
            ? ErrorBody.withFields(ex.code(), ex.getMessage(), newRequestId(), ex.fields())
            : ErrorBody.of(ex.code(), ex.getMessage(), newRequestId());
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.status());
        if (ex.retryAfterSeconds() > 0) {
            builder.header("Retry-After", String.valueOf(ex.retryAfterSeconds()));
        }
        return builder.body(new ErrorResponse(body));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        ErrorBody body = ErrorBody.withFields("VALIDATION_ERROR", "Request validation failed.", newRequestId(), fields);
        return ResponseEntity.badRequest().body(new ErrorResponse(body));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            String path = v.getPropertyPath() != null ? v.getPropertyPath().toString() : "request";
            fields.putIfAbsent(path, v.getMessage());
        }
        ErrorBody body = ErrorBody.withFields("VALIDATION_ERROR", "Request validation failed.", newRequestId(), fields);
        return ResponseEntity.badRequest().body(new ErrorResponse(body));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return badRequest("BAD_REQUEST", "Malformed JSON request body.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest("BAD_REQUEST", "Invalid path or query parameter.");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        ErrorBody body = ErrorBody.of("UNAUTHENTICATED", "Authentication required.", newRequestId());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(body));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        ErrorBody body = ErrorBody.of("FORBIDDEN", "You do not have access to this resource.", newRequestId());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(body));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        // Log the real error server-side; return a generic message to the client (no leakage).
        log.error("Unhandled exception while handling a request", ex);
        ErrorBody body = ErrorBody.of("INTERNAL_ERROR", "An unexpected error occurred.", newRequestId());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(body));
    }

    private ResponseEntity<ErrorResponse> badRequest(String code, String message) {
        ErrorBody body = ErrorBody.of(code, message, newRequestId());
        return ResponseEntity.badRequest().body(new ErrorResponse(body));
    }

    private String newRequestId() {
        return UUID.randomUUID().toString();
    }
}

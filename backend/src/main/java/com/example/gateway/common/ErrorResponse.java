package com.example.gateway.common;

/**
 * Wraps an {@link ErrorBody} under the {@code "error"} JSON key so responses match
 * the spec format {@code {"error":{"code","message","requestId"}}}.
 */
public record ErrorResponse(ErrorBody error) {}

package com.example.gateway.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Body of the spec error envelope:
 * {@code {"code","message","requestId","fields"}}.
 *
 * <p>{@code fields} is a map of field → message, populated only for validation
 * errors. {@link JsonInclude} {@code NON_NULL} ensures the {@code fields} key is
 * omitted from the JSON when no field errors are present, so non-validation
 * responses serialise as {@code {"code","message","requestId"}}.
 *
 * <p>Constructed via the {@link #of} / {@link #withFields} factories used by
 * {@link GlobalExceptionHandler} and {@link ErrorResponseWriter}; wrapped by
 * {@link ErrorResponse} under the {@code "error"} JSON key.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorBody(
    String code,
    String message,
    String requestId,
    Map<String, String> fields
) {

    public static ErrorBody of(String code, String message, String requestId) {
        return new ErrorBody(code, message, requestId, null);
    }

    public static ErrorBody withFields(String code, String message, String requestId,
                                       Map<String, String> fields) {
        return new ErrorBody(code, message, requestId, fields);
    }
}

package com.example.gateway.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/** Body of the error envelope: {"code","message","requestId","fields"}. */
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

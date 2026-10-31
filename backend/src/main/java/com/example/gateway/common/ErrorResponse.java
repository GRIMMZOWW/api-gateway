package com.example.gateway.common;

/** Wraps an ErrorBody under the "error" key: {"error":{"code","message","requestId"}}. */
public record ErrorResponse(ErrorBody error) {}

package com.example.gateway.gateway;

import org.springframework.http.HttpHeaders;

/** The result of forwarding a request to the upstream: the upstream's HTTP status, response headers, and response body. */
public record ForwardResult(int statusCode, HttpHeaders headers, byte[] body) {}

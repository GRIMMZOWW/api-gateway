package com.example.gateway.auth;

public record AuthResponse(String token, UserResponse user) {}

package com.example.gateway.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank String password,
    @NotBlank @Size(max = 100) String displayName
) {}

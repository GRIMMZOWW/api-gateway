package com.example.gateway.auth;

import com.example.gateway.common.ApiException;
import com.example.gateway.ratelimit.TokenBucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginThrottle loginThrottle;

    public AuthController(AuthService authService, LoginThrottle loginThrottle) {
        this.authService = authService;
        this.loginThrottle = loginThrottle;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        TokenBucket.Decision decision = loginThrottle.tryConsume(clientIp(http));
        if (!decision.allowed()) {
            throw new ApiException("RATE_LIMITED", "Too many login attempts. Try again later.",
                HttpStatus.TOO_MANY_REQUESTS, decision.retryAfterSeconds());
        }
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserResponse me() {
        return authService.me();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

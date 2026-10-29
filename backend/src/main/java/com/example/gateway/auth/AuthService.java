package com.example.gateway.auth;

import com.example.gateway.common.ApiException;
import com.example.gateway.common.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       PasswordPolicy passwordPolicy, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        String displayName = request.displayName().trim();
        passwordPolicy.validate(request.password());
        if (userRepository.existsByEmail(email)) {
            throw new ApiException("EMAIL_TAKEN", "Email already registered.", HttpStatus.CONFLICT);
        }
        User user = new User(null, email, passwordEncoder.encode(request.password()),
            displayName, UserRole.USER, Instant.now());
        user = userRepository.save(user);
        return new AuthResponse(jwtService.generate(user.getId(), user.getRole()), toResponse(user));
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> invalidCredentials());
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return new AuthResponse(jwtService.generate(user.getId(), user.getRole()), toResponse(user));
    }

    public UserResponse me() {
        Long userId = SecurityUtils.currentUserId();
        if (userId == null) {
            throw new ApiException("UNAUTHENTICATED", "Authentication required.", HttpStatus.UNAUTHORIZED);
        }
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ApiException("UNAUTHENTICATED", "Authentication required.", HttpStatus.UNAUTHORIZED));
        return toResponse(user);
    }

    private ApiException invalidCredentials() {
        // Spec security requirement #7: login failure says only "invalid email or password".
        return new ApiException("INVALID_CREDENTIALS", "Invalid email or password.", HttpStatus.UNAUTHORIZED);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(),
            user.getRole().name(), user.getCreatedAt());
    }
}

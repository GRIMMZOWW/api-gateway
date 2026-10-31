package com.example.gateway.auth;

import com.example.gateway.common.ApiException;
import com.example.gateway.ratelimit.TokenBucket;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc test of AuthController. */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private LoginThrottle loginThrottle;

    // The @WebMvcTest slice scans @Component filters (JwtAuthFilter) but not the
    // @Service JwtService it depends on; mock it so the slice context loads.
    @MockBean
    private JwtService jwtService;

    private final UserResponse sampleUser =
        new UserResponse(1L, "joe@example.com", "Joe", "USER", Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    void registerHappyReturns201() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
            .thenReturn(new AuthResponse("tok", sampleUser));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"joe@example.com\",\"password\":\"Hunter12\",\"displayName\":\"Joe\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").value("tok"))
            .andExpect(jsonPath("$.user.email").value("joe@example.com"));
    }

    @Test
    void registerBlankEmailReturns400WithFields() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"\",\"password\":\"Hunter12\",\"displayName\":\"Joe\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.fields.email").exists());
    }

    @Test
    void registerDuplicateEmailReturns409() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
            .thenThrow(new ApiException("EMAIL_TAKEN", "Email already registered.", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"joe@example.com\",\"password\":\"Hunter12\",\"displayName\":\"Joe\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("EMAIL_TAKEN"));
    }

    @Test
    void registerShortPasswordReturns400WithFields() throws Exception {
        // Password policy runs in AuthService (mocked here as VALIDATION_ERROR).
        when(authService.register(any(RegisterRequest.class)))
            .thenThrow(new ApiException("VALIDATION_ERROR", "Request validation failed.",
                HttpStatus.BAD_REQUEST, Map.of("password", "Password must be at least 8 characters long.")));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"joe@example.com\",\"password\":\"ab1\",\"displayName\":\"Joe\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.fields.password").exists());
    }

    @Test
    void loginThrottledReturns429WithRetryAfter() throws Exception {
        when(loginThrottle.tryConsume(any())).thenReturn(new TokenBucket.Decision(false, 0, 60));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"joe@example.com\",\"password\":\"Hunter12\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"))
            .andExpect(header().string("Retry-After", "60"));
    }

    @Test
    void loginHappyReturns200() throws Exception {
        when(loginThrottle.tryConsume(any())).thenReturn(new TokenBucket.Decision(true, 4, 0));
        when(authService.login(any(LoginRequest.class))).thenReturn(new AuthResponse("tok", sampleUser));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"joe@example.com\",\"password\":\"Hunter12\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("tok"));
    }

    @Test
    void loginInvalidCredentialsReturns401() throws Exception {
        when(loginThrottle.tryConsume(any())).thenReturn(new TokenBucket.Decision(true, 4, 0));
        when(authService.login(any(LoginRequest.class)))
            .thenThrow(new ApiException("INVALID_CREDENTIALS", "Invalid email or password.", HttpStatus.UNAUTHORIZED));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"joe@example.com\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void meUnauthorizedReturns401() throws Exception {
        when(authService.me())
            .thenThrow(new ApiException("UNAUTHENTICATED", "Authentication required.", HttpStatus.UNAUTHORIZED));

        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void meHappyReturns200() throws Exception {
        when(authService.me()).thenReturn(sampleUser);

        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("joe@example.com"));
    }
}

package com.example.gateway.apis;

import com.example.gateway.auth.JwtService;
import com.example.gateway.common.ApiException;
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
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc test of {@link ApiController}. Security filters are disabled so the
 * controller is reached directly; {@link ApiService} is mocked. The
 * {@code @WebMvcTest} slice picks up {@code GlobalExceptionHandler}
 * ({@code @RestControllerAdvice}) so the spec error-body format is exercised.
 * No DB, no full Spring context.
 */
@WebMvcTest(ApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class ApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ApiService apiService;

    // The @WebMvcTest slice component-scans JwtAuthFilter (a Filter) but does not
    // scan @Service JwtService, so the filter cannot be constructed. Providing a
    // mock satisfies the dependency; filters are disabled anyway via addFilters=false.
    @MockBean
    private JwtService jwtService;

    private final ApiResponse sample = new ApiResponse(
        1L, "Demo", "demo", "https://upstream.example.com",
        10_000, true, Instant.parse("2026-01-01T00:00:00Z"));

    private final Api sampleEntity = new Api(
        1L, 7L, "Demo", "demo", "https://upstream.example.com",
        10_000, true, Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    void createHappyReturns201() throws Exception {
        when(apiService.create(any(), any(CreateApiRequest.class))).thenReturn(sample);

        mockMvc.perform(post("/api/apis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Demo\",\"slug\":\"demo\","
                    + "\"upstreamBaseUrl\":\"https://upstream.example.com\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.slug").value("demo"))
            .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void createBlankSlugReturns400WithFields() throws Exception {
        // @NotBlank on slug fires MethodArgumentNotValidException → VALIDATION_ERROR
        // with fields.slug, mapped by GlobalExceptionHandler.
        mockMvc.perform(post("/api/apis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Demo\",\"slug\":\"\","
                    + "\"upstreamBaseUrl\":\"https://upstream.example.com\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.fields.slug").exists());
    }

    @Test
    void createMissingUpstreamReturns400WithFields() throws Exception {
        mockMvc.perform(post("/api/apis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Demo\",\"slug\":\"demo\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.fields.upstreamBaseUrl").exists());
    }

    @Test
    void createSsrfBlockedReturns400() throws Exception {
        when(apiService.create(any(), any(CreateApiRequest.class)))
            .thenThrow(new ApiException("SSRF_BLOCKED", "Upstream URL is not allowed.",
                HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/apis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Demo\",\"slug\":\"demo\","
                    + "\"upstreamBaseUrl\":\"http://127.0.0.1\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("SSRF_BLOCKED"));
    }

    @Test
    void createApiLimitReturns400() throws Exception {
        when(apiService.create(any(), any(CreateApiRequest.class)))
            .thenThrow(new ApiException("API_LIMIT_REACHED",
                "You have reached the maximum of 10 APIs.", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/apis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Demo\",\"slug\":\"demo\","
                    + "\"upstreamBaseUrl\":\"https://upstream.example.com\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("API_LIMIT_REACHED"));
    }

    @Test
    void listReturns200() throws Exception {
        when(apiService.list(any())).thenReturn(List.of(sample));

        mockMvc.perform(get("/api/apis"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].slug").value("demo"))
            .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void getOwnedReturns200() throws Exception {
        // The GET endpoint calls getOwnedApi (returns entity) and converts to ApiResponse.
        when(apiService.getOwnedApi(any(), eq(1L))).thenReturn(sampleEntity);

        mockMvc.perform(get("/api/apis/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.slug").value("demo"))
            .andExpect(jsonPath("$.name").value("Demo"));
    }

    @Test
    void getSomeoneElsesReturns404() throws Exception {
        // Owner scoping: ApiService throws NOT_FOUND (never 403) for foreign-owned APIs.
        when(apiService.getOwnedApi(any(), eq(2L)))
            .thenThrow(new ApiException("NOT_FOUND", "API not found.", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/apis/2"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void updateHappyReturns200() throws Exception {
        when(apiService.update(any(), eq(1L), any(UpdateApiRequest.class)))
            .thenReturn(sample);

        mockMvc.perform(patch("/api/apis/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.slug").value("demo"));
    }

    @Test
    void updateTimeoutOutOfRangeReturns400WithFields() throws Exception {
        when(apiService.update(any(), eq(1L), any(UpdateApiRequest.class)))
            .thenThrow(new ApiException("VALIDATION_ERROR", "Request validation failed.",
                HttpStatus.BAD_REQUEST, Map.of("timeoutMs",
                    "timeoutMs must be between 500 and 30000.")));

        mockMvc.perform(patch("/api/apis/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"timeoutMs\":10}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.fields.timeoutMs").exists());
    }

    @Test
    void deleteReturns204() throws Exception {
        doNothing().when(apiService).delete(any(), eq(1L));

        mockMvc.perform(delete("/api/apis/1"))
            .andExpect(status().isNoContent());
    }

    @Test
    void deleteUnownedReturns404() throws Exception {
        org.mockito.Mockito.doThrow(
                new ApiException("NOT_FOUND", "API not found.", HttpStatus.NOT_FOUND))
            .when(apiService).delete(any(), eq(9L));

        mockMvc.perform(delete("/api/apis/9"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }
}

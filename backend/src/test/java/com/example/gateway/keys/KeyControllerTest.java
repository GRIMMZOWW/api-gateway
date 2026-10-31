package com.example.gateway.keys;

import com.example.gateway.auth.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc slice test of KeyController. */
@WebMvcTest(KeyController.class)
@AutoConfigureMockMvc(addFilters = false)
class KeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KeyService keyService;

    // The @WebMvcTest slice component-scans JwtAuthFilter (a Filter) but does not
    // scan @Service JwtService, so the filter cannot be constructed. Providing a
    // mock satisfies the dependency; filters are disabled anyway via addFilters=false.
    @MockBean
    private JwtService jwtService;

    private final Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");

    private KeyResponse sampleKeyResponse() {
        return new KeyResponse(1L, "prod-key", "gk_ab12cd34", 10, new BigDecimal("5.00"),
            "ACTIVE", null, null, createdAt);
    }

    @Test
    void createReturns201WithKeyRecordAndFullKey() throws Exception {
        CreateKeyResponse response = new CreateKeyResponse(sampleKeyResponse(),
            "gk_ab12cd34EfGhIjKlMnOpQrStUvWxYz1234567890ab");
        when(keyService.create(any(), eq(1L), any(CreateKeyRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/apis/1/keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"prod-key\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.key.id").value(1))
            .andExpect(jsonPath("$.key.name").value("prod-key"))
            .andExpect(jsonPath("$.key.prefix").value("gk_ab12cd34"))
            .andExpect(jsonPath("$.key.capacity").value(10))
            .andExpect(jsonPath("$.key.status").value("ACTIVE"))
            .andExpect(jsonPath("$.fullKey").value("gk_ab12cd34EfGhIjKlMnOpQrStUvWxYz1234567890ab"));
    }

    @Test
    void createBlankNameReturns400WithFields() throws Exception {
        // @NotBlank on CreateKeyRequest.name → MethodArgumentNotValidException → VALIDATION_ERROR.
        mockMvc.perform(post("/api/apis/1/keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.fields.name").exists());
    }

    @Test
    void listReturns200AndDoesNotExposeFullKey() throws Exception {
        when(keyService.list(any(), eq(1L))).thenReturn(List.of(sampleKeyResponse()));

        mockMvc.perform(get("/api/apis/1/keys"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].prefix").value("gk_ab12cd34"))
            .andExpect(jsonPath("$[0].status").value("ACTIVE"))
            // The list response is a KeyResponse array — neither the full key
            // nor a nested "key" wrapper may appear.
            .andExpect(jsonPath("$[0].fullKey").doesNotExist())
            .andExpect(jsonPath("$[0].key").doesNotExist());
    }

    @Test
    void updateReturns200WithKeyRecord() throws Exception {
        KeyResponse updated = new KeyResponse(1L, "prod-key", "gk_ab12cd34", 50, new BigDecimal("5.00"),
            "ACTIVE", null, null, createdAt);
        when(keyService.update(any(), eq(2L), any(UpdateKeyRequest.class))).thenReturn(updated);

        mockMvc.perform(patch("/api/keys/2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"capacity\":50}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.capacity").value(50));
    }

    @Test
    void revokeReturns204AndCallsService() throws Exception {
        mockMvc.perform(post("/api/keys/2/revoke"))
            .andExpect(status().isNoContent());

        verify(keyService).revoke(any(), eq(2L));
    }
}

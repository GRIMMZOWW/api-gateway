package com.example.gateway.keys;

import com.example.gateway.apis.Api;
import com.example.gateway.apis.ApiRepository;
import com.example.gateway.common.GatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeyLookupServiceTest {

    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private ApiRepository apiRepository;
    @Mock private GatewayProperties properties;

    private KeyLookupService service;

    private final Api demoApi = new Api(10L, 1L, "Demo", "demo",
        "https://httpbin.org", 10000, true, Instant.parse("2026-01-01T00:00:00Z"));
    private final ApiKey demoKey = new ApiKey(20L, 10L, "mobile", "gk_ab12cd34",
        "hash123", 10, new BigDecimal("5.00"), ApiKeyStatus.ACTIVE, null, null,
        Instant.parse("2026-01-01T00:00:00Z"), null);

    @BeforeEach
    void setUp() {
        when(properties.cacheMaxEntries()).thenReturn(100);
        when(properties.cacheTtlSeconds()).thenReturn(60);
        service = new KeyLookupService(apiKeyRepository, apiRepository, properties);
    }

    @Test
    void cacheMissLoadsFromDbAndCaches() {
        when(apiKeyRepository.findByKeyHash("hash123")).thenReturn(Optional.of(demoKey));
        when(apiRepository.findById(10L)).thenReturn(Optional.of(demoApi));

        Optional<CachedKey> first = service.find("hash123");
        Optional<CachedKey> second = service.find("hash123");

        assertThat(first).isPresent();
        assertThat(first.get().keyId()).isEqualTo(20L);
        assertThat(first.get().slug()).isEqualTo("demo");
        assertThat(second).contains(first.get()); // cached
        verify(apiKeyRepository, times(1)).findByKeyHash("hash123"); // only once
        verify(apiRepository, times(1)).findById(10L);
    }

    @Test
    void unknownHashIsNotCached() {
        when(apiKeyRepository.findByKeyHash("nope")).thenReturn(Optional.empty());

        assertThat(service.find("nope")).isEmpty();
        assertThat(service.find("nope")).isEmpty(); // miss again
        verify(apiKeyRepository, times(2)).findByKeyHash("nope");
    }

    @Test
    void evictKeyForcesReload() {
        when(apiKeyRepository.findByKeyHash("hash123")).thenReturn(Optional.of(demoKey));
        when(apiRepository.findById(10L)).thenReturn(Optional.of(demoApi));

        service.find("hash123");            // load + cache
        service.evictKey("hash123");        // invalidate
        service.find("hash123");            // reload from DB

        verify(apiKeyRepository, times(2)).findByKeyHash("hash123");
    }

    @Test
    void evictApiInvalidatesAllKeysForThatApi() {
        when(apiKeyRepository.findByKeyHash("h1")).thenReturn(Optional.of(demoKey));
        when(apiRepository.findById(10L)).thenReturn(Optional.of(demoApi));

        service.find("h1");                 // cached, apiId=10
        service.evictApi(10L);              // invalidate all keys for api 10
        when(apiKeyRepository.findByKeyHash("h1")).thenReturn(Optional.of(demoKey));
        service.find("h1");                 // miss -> reload

        verify(apiKeyRepository, times(2)).findByKeyHash("h1");
    }

    @Test
    void keyWithoutApiIsInvalid() {
        when(apiKeyRepository.findByKeyHash("orphan")).thenReturn(Optional.of(demoKey));
        when(apiRepository.findById(10L)).thenReturn(Optional.empty());

        assertThat(service.find("orphan")).isEmpty();
    }
}

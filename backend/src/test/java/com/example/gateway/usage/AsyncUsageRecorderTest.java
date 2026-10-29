package com.example.gateway.usage;

import com.example.gateway.common.GatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncUsageRecorderTest {

    @Mock private GatewayProperties properties;
    @Mock private JdbcTemplate jdbcTemplate;

    private UsageEvent event(long i) {
        return new UsageEvent(10L, 20L, "req-" + i, "GET", "/get", 200, 5,
            UsageOutcome.SUCCESS, Instant.now());
    }

    @BeforeEach
    void setUp() {
        when(properties.usageQueueCapacity()).thenReturn(10_000);
        when(properties.usageBatchSize()).thenReturn(100);
        when(properties.usageFlushIntervalMs()).thenReturn(1_000L);
    }

    @Test
    void fiveHundredEventsProduceFiveHundredRows() {
        AsyncUsageRecorder recorder = new AsyncUsageRecorder(properties, jdbcTemplate);
        for (int i = 0; i < 500; i++) {
            recorder.record(event(i));
        }
        recorder.flushAll();

        ArgumentCaptor<BatchPreparedStatementSetter> captor =
            ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbcTemplate, atLeast(1)).batchUpdate(anyString(), captor.capture());
        int inserted = captor.getAllValues().stream()
            .mapToInt(BatchPreparedStatementSetter::getBatchSize)
            .sum();
        assertThat(inserted).isEqualTo(500);
        assertThat(recorder.droppedEvents()).isZero();
    }

    @Test
    void queueFullDropsExcessEvents() {
        when(properties.usageQueueCapacity()).thenReturn(5);
        AsyncUsageRecorder recorder = new AsyncUsageRecorder(properties, jdbcTemplate);
        for (int i = 0; i < 7; i++) {
            recorder.record(event(i));
        }
        assertThat(recorder.queueSize()).isEqualTo(5);
        assertThat(recorder.droppedEvents()).isEqualTo(2);
    }

    @Test
    void batchesAreChunkedToBatchSize() {
        when(properties.usageBatchSize()).thenReturn(50);
        AsyncUsageRecorder recorder = new AsyncUsageRecorder(properties, jdbcTemplate);
        for (int i = 0; i < 120; i++) {
            recorder.record(event(i));
        }
        recorder.flushAll();

        ArgumentCaptor<BatchPreparedStatementSetter> captor =
            ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbcTemplate, atLeast(1)).batchUpdate(anyString(), captor.capture());
        // 120 events in chunks of 50 -> batches of 50, 50, 20 -> max batch size 50.
        int maxBatch = captor.getAllValues().stream()
            .mapToInt(BatchPreparedStatementSetter::getBatchSize)
            .max().orElse(0);
        assertThat(maxBatch).isLessThanOrEqualTo(50);
        int total = captor.getAllValues().stream()
            .mapToInt(BatchPreparedStatementSetter::getBatchSize)
            .sum();
        assertThat(total).isEqualTo(120);
    }

    @Test
    void updatesLastUsedAtPerKey() {
        AsyncUsageRecorder recorder = new AsyncUsageRecorder(properties, jdbcTemplate);
        recorder.record(event(1));
        recorder.record(event(2));
        recorder.flushAll();

        // Both events share apiKeyId=20 -> one last_used_at update.
        verify(jdbcTemplate).update(eq("UPDATE api_keys SET last_used_at = ? WHERE id = ?"),
            any(), eq(20L));
    }
}

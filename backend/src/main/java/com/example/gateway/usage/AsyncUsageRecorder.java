package com.example.gateway.usage;

import com.example.gateway.common.GatewayProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Async batched writer for {@code usage_logs} (spec core logic #5).
 *
 * <ul>
 *   <li>The request thread calls {@link #record}, which {@code offer}s onto a bounded
 *       {@link ArrayBlockingQueue} (capacity {@code gateway.usage-queue-capacity}) and
 *       never waits for the database.</li>
 *   <li>If the queue is full, the event is dropped and {@code droppedUsageEvents} is
 *       incremented (exposed on the actuator health endpoint by
 *       {@link UsageWriterHealthIndicator}).</li>
 *   <li>One daemon background thread drains the queue: it waits up to
 *       {@code gateway.usage-flush-interval-ms} for the first event, then drains up to
 *       {@code gateway.usage-batch-size} events and writes them with one
 *       {@code JdbcTemplate.batchUpdate}. In the same pass it updates
 *       {@code api_keys.last_used_at} for the keys seen.</li>
 *   <li>On application shutdown, the remaining events are flushed before exit.</li>
 * </ul>
 */
@Component
public class AsyncUsageRecorder implements UsageRecorder {

    private static final Logger log = LoggerFactory.getLogger(AsyncUsageRecorder.class);

    private static final String INSERT_SQL =
        "INSERT INTO usage_logs (api_id, api_key_id, request_id, method, path, " +
        "status_code, latency_ms, outcome, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_LAST_USED_SQL =
        "UPDATE api_keys SET last_used_at = ? WHERE id = ?";

    private final ArrayBlockingQueue<UsageEvent> queue;
    private final JdbcTemplate jdbcTemplate;
    private final int batchSize;
    private final long flushIntervalMs;
    private final AtomicLong droppedEvents = new AtomicLong();
    private final Thread worker;

    public AsyncUsageRecorder(GatewayProperties properties, JdbcTemplate jdbcTemplate) {
        this.queue = new ArrayBlockingQueue<>(properties.usageQueueCapacity());
        this.jdbcTemplate = jdbcTemplate;
        this.batchSize = properties.usageBatchSize();
        this.flushIntervalMs = properties.usageFlushIntervalMs();
        this.worker = new Thread(this::run, "usage-writer");
        this.worker.setDaemon(true);
    }

    @PostConstruct
    void start() {
        worker.start();
    }

    @Override
    public void record(UsageEvent event) {
        if (!queue.offer(event)) {
            droppedEvents.incrementAndGet();
        }
    }

    public long droppedEvents() {
        return droppedEvents.get();
    }

    public int queueSize() {
        return queue.size();
    }

    private void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                UsageEvent first = queue.poll(flushIntervalMs, TimeUnit.MILLISECONDS);
                List<UsageEvent> batch = new ArrayList<>(batchSize);
                if (first != null) {
                    batch.add(first);
                }
                queue.drainTo(batch, batchSize - 1);
                if (!batch.isEmpty()) {
                    flush(batch);
                }
            }
        } catch (InterruptedException e) {
            // interrupted on shutdown; fall through to the final flush
        }
        flushAll();
    }

    /** Drains the entire queue in batch-size chunks. Used on shutdown and by tests. */
    void flushAll() {
        List<UsageEvent> batch = new ArrayList<>(batchSize);
        while (true) {
            batch.clear();
            queue.drainTo(batch, batchSize);
            if (batch.isEmpty()) {
                break;
            }
            flush(batch);
        }
    }

    private void flush(List<UsageEvent> batch) {
        // Snapshot so the setter's getBatchSize() is stable even if the caller mutates
        // the source list afterwards (JdbcTemplate reads the setter synchronously in
        // production, but tests inspect captured setters after the fact).
        List<UsageEvent> events = List.copyOf(batch);
        try {
            jdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    UsageEvent e = events.get(i);
                    ps.setLong(1, e.apiId());
                    if (e.apiKeyId() == null) {
                        ps.setNull(2, java.sql.Types.BIGINT);
                    } else {
                        ps.setLong(2, e.apiKeyId());
                    }
                    ps.setString(3, e.requestId());
                    ps.setString(4, e.method());
                    ps.setString(5, e.path());
                    ps.setInt(6, e.statusCode());
                    ps.setInt(7, e.latencyMs());
                    ps.setString(8, e.outcome().name());
                    ps.setTimestamp(9, Timestamp.from(e.createdAt()));
                }

                @Override
                public int getBatchSize() {
                    return events.size();
                }
            });
            updateLastUsed(events);
        } catch (RuntimeException e) {
            log.warn("Failed to flush usage batch of {}: {}", events.size(), e.getMessage());
        }
    }

    private void updateLastUsed(List<UsageEvent> batch) {
        Map<Long, Instant> lastUsedByKey = new HashMap<>();
        for (UsageEvent e : batch) {
            if (e.apiKeyId() != null) {
                lastUsedByKey.merge(e.apiKeyId(), e.createdAt(),
                    (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        for (Map.Entry<Long, Instant> entry : lastUsedByKey.entrySet()) {
            jdbcTemplate.update(UPDATE_LAST_USED_SQL, Timestamp.from(entry.getValue()), entry.getKey());
        }
    }

    @PreDestroy
    void shutdown() {
        worker.interrupt();
        try {
            worker.join(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

package com.example.gateway.usage;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Exposes the usage writer's {@code droppedUsageEvents} counter and current queue size on
 * the actuator health endpoint (spec core logic #5: "If the queue is full, drop the event
 * and increment a droppedUsageEvents counter exposed on the actuator health endpoint").
 */
@Component
public class UsageWriterHealthIndicator implements HealthIndicator {

    private final AsyncUsageRecorder recorder;

    public UsageWriterHealthIndicator(AsyncUsageRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public Health health() {
        long dropped = recorder.droppedEvents();
        int queueSize = recorder.queueSize();
        return Health.up()
            .withDetail("droppedUsageEvents", dropped)
            .withDetail("queueSize", queueSize)
            .build();
    }
}

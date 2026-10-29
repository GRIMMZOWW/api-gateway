package com.example.gateway.usage;

/**
 * Records one gateway usage event. The request thread calls {@link #record} and never
 * waits for the database; the async implementation ({@link AsyncUsageRecorder}) batches
 * inserts on a background thread.
 */
public interface UsageRecorder {

    void record(UsageEvent event);
}

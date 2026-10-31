package com.example.gateway.usage;

/** Records one gateway usage event. */
public interface UsageRecorder {

    void record(UsageEvent event);
}

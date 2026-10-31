package com.example.gateway.ratelimit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Holds one TokenBucket per active key id in a ConcurrentHashMap. */
@Component
public class RateLimiterRegistry {

    private static final long ONE_HOUR_NANOS = 60L * 60 * 1_000_000_000L;
    private static final long TEN_MINUTES_MS = 10L * 60 * 1000;

    private final ConcurrentHashMap<Long, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> lastAccessNanos = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public RateLimiterRegistry() {
        this(System::nanoTime);
    }

    /** Test constructor with an injectable clock. */
    public RateLimiterRegistry(LongSupplier clock) {
        this.clock = clock;
    }

    public TokenBucket.Decision tryConsume(Long keyId, int capacity, double refillPerSec) {
        // compute() is atomic per key: only ONE thread can create or replace the bucket,
        // so a burst of simultaneous first requests can never create several full buckets.
        // If the limits changed (PATCH), the stale bucket is replaced by a new one.
        TokenBucket bucket = buckets.compute(keyId, (id, existing) ->
                (existing == null
                        || existing.capacity() != capacity
                        || existing.refillPerSec() != refillPerSec)
                        ? new TokenBucket(capacity, refillPerSec, clock)
                        : existing);
        lastAccessNanos.put(keyId, clock.getAsLong());
        return bucket.tryConsume();
    }

    /** Removes the bucket for a key (used by tests). */
    public void evict(Long keyId) {
        buckets.remove(keyId);
        lastAccessNanos.remove(keyId);
    }

    public int size() {
        return buckets.size();
    }

    @Scheduled(fixedDelay = TEN_MINUTES_MS)
    public void cleanupIdle() {
        long now = clock.getAsLong();
        lastAccessNanos.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > ONE_HOUR_NANOS) {
                buckets.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }
}
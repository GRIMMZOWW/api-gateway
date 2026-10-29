package com.example.gateway.ratelimit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Holds one {@link TokenBucket} per active key id in a {@link ConcurrentHashMap} (spec
 * core logic #2). Each gateway request asks for one token via {@link #tryConsume}.
 *
 * <p>Self-healing on limit change: if a key's {@code capacity}/{@code refillPerSec} was
 * changed via PATCH, the next request detects the mismatch and rebuilds the bucket with
 * the new limits — so the new limits take effect immediately without an explicit eviction
 * hook (equivalent to the spec's "remove its bucket so the next request creates a new one").
 *
 * <p>A scheduled job runs every 10 minutes and removes buckets unused for 1 hour.
 *
 * <p>Known limit (README): buckets live in one JVM, so this works for a single gateway
 * instance only. The stretch goal moves them to Redis with an atomic Lua script.
 */
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
        TokenBucket bucket = buckets.get(keyId);
        // Self-heal: if the limits changed (PATCH), drop the stale bucket.
        if (bucket != null && (bucket.capacity() != capacity || bucket.refillPerSec() != refillPerSec)) {
            buckets.remove(keyId);
            lastAccessNanos.remove(keyId);
            bucket = null;
        }
        if (bucket == null) {
            bucket = new TokenBucket(capacity, refillPerSec, clock);
            buckets.put(keyId, bucket);
        }
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

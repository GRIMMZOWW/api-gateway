package com.example.gateway.ratelimit;

import java.util.function.LongSupplier;

/**
 * A thread-safe token bucket rate limiter. One instance per key (or per login IP).
 *
 * <p>Fields: {@code capacity} (burst size), {@code refillPerSec} (tokens added per
 * second), {@code tokens} (starts full), {@code lastRefillNanos}.
 *
 * <p>{@link #tryConsume()} is {@code synchronized} on the bucket. It reads the clock,
 * adds {@code elapsedSeconds * refillPerSec} (capped at {@code capacity}), then either
 * consumes one token (allow) or computes a {@code Retry-After} (deny).
 *
 * <p>An injectable {@link LongSupplier} clock lets unit tests advance time without
 * {@code Thread.sleep}.
 */
public class TokenBucket {

    private final int capacity;
    private final double refillPerSec;
    private double tokens;
    private long lastRefillNanos;
    private final LongSupplier clock;

    public TokenBucket(int capacity, double refillPerSec) {
        this(capacity, refillPerSec, System::nanoTime);
    }

    public TokenBucket(int capacity, double refillPerSec, LongSupplier clock) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        if (refillPerSec <= 0) {
            throw new IllegalArgumentException("refillPerSec must be > 0");
        }
        this.capacity = capacity;
        this.refillPerSec = refillPerSec;
        this.clock = clock;
        this.tokens = capacity;
        this.lastRefillNanos = clock.getAsLong();
    }

    /**
     * Attempts to consume one token.
     *
     * @return a {@link Decision}; on allow {@code remaining} is {@code floor(tokens)},
     *         on deny {@code retryAfterSeconds} is {@code ceil((1 - tokens) / refillPerSec)},
     *         minimum 1.
     */
    public synchronized Decision tryConsume() {
        long now = clock.getAsLong();
        long elapsedNanos = now - lastRefillNanos;
        if (elapsedNanos > 0) {
            double elapsedSec = elapsedNanos / 1_000_000_000.0;
            tokens = Math.min(capacity, tokens + elapsedSec * refillPerSec);
            lastRefillNanos = now;
        }
        if (tokens >= 1.0) {
            tokens -= 1.0;
            int remaining = (int) Math.floor(tokens);
            return new Decision(true, remaining, 0);
        }
        int retryAfter = (int) Math.max(1, Math.ceil((1.0 - tokens) / refillPerSec));
        return new Decision(false, 0, retryAfter);
    }

    public int capacity() {
        return capacity;
    }

    public double refillPerSec() {
        return refillPerSec;
    }

    public record Decision(boolean allowed, int remaining, int retryAfterSeconds) {}
}

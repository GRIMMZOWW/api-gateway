package com.example.gateway.auth;

import com.example.gateway.ratelimit.TokenBucket;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Throttles login attempts to 5 per minute per client IP using a TokenBucket per IP. */
@Component
public class LoginThrottle {

    private static final int CAPACITY = 5;
    private static final double REFILL_PER_SEC = 5.0 / 60.0; // 5 per minute

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public LoginThrottle() {
        this(System::nanoTime);
    }

    /** Test constructor with an injectable clock. */
    public LoginThrottle(LongSupplier clock) {
        this.clock = clock;
    }

    public TokenBucket.Decision tryConsume(String ip) {
        TokenBucket bucket = buckets.computeIfAbsent(ip, k -> new TokenBucket(CAPACITY, REFILL_PER_SEC, clock));
        return bucket.tryConsume();
    }
}

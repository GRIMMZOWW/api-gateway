package com.example.gateway.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenBucketTest {

    private long nanos;
    private LongSupplier clock;

    @BeforeEach
    void setUp() {
        nanos = 0L;
        clock = () -> nanos;
    }

    @Test
    void startsFull() {
        TokenBucket bucket = new TokenBucket(10, 0.1, clock);
        TokenBucket.Decision d = bucket.tryConsume();
        assertThat(d.allowed()).isTrue();
        assertThat(d.remaining()).isEqualTo(9);
    }

    @Test
    void allowsExactlyCapacityThenDenies() {
        TokenBucket bucket = new TokenBucket(5, 0.10, clock);
        for (int i = 0; i < 5; i++) {
            assertThat(bucket.tryConsume().allowed()).isTrue();
        }
        TokenBucket.Decision denied = bucket.tryConsume();
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void refillsOverTime() {
        TokenBucket bucket = new TokenBucket(10, 10.0, clock);
        for (int i = 0; i < 10; i++) {
            bucket.tryConsume();
        }
        assertThat(bucket.tryConsume().allowed()).isFalse();
        nanos += 1_000_000_000L; // 1 second -> refills 10 tokens
        assertThat(bucket.tryConsume().allowed()).isTrue();
    }

    @Test
    void neverExceedsCapacity() {
        TokenBucket bucket = new TokenBucket(5, 1000.0, clock);
        nanos += 10_000_000_000L; // huge elapsed, but tokens capped at capacity
        TokenBucket.Decision d = bucket.tryConsume();
        assertThat(d.allowed()).isTrue();
        assertThat(d.remaining()).isEqualTo(4); // tokens=5, consume 1 -> floor(4)
    }

    @Test
    void retryAfterIsCorrect() {
        TokenBucket bucket = new TokenBucket(1, 0.10, clock);
        assertThat(bucket.tryConsume().allowed()).isTrue(); // tokens -> 0
        TokenBucket.Decision denied = bucket.tryConsume();
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isEqualTo(10); // ceil((1-0)/0.10)
    }

    @Test
    void rejectsInvalidConfig() {
        assertThatThrownBy(() -> new TokenBucket(0, 1.0, clock)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucket(1, 0.0, clock)).isInstanceOf(IllegalArgumentException.class);
    }
}

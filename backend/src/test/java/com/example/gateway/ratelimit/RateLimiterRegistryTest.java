package com.example.gateway.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterRegistryTest {

    @Test
    void allowsUpToCapacityThenDenies() {
        RateLimiterRegistry registry = new RateLimiterRegistry(() -> 0L);
        for (int i = 0; i < 5; i++) {
            assertThat(registry.tryConsume(1L, 5, 0.10).allowed()).isTrue();
        }
        assertThat(registry.tryConsume(1L, 5, 0.10).allowed()).isFalse();
    }

    @Test
    void selfHealsWhenLimitsChange() {
        RateLimiterRegistry registry = new RateLimiterRegistry(() -> 0L); // frozen clock -> no refill
        for (int i = 0; i < 5; i++) {
            assertThat(registry.tryConsume(1L, 5, 0.10).allowed()).isTrue();
        }
        assertThat(registry.tryConsume(1L, 5, 0.10).allowed()).isFalse(); // capacity 5 drained
        // PATCH raises capacity to 10 -> the next request detects the mismatch and rebuilds.
        for (int i = 0; i < 10; i++) {
            assertThat(registry.tryConsume(1L, 10, 0.10).allowed()).isTrue();
        }
        assertThat(registry.tryConsume(1L, 10, 0.10).allowed()).isFalse(); // capacity 10 drained
    }

    @Test
    void separateKeysHaveSeparateBuckets() {
        RateLimiterRegistry registry = new RateLimiterRegistry(() -> 0L);
        for (int i = 0; i < 3; i++) {
            registry.tryConsume(1L, 3, 0.10);
        }
        assertThat(registry.tryConsume(1L, 3, 0.10).allowed()).isFalse();
        // A different key has its own full bucket.
        assertThat(registry.tryConsume(2L, 3, 0.10).allowed()).isTrue();
    }

    @Test
    void evictRemovesBucket() {
        RateLimiterRegistry registry = new RateLimiterRegistry(() -> 0L);
        registry.tryConsume(1L, 5, 0.10);
        assertThat(registry.size()).isEqualTo(1);
        registry.evict(1L);
        assertThat(registry.size()).isZero();
    }

    /**
     * Spec unit test: 100 threads on one bucket with capacity 50 allow exactly 50.
     * The bucket's {@code tryConsume} is {@code synchronized}, so concurrent consumes are
     * serialized and never over-allow.
     */
    @Test
    void concurrency100ThreadsCapacity50AllowsExactly50() throws InterruptedException {
        RateLimiterRegistry registry = new RateLimiterRegistry(() -> 0L); // frozen clock -> no refill
        int threads = 100;
        int capacity = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger denied = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (registry.tryConsume(1L, capacity, 0.10).allowed()) {
                    allowed.incrementAndGet();
                } else {
                    denied.incrementAndGet();
                }
            });
        }
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(allowed.get()).isEqualTo(50);
        assertThat(denied.get()).isEqualTo(50);
    }

    @Test
    void cleanupIdleRemovesBucketsUnusedForOneHour() {
        long[] clock = {0L};
        RateLimiterRegistry registry = new RateLimiterRegistry(() -> clock[0]);
        registry.tryConsume(1L, 5, 0.10);          // lastAccess = 0
        clock[0] = 1_800_000_000_000L;             // 30 minutes
        registry.tryConsume(2L, 5, 0.10);          // lastAccess = 30 min
        clock[0] = 3_700_000_000_000L;             // ~61.6 minutes
        registry.cleanupIdle();
        // key 1 idle ~61.6 min > 1h -> removed; key 2 idle ~31.6 min < 1h -> kept.
        assertThat(registry.size()).isEqualTo(1);
    }
}

package com.capstone.agenticrag.security;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

/**
 * In-memory token-bucket rate limiter keyed by an arbitrary string (e.g. {@code chat:<userId>}). Suitable for a single
 * backend instance; a multi-instance deployment should move buckets to a shared store such as Redis.
 */
@Component
public class RateLimiter {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(100_000)
            .build();

    /** Returns true if a request is allowed under a limit of {@code perMinute} requests per minute. */
    public boolean tryAcquire(String key, int perMinute) {
        if (perMinute <= 0) {
            return true;
        }
        return buckets.get(key + "@" + perMinute, k -> new Bucket(perMinute)).tryTake();
    }

    private static final class Bucket {
        private final double capacity;
        private final double refillPerNano;
        private double tokens;
        private long lastRefill;

        Bucket(int perMinute) {
            this.capacity = perMinute;
            this.refillPerNano = perMinute / (double) TimeUnit.MINUTES.toNanos(1);
            this.tokens = perMinute;
            this.lastRefill = System.nanoTime();
        }

        synchronized boolean tryTake() {
            long now = System.nanoTime();
            tokens = Math.min(capacity, tokens + (now - lastRefill) * refillPerNano);
            lastRefill = now;
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }
    }
}

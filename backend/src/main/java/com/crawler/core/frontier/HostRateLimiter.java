package com.crawler.core.frontier;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class HostRateLimiter {

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final double refillRatePerSecond;
    private final double maxCapacity;

    public HostRateLimiter() {
        // Defaults: 2 requests per second per host, burst limit 3
        this(2.0, 3.0);
    }

    public HostRateLimiter(double refillRatePerSecond, double maxCapacity) {
        this.refillRatePerSecond = refillRatePerSecond;
        this.maxCapacity = maxCapacity;
    }

    /**
     * Acquires a permit for the specified host, sleeping if tokens are exhausted.
     */
    public void acquire(String host) throws InterruptedException {
        if (host == null || host.isBlank()) {
            return;
        }
        TokenBucket bucket = buckets.computeIfAbsent(
                host,
                k -> new TokenBucket(refillRatePerSecond, maxCapacity)
        );
        bucket.acquire();
    }

    private static class TokenBucket {
        private final double refillRate;
        private final double capacity;
        private double tokens;
        private long lastRefillTimestamp;
        private final ReentrantLock lock = new ReentrantLock();

        TokenBucket(double refillRate, double capacity) {
            this.refillRate = refillRate;
            this.capacity = capacity;
            this.tokens = capacity;
            this.lastRefillTimestamp = System.nanoTime();
        }

        void acquire() throws InterruptedException {
            while (true) {
                long waitTimeMs = 0;
                lock.lock();
                try {
                    refill();
                    if (tokens >= 1.0) {
                        tokens -= 1.0;
                        return;
                    }
                    // Calculate wait duration until 1 token is available
                    double deficit = 1.0 - tokens;
                    waitTimeMs = (long) Math.ceil((deficit / refillRate) * 1000.0);
                } finally {
                    lock.unlock();
                }

                if (waitTimeMs > 0) {
                    Thread.sleep(waitTimeMs);
                }
            }
        }

        private void refill() {
            long now = System.nanoTime();
            double secondsElapsed = (now - lastRefillTimestamp) / 1_000_000_000.0;
            if (secondsElapsed > 0) {
                tokens = Math.min(capacity, tokens + (secondsElapsed * refillRate));
                lastRefillTimestamp = now;
            }
        }
    }
}
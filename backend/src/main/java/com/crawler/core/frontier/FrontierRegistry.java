package com.crawler.core.frontier;

import com.crawler.core.model.CrawlTarget;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class FrontierRegistry {

    private final BlockingQueue<CrawlTarget> queue = new LinkedBlockingQueue<>();
    private final Set<String> visitedUris = ConcurrentHashMap.newKeySet();
    private final BloomFilter bloomFilter;
    private final AtomicInteger inFlightCount = new AtomicInteger(0);

    public FrontierRegistry() {
        // Capacity for 250,000 URLs with a 1% false positive rate (~298 KB in memory)
        this.bloomFilter = new BloomFilter(250_000, 0.01);
    }

    public FrontierRegistry(int expectedCapacity, double fpp) {
        this.bloomFilter = new BloomFilter(expectedCapacity, fpp);
    }

    /**
     * Enqueues a target if it hasn't been crawled yet.
     * Uses BloomFilter as an initial fast rejection check before checking visitedUris.
     */
    public boolean offer(CrawlTarget target) {
        String normalizedKey = target.getUri().normalize().toString();

        // 1. If BloomFilter definitely says NO, it was never seen before
        if (!bloomFilter.mightContain(normalizedKey)) {
            bloomFilter.add(normalizedKey);
            visitedUris.add(normalizedKey);
            queue.offer(target);
            return true;
        }

        // 2. If BloomFilter says MAYBE, fall back to exact Set to resolve potential false positives
        if (visitedUris.add(normalizedKey)) {
            queue.offer(target);
            return true;
        }

        return false;
    }

    public CrawlTarget poll(long timeout, TimeUnit unit) throws InterruptedException {
        CrawlTarget target = queue.poll(timeout, unit);
        if (target != null) {
            inFlightCount.incrementAndGet();
        }
        return target;
    }

    public void markCompleted(CrawlTarget target) {
        inFlightCount.decrementAndGet();
    }

    public boolean isIdle() {
        return queue.isEmpty() && inFlightCount.get() == 0;
    }

    public int getQueueSize() {
        return queue.size();
    }

    public int getVisitedCount() {
        return visitedUris.size();
    }

    public int getInFlightCount() {
        return inFlightCount.get();
    }

    public BloomFilter getBloomFilter() {
        return bloomFilter;
    }

    public void clear() {
        queue.clear();
        visitedUris.clear();
        bloomFilter.clear();
        inFlightCount.set(0);
    }
}
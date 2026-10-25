package com.crawler.core.frontier;

import com.crawler.core.model.CrawlTarget;
import org.springframework.stereotype.Component;

import java.net.URI;
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
    private final AtomicInteger inFlightCount = new AtomicInteger(0);

    /**
     * Enqueues a seed or newly discovered target if it has not yet been visited.
     * @return true if added to frontier, false if already visited or duplicate.
     */
    public boolean offer(CrawlTarget target) {
        if (target == null || target.getUri() == null) {
            return false;
        }

        String normalizedKey = normalizeUri(target.getUri());

        // Atomic check-and-add: O(1)
        if (visitedUris.add(normalizedKey)) {
            boolean enqueued = queue.offer(target);
            if (!enqueued) {
                // Roll back if queue refused the element
                visitedUris.remove(normalizedKey);
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Normalizes URIs to prevent subtle duplicate crawls (case-insensitive host,
     * stripping fragments, and standardizing path root).
     */
    private String normalizeUri(URI uri) {
        try {
            URI normalized = uri.normalize();
            String scheme = normalized.getScheme() != null ? normalized.getScheme().toLowerCase() : "http";
            String host = normalized.getHost() != null ? normalized.getHost().toLowerCase() : "";
            int port = normalized.getPort();
            String path = normalized.getPath();

            if (path == null || path.isEmpty()) {
                path = "/";
            } else if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            String query = normalized.getQuery() != null ? "?" + normalized.getQuery() : "";
            String portPart = (port != -1 && port != 80 && port != 443) ? ":" + port : "";

            return scheme + "://" + host + portPart + path + query;
        } catch (Exception e) {
            return uri.normalize().toString();
        }
    }

    /**
     * Retrieves the next target, blocking up to the timeout duration.
     */
    public CrawlTarget poll(long timeout, TimeUnit unit) throws InterruptedException {
        CrawlTarget target = queue.poll(timeout, unit);
        if (target != null) {
            inFlightCount.incrementAndGet();
        }
        return target;
    }

    /**
     * Marks a target crawl lifecycle as concluded (success or error).
     */
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

    public void clear() {
        queue.clear();
        visitedUris.clear();
        inFlightCount.set(0);
    }
}
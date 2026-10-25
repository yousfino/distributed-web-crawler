package com.crawler.core.engine;

import com.crawler.core.frontier.FrontierRegistry;
import com.crawler.core.frontier.HostRateLimiter;
import com.crawler.core.model.CrawlTarget;
import com.crawler.core.model.ParsedDocument;
import com.crawler.core.parser.DocumentParser;
import com.crawler.core.parser.ParserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Service
public class CrawlEngine {

    private static final Logger log = LoggerFactory.getLogger(CrawlEngine.class);

    private final FrontierRegistry frontier;
    private final HostRateLimiter rateLimiter;
    private final HttpFetcher httpFetcher;
    private final ParserFactory parserFactory;
    private final int threadPoolSize;
    private final int maxDepth;

    private ExecutorService workerPool;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public CrawlEngine(
            FrontierRegistry frontier,
            HostRateLimiter rateLimiter,
            HttpFetcher httpFetcher,
            ParserFactory parserFactory,
            @Value("${crawler.engine.default-thread-pool-size:8}") int threadPoolSize,
            @Value("${crawler.engine.max-depth:3}") int maxDepth
    ) {
        this.frontier = frontier;
        this.rateLimiter = rateLimiter;
        this.httpFetcher = httpFetcher;
        this.parserFactory = parserFactory;
        this.threadPoolSize = threadPoolSize;
        this.maxDepth = maxDepth;
    }

    public synchronized void start(URI seedUri, String jobId, Consumer<ParsedDocument> resultConsumer) {
        if (isRunning.get()) {
            throw new IllegalStateException("CrawlEngine is already actively executing a crawl job.");
        }

        frontier.clear();
        isRunning.set(true);
        frontier.offer(new CrawlTarget(seedUri, 0, jobId));

        this.workerPool = Executors.newFixedThreadPool(threadPoolSize);

        for (int i = 0; i < threadPoolSize; i++) {
            workerPool.submit(() -> runWorker(jobId, resultConsumer));
        }
    }

    private void runWorker(String jobId, Consumer<ParsedDocument> resultConsumer) {
        int emptyPollCount = 0;

        while (isRunning.get()) {
            CrawlTarget target = null;
            try {
                // Poll for work
                target = frontier.poll(100, TimeUnit.MILLISECONDS);
                if (target == null) {
                    if (frontier.isIdle()) {
                        emptyPollCount++;
                        // If idle for ~1 second (10 x 100ms), all work is confirmed finished
                        if (emptyPollCount >= 10) {
                            stop();
                            break;
                        }
                    } else {
                        emptyPollCount = 0;
                    }
                    continue;
                }

                emptyPollCount = 0;

                // Rate limiting
                rateLimiter.acquire(target.getDomain());

                // Fetch
                HttpFetcher.HttpResponsePayload payload = httpFetcher.fetch(target.getUri());

                // Parse
                DocumentParser parser = parserFactory.getParser(payload.contentType());
                ParsedDocument parsedDoc = parser.parse(
                        target,
                        payload.body(),
                        payload.statusCode(),
                        payload.contentType(),
                        payload.latencyMs()
                );

                if (resultConsumer != null) {
                    resultConsumer.accept(parsedDoc);
                }

                // Enqueue discovered child links
                if (target.getDepth() < maxDepth && payload.statusCode() < 400) {
                    for (URI link : parsedDoc.getDiscoveredLinks()) {
                        // Scope to same host and port
                        boolean sameHost = target.getDomain().equalsIgnoreCase(link.getHost());
                        boolean samePort = target.getUri().getPort() == link.getPort();
                        if (sameHost && samePort) {
                            frontier.offer(new CrawlTarget(link, target.getDepth() + 1, jobId));
                        }
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                log.error("Failed to crawl target: {}", target != null ? target.getUri() : "unknown", ex);
            } finally {
                if (target != null) {
                    frontier.markCompleted(target);
                }
            }
        }
    }

    public synchronized void stop() {
        if (!isRunning.get()) {
            return;
        }
        isRunning.set(false);
        if (workerPool != null && !workerPool.isShutdown()) {
            workerPool.shutdown();
            try {
                if (!workerPool.awaitTermination(2, TimeUnit.SECONDS)) {
                    workerPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                workerPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean isRunning() {
        return isRunning.get();
    }
}
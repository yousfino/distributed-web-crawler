package com.crawler.api;

import com.crawler.api.dto.CrawlProgressEvent;
import com.crawler.api.dto.CrawlStatsDto;
import com.crawler.core.engine.CrawlEngine;
import com.crawler.core.model.ParsedDocument;
import com.crawler.core.storage.LinkGraph;
import com.crawler.core.storage.StorageStrategy;
import com.crawler.core.storage.TopKKeywordsExtractor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CrawlerService {

    private final CrawlEngine crawlEngine;
    private final StorageStrategy storageStrategy;
    private final LinkGraph linkGraph;
    private final TopKKeywordsExtractor keywordsExtractor;
    private final SimpMessagingTemplate messagingTemplate;

    private final Map<String, Integer> globalKeywordAggregator = new ConcurrentHashMap<>();
    private volatile String currentJobId = null;

    public CrawlerService(
            CrawlEngine crawlEngine,
            StorageStrategy storageStrategy,
            LinkGraph linkGraph,
            TopKKeywordsExtractor keywordsExtractor,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.crawlEngine = crawlEngine;
        this.storageStrategy = storageStrategy;
        this.linkGraph = linkGraph;
        this.keywordsExtractor = keywordsExtractor;
        this.messagingTemplate = messagingTemplate;
    }

    public synchronized String initiateCrawl(URI seedUri) {
        if (crawlEngine.isRunning()) {
            throw new IllegalStateException("A crawl job is already in progress.");
        }

        // Reset previous state
        storageStrategy.clear();
        linkGraph.clear();
        globalKeywordAggregator.clear();

        this.currentJobId = UUID.randomUUID().toString();

        crawlEngine.start(seedUri, currentJobId, this::handleParsedDocument);
        return currentJobId;
    }

    public void stopCrawl() {
        crawlEngine.stop();
    }

    private void handleParsedDocument(ParsedDocument doc) {
        // 1. Store in strategy
        storageStrategy.store(doc);

        // 2. Map edges in link graph
        for (URI link : doc.getDiscoveredLinks()) {
            linkGraph.addEdge(doc.getUri(), link);
        }

        // 3. Aggregate keyword frequencies
        doc.getKeywordFrequencies().forEach((word, count) ->
                globalKeywordAggregator.merge(word, count, Integer::sum)
        );

        // 4. Stream real-time event via WebSocket
        CrawlProgressEvent event = new CrawlProgressEvent(
                currentJobId,
                doc.getUri(),
                doc.getTitle(),
                doc.getStatusCode(),
                doc.getWordCount(),
                doc.getDiscoveredLinks().size(),
                doc.getResponseTimeMs(),
                Instant.now()
        );
        messagingTemplate.convertAndSend("/topic/progress", event);
    }

    public CrawlStatsDto getCrawlStats(int topK) {
        return new CrawlStatsDto(
                currentJobId,
                crawlEngine.isRunning(),
                storageStrategy.count(),
                linkGraph.getNodeCount(),
                linkGraph.getEdgeCount(),
                linkGraph.hasCycle(),
                keywordsExtractor.extractTopK(globalKeywordAggregator, topK),
                linkGraph.getNodes()
        );
    }
}
package com.crawler.api.dto;

import java.net.URI;
import java.time.Instant;

public record CrawlProgressEvent(
        String jobId,
        URI pageUri,
        String title,
        int statusCode,
        int wordCount,
        int totalDiscoveredLinks,
        long latencyMs,
        Instant timestamp
) {}
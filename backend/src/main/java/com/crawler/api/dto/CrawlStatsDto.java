package com.crawler.api.dto;

import com.crawler.core.storage.TopKKeywordsExtractor;
import java.util.List;
import java.util.Set;

public record CrawlStatsDto(
        String jobId,
        boolean isRunning,
        int totalPagesCrawled,
        int graphNodesCount,
        int graphEdgesCount,
        boolean hasGraphCycle,
        List<TopKKeywordsExtractor.KeywordRank> topKeywords,
        Set<String> discoveredUris
) {}
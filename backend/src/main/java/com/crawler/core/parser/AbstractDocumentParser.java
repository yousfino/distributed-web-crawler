package com.crawler.core.parser;

import com.crawler.core.model.CrawlTarget;
import com.crawler.core.model.ParsedDocument;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractDocumentParser implements DocumentParser {

    @Override
    public final ParsedDocument parse(CrawlTarget target, String rawBody, int statusCode, String contentType, long latencyMs) {
        if (statusCode >= 400 || rawBody == null || rawBody.isBlank()) {
            return buildErrorDocument(target.getUri(), statusCode, contentType, latencyMs);
        }

        String title = extractTitle(rawBody);
        List<String> headings = extractHeadings(rawBody);
        Set<URI> links = extractLinks(target.getUri(), rawBody);
        Map<String, Integer> keywords = extractKeywordFrequencies(rawBody);
        int totalWords = calculateWordCount(keywords);

        return ParsedDocument.builder()
                .uri(target.getUri())
                .statusCode(statusCode)
                .title(title)
                .contentType(contentType)
                .headings(headings)
                .discoveredLinks(links)
                .keywordFrequencies(keywords)
                .wordCount(totalWords)
                .responseTimeMs(latencyMs)
                .crawledAt(Instant.now())
                .build();
    }

    protected abstract String extractTitle(String rawBody);
    protected abstract List<String> extractHeadings(String rawBody);
    protected abstract Set<URI> extractLinks(URI baseUri, String rawBody);
    protected abstract Map<String, Integer> extractKeywordFrequencies(String rawBody);

    protected int calculateWordCount(Map<String, Integer> keywordFrequencies) {
        return keywordFrequencies.values().stream().mapToInt(Integer::intValue).sum();
    }

    private ParsedDocument buildErrorDocument(URI uri, int statusCode, String contentType, long latencyMs) {
        return ParsedDocument.builder()
                .uri(uri)
                .statusCode(statusCode)
                .title("")
                .contentType(contentType != null ? contentType : "unknown")
                .headings(Collections.emptyList())
                .discoveredLinks(Collections.emptySet())
                .keywordFrequencies(Collections.emptyMap())
                .wordCount(0)
                .responseTimeMs(latencyMs)
                .crawledAt(Instant.now())
                .build();
    }
}
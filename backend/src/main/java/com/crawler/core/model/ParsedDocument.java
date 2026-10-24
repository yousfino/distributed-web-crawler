package com.crawler.core.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter
@Builder
@ToString
public class ParsedDocument {
    private final URI uri;
    private final int statusCode;
    private final String title;
    private final String contentType;
    private final int wordCount;
    private final List<String> headings;
    private final Set<URI> discoveredLinks;
    private final Map<String, Integer> keywordFrequencies;
    private final long responseTimeMs;
    private final Instant crawledAt;
}
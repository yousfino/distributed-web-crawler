package com.crawler.core.parser;

import com.crawler.core.model.CrawlTarget;
import com.crawler.core.model.ParsedDocument;

public interface DocumentParser {
    boolean supports(String contentType);
    ParsedDocument parse(CrawlTarget target, String rawBody, int statusCode, String contentType, long latencyMs);
}
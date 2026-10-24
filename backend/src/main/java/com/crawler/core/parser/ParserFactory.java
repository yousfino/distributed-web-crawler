package com.crawler.core.parser;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ParserFactory {

    private final List<DocumentParser> parsers;

    public ParserFactory(List<DocumentParser> parsers) {
        this.parsers = parsers;
    }

    public DocumentParser getParser(String contentType) {
        return parsers.stream()
                .filter(p -> p.supports(contentType))
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException(
                        "No parser available for Content-Type: " + contentType));
    }
}
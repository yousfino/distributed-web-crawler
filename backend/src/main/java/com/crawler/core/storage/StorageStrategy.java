package com.crawler.core.storage;

import com.crawler.core.model.ParsedDocument;

import java.util.List;
import java.util.Optional;

public interface StorageStrategy {
    void store(ParsedDocument document);
    Optional<ParsedDocument> findByUri(String uri);
    List<ParsedDocument> findAll();
    int count();
    void clear();
}
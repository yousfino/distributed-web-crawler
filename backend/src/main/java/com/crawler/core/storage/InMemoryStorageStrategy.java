package com.crawler.core.storage;

import com.crawler.core.model.ParsedDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryStorageStrategy implements StorageStrategy {

    private final Map<String, ParsedDocument> store = new ConcurrentHashMap<>();

    @Override
    public void store(ParsedDocument document) {
        if (document != null && document.getUri() != null) {
            store.put(document.getUri().normalize().toString(), document);
        }
    }

    @Override
    public Optional<ParsedDocument> findByUri(String uri) {
        if (uri == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(uri));
    }

    @Override
    public List<ParsedDocument> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public int count() {
        return store.size();
    }

    @Override
    public void clear() {
        store.clear();
    }
}
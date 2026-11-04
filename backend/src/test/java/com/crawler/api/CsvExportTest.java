package com.crawler.api;

import com.crawler.core.engine.CrawlEngine;
import com.crawler.core.model.ParsedDocument;
import com.crawler.core.storage.*;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CsvExportTest {
    private final InMemoryStorageStrategy storage = new InMemoryStorageStrategy();
    private final CrawlerService service = new CrawlerService(mock(CrawlEngine.class), storage,
            new LinkGraph(), new TopKKeywordsExtractor(), mock(SimpMessagingTemplate.class));

    private ParsedDocument page(String url, String title) {
        return ParsedDocument.builder().uri(URI.create(url)).title(title).statusCode(200)
                .contentType("text/html; charset=UTF-8").wordCount(12).responseTimeMs(45)
                .headings(List.of("Résumé, overview", "Details"))
                .discoveredLinks(Set.of(URI.create("https://example.com/child")))
                .keywordFrequencies(Map.of("beta", 2, "alpha", 2, "report", 5))
                .crawledAt(Instant.parse("2026-11-04T12:30:00Z")).build();
    }

    @Test
    void exportsAllPagesInUrlOrderWithReadableMetrics() {
        for (int i = 59; i >= 0; i--) {
            storage.store(page("https://example.com/" + String.format("%02d", i), "Page " + i));
        }
        String csv = service.exportCsv();
        assertTrue(csv.startsWith("\uFEFFJob ID,Page URL,Page Title,HTTP Status"));
        assertEquals(61, csv.split("\r\n").length);
        assertTrue(csv.indexOf("https://example.com/00") < csv.indexOf("https://example.com/59"));
        assertTrue(csv.contains("\"200\",\"text/html; charset=UTF-8\",\"12\",\"45\",\"1\""));
        assertTrue(csv.contains("\"Résumé, overview | Details\""));
        assertTrue(csv.contains("\"report (5); alpha (2); beta (2)\""));
        assertTrue(csv.contains("\"2026-11-04T12:30:00Z\""));
    }

    @Test
    void escapesQuotesCommasAndMultilineContentAndNeutralizesFormulas() {
        storage.store(page("https://example.com/", "  =HYPERLINK(\"bad\")\nnext,line"));
        String csv = service.exportCsv();
        assertTrue(csv.contains("\"'  =HYPERLINK(\"\"bad\"\")\nnext,line\""));
    }

    @Test
    void emptyExportStillHasAHeader() {
        assertEquals(1, service.exportCsv().split("\r\n").length);
    }
}

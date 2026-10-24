package com.crawler.core.parser;

import com.crawler.core.model.CrawlTarget;
import com.crawler.core.model.ParsedDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class HtmlDocumentParserTest {

    private HtmlDocumentParser parser;

    @BeforeEach
    void setUp() {
        parser = new HtmlDocumentParser();
    }

    @Test
    void shouldExtractMetadataAndNormalizeLinksCorrectly() {
        URI baseUri = URI.create("https://example.com/blog");
        CrawlTarget target = new CrawlTarget(baseUri, 0, "job-101");

        String html = """
            <!DOCTYPE html>
            <html>
            <head><title>Test Engine Page</title></head>
            <body>
                <h1>Main Heading</h1>
                <h2>Sub Heading</h2>
                <p>Distributed web crawlers analyze links and calculate page frequencies.</p>
                <a href="/about">About Us</a>
                <a href="https://example.com/pricing#tier-1">Pricing</a>
                <a href="mailto:admin@example.com">Contact</a>
            </body>
            </html>
            """;

        ParsedDocument doc = parser.parse(target, html, 200, "text/html; charset=UTF-8", 45L);

        assertEquals("Test Engine Page", doc.getTitle());
        assertEquals(2, doc.getHeadings().size());
        assertTrue(doc.getHeadings().contains("Main Heading"));

        // Verify normalized links: /about resolves to https://example.com/about, fragment stripped from pricing
        assertEquals(2, doc.getDiscoveredLinks().size());
        assertTrue(doc.getDiscoveredLinks().contains(URI.create("https://example.com/about")));
        assertTrue(doc.getDiscoveredLinks().contains(URI.create("https://example.com/pricing")));

        // Verify keyword frequencies
        assertTrue(doc.getKeywordFrequencies().get("distributed") >= 1);
        assertTrue(doc.getWordCount() > 0);
    }
}
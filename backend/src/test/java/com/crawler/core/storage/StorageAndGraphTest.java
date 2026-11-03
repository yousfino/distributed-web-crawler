package com.crawler.core.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StorageAndGraphTest {

    private LinkGraph linkGraph;
    private TopKKeywordsExtractor extractor;

    @BeforeEach
    void setUp() {
        linkGraph = new LinkGraph();
        extractor = new TopKKeywordsExtractor();
    }

    @Test
    void shouldDetectCyclesInDirectedLinkGraph() {
        URI a = URI.create("http://example.com/a");
        URI b = URI.create("http://example.com/b");
        URI c = URI.create("http://example.com/c");

        linkGraph.addEdge(a, b);
        linkGraph.addEdge(b, c);
        assertFalse(linkGraph.hasCycle());

        linkGraph.addEdge(c, a);
        assertTrue(linkGraph.hasCycle());
    }

    @Test
    void shouldExtractTopKKeywordsAccurately() {
        Map<String, Integer> frequencies = Map.of(
                "java", 15,
                "concurrency", 40,
                "thread", 30,
                "distributed", 25,
                "heap", 5
        );

        List<TopKKeywordsExtractor.KeywordRank> top3 = extractor.extractTopK(frequencies, 3);

        assertEquals(3, top3.size());
        assertEquals("concurrency", top3.get(0).keyword());
        assertEquals(40, top3.get(0).frequency());
        assertEquals("thread", top3.get(1).keyword());
        assertEquals(30, top3.get(1).frequency());
        assertEquals("distributed", top3.get(2).keyword());
        assertEquals(25, top3.get(2).frequency());
    }
}
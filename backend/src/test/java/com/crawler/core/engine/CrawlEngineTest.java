package com.crawler.core.engine;

import com.crawler.core.frontier.FrontierRegistry;
import com.crawler.core.frontier.HostRateLimiter;
import com.crawler.core.model.ParsedDocument;
import com.crawler.core.parser.HtmlDocumentParser;
import com.crawler.core.parser.ParserFactory;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class CrawlEngineTest {

    private HttpServer server;
    private CrawlEngine engine;
    private FrontierRegistry frontier;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();

        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String responseBody;
            if (path.equals("/")) {
                responseBody = "<html><head><title>Root</title></head><body><h1>Root</h1><a href=\"/sub1\">1</a><a href=\"/sub2\">2</a></body></html>";
            } else if (path.equals("/sub1")) {
                responseBody = "<html><head><title>Page 1</title></head><body><h1>Page 1</h1><a href=\"/sub2\">2</a></body></html>";
            } else if (path.equals("/sub2")) {
                responseBody = "<html><head><title>Page 2</title></head><body><h1>Page 2</h1><a href=\"/\">Root</a></body></html>";
            } else {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            byte[] bytes = responseBody.getBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        frontier = new FrontierRegistry();
        HostRateLimiter rateLimiter = new HostRateLimiter(50.0, 50.0); // High throughput for testing
        HttpFetcher fetcher = new HttpFetcher("TestBot/1.0", 3000);
        ParserFactory parserFactory = new ParserFactory(List.of(new HtmlDocumentParser()));

        engine = new CrawlEngine(frontier, rateLimiter, fetcher, parserFactory, 4, 2);
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.stop();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldConcurrentlyCrawlAndDeduplicateCycles() throws InterruptedException {
        URI seed = URI.create("http://localhost:" + port + "/");
        List<ParsedDocument> crawled = new CopyOnWriteArrayList<>();

        engine.start(seed, "test-job", crawled::add);

        // Await completion or timeout (max 5 seconds)
        long deadline = System.currentTimeMillis() + 5000;
        while (engine.isRunning() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        // Verify all 3 unique pages were parsed despite cyclic links
        assertEquals(3, crawled.size(), "Expected exactly 3 unique pages to be crawled");
        assertEquals(3, frontier.getVisitedCount(), "Frontier should record 3 unique visited URLs");
        assertTrue(crawled.stream().anyMatch(doc -> "Root".equals(doc.getTitle())));
        assertTrue(crawled.stream().anyMatch(doc -> "Page 1".equals(doc.getTitle())));
        assertTrue(crawled.stream().anyMatch(doc -> "Page 2".equals(doc.getTitle())));
    }
}
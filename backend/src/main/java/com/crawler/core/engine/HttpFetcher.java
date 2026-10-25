package com.crawler.core.engine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class HttpFetcher {

    private final HttpClient client;
    private final String userAgent;
    private final Duration timeout;

    public HttpFetcher(
            @Value("${crawler.engine.user-agent:DistributedCrawlerBot/1.0}") String userAgent,
            @Value("${crawler.engine.request-timeout-ms:5000}") long timeoutMs
    ) {
        this.userAgent = userAgent;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(this.timeout)
                .build();
    }

    public record HttpResponsePayload(int statusCode, String contentType, String body, long latencyMs) {}

    public HttpResponsePayload fetch(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("User-Agent", userAgent)
                .timeout(timeout)
                .GET()
                .build();

        long start = System.currentTimeMillis();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        long latency = System.currentTimeMillis() - start;

        String contentType = response.headers()
                .firstValue("Content-Type")
                .orElse("text/html");

        return new HttpResponsePayload(response.statusCode(), contentType, response.body(), latency);
    }
}
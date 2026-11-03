package com.crawler.api.controller;

import com.crawler.api.CrawlerService;
import com.crawler.api.dto.CrawlRequestDto;
import com.crawler.api.dto.CrawlStatsDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/crawler")
@CrossOrigin(origins = "*")
public class CrawlerController {

    private final CrawlerService crawlerService;

    public CrawlerController(CrawlerService crawlerService) {
        this.crawlerService = crawlerService;
    }

    @PostMapping("/start")
    public ResponseEntity<?> startCrawl(@Valid @RequestBody CrawlRequestDto request) {
        URI seed = URI.create(request.seedUrl());
        String jobId = crawlerService.initiateCrawl(seed);
        return ResponseEntity.ok(Map.of(
                "jobId", jobId,
                "message", "Crawl initiated successfully"
        ));
    }

    @PostMapping("/stop")
    public ResponseEntity<?> stopCrawl() {
        crawlerService.stopCrawl();
        return ResponseEntity.ok(Map.of("message", "Crawl stopped successfully"));
    }

    @GetMapping("/stats")
    public ResponseEntity<CrawlStatsDto> getStats(@RequestParam(defaultValue = "10") int topK) {
        return ResponseEntity.ok(crawlerService.getCrawlStats(topK));
    }
}
package com.crawler.core.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.net.URI;
import java.util.Objects;

@Getter
@Builder
@ToString
public class CrawlTarget {
    private final URI uri;
    private final int depth;
    private final String jobId;

    public CrawlTarget(URI uri, int depth, String jobId) {
        this.uri = Objects.requireNonNull(uri, "URI cannot be null");
        this.depth = depth;
        this.jobId = Objects.requireNonNull(jobId, "Job ID cannot be null");
    }

    public String getDomain() {
        return uri.getHost();
    }
}
package com.crawler.api.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record CrawlRequestDto(
        @NotBlank(message = "Seed URL is required")
        @URL(message = "Seed URL must be a valid HTTP/HTTPS address")
        String seedUrl
) {}
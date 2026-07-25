package com.branchlight.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "branchlight.search.page-fetcher")
public record PageFetcherProperties(
        Duration connectionTimeout,
        Duration responseTimeout,
        int maximumRedirects,
        int maximumResponseBytes) {
}

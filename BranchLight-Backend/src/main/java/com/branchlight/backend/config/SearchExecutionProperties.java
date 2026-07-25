package com.branchlight.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "branchlight.search.execution")
public record SearchExecutionProperties(
        int providerQueryParallelism,
        int pageFetchParallelism,
        int pageProcessingParallelism) {
}
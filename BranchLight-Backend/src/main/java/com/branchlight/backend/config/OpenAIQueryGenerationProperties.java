package com.branchlight.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
        prefix = "branchlight.openai.query-generation")
public record OpenAIQueryGenerationProperties(
        boolean enabled,
        String model) {

    public OpenAIQueryGenerationProperties {
        model = model == null ? "" : model;
    }
}

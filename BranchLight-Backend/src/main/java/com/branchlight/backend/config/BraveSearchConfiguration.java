package com.branchlight.backend.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import com.branchlight.backend.search.provider.brave.BraveSearchProvider;

@Configuration(proxyBeanMethods = false)
public class BraveSearchConfiguration {

    @Bean
    @ConditionalOnProperty(name = "BRAVE_SEARCH_API_KEY")
    BraveSearchProvider braveSearchProvider(
            @Value("${BRAVE_SEARCH_API_KEY}") String apiKey,
            @Value("${branchlight.search.brave.base-url}") URI baseUrl,
            @Value("${branchlight.search.brave.timeout}") Duration timeout,
            JsonMapper jsonMapper) {
        return new BraveSearchProvider(
                apiKey,
                baseUrl,
                timeout,
                jsonMapper);
    }
}

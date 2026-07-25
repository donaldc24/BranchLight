package com.branchlight.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.branchlight.backend.search.fetch.HttpPageFetcher;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PageFetcherProperties.class)
public class PageFetcherConfiguration {

    @Bean(destroyMethod = "close")
    HttpPageFetcher httpPageFetcher(
            PageFetcherProperties properties) {
        return new HttpPageFetcher(
                properties.connectionTimeout(),
                properties.responseTimeout(),
                properties.maximumRedirects(),
                properties.maximumResponseBytes());
    }
}

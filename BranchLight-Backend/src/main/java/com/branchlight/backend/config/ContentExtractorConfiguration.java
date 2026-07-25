package com.branchlight.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import com.branchlight.backend.search.content.JsoupContentExtractor;

@Configuration(proxyBeanMethods = false)
public class ContentExtractorConfiguration {

    @Bean
    JsoupContentExtractor jsoupContentExtractor(
            JsonMapper jsonMapper) {
        return new JsoupContentExtractor(jsonMapper);
    }
}

package com.branchlight.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

import com.branchlight.backend.search.provider.brave.BraveSearchProvider;

import static org.assertj.core.api.Assertions.assertThat;

class BraveSearchConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withInitializer(context -> context.getBeanFactory()
                            .setConversionService(
                                    ApplicationConversionService.getSharedInstance()))
                    .withUserConfiguration(BraveSearchConfiguration.class)
                    .withBean(
                            JsonMapper.class,
                            () -> JsonMapper.builder().build())
                    .withPropertyValues(
                            "branchlight.search.brave.base-url=http://localhost:9999",
                            "branchlight.search.brave.timeout=250ms");

    @Test
    void createsTheProviderOnlyWhenTheBackendApiKeyIsConfigured() {
        contextRunner
                .withPropertyValues(
                        "BRAVE_SEARCH_API_KEY=not-a-real-key")
                .run(context -> assertThat(context)
                        .hasSingleBean(BraveSearchProvider.class));
    }

    @Test
    void leavesTheProviderDisabledWithoutAnApiKey() {
        contextRunner.run(context -> assertThat(context)
                .doesNotHaveBean(BraveSearchProvider.class));
    }
}

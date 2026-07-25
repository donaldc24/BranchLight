package com.branchlight.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

import com.branchlight.backend.search.content.ContentExtractor;
import com.branchlight.backend.search.content.JsoupContentExtractor;

import static org.assertj.core.api.Assertions.assertThat;

class ContentExtractorConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            ContentExtractorConfiguration.class)
                    .withBean(
                            JsonMapper.class,
                            () -> JsonMapper.builder().build());

    @Test
    void registersTheJsoupContentExtractor() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ContentExtractor.class);
            assertThat(context)
                    .hasSingleBean(JsoupContentExtractor.class);
        });
    }
}

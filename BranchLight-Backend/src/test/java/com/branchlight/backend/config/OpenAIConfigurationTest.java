package com.branchlight.backend.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.openai.client.OpenAIClient;
import com.branchlight.backend.search.query.QueryVariantGenerator;
import com.branchlight.backend.search.query.openai.OpenAiQueryVariantGenerator;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class OpenAIConfigurationTest {

    private static final String FAKE_API_KEY =
            "test-only-openai-key-never-log";

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withInitializer(
                            new ConfigDataApplicationContextInitializer())
                    .withUserConfiguration(OpenAIConfiguration.class);

    @Test
    void createsTheClientWhenQueryGenerationIsEnabled(
            CapturedOutput output) {
        contextRunner
                .withPropertyValues(
                        "BRANCHLIGHT_OPENAI_QUERY_GENERATION_ENABLED=true",
                        "OPENAI_API_KEY=" + FAKE_API_KEY,
                        "OPENAI_QUERY_MODEL=test-query-model")
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenAIClient.class);
                    assertThat(context).hasSingleBean(
                            QueryVariantGenerator.class);
                    assertThat(context).hasSingleBean(
                            OpenAiQueryVariantGenerator.class);

                    var properties = context.getBean(
                            OpenAIQueryGenerationProperties.class);
                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.model())
                            .isEqualTo("test-query-model");
                });

        assertThat(output).doesNotContain(FAKE_API_KEY);
    }

    @Test
    void leavesTheClientDisabledWhenConfigurationIsDisabled() {
        contextRunner
                .withPropertyValues(
                        "BRANCHLIGHT_OPENAI_QUERY_GENERATION_ENABLED=false",
                        "OPENAI_API_KEY=" + FAKE_API_KEY,
                        "OPENAI_QUERY_MODEL=test-query-model")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context)
                            .doesNotHaveBean(OpenAIClient.class);
                    assertThat(context)
                            .doesNotHaveBean(QueryVariantGenerator.class);
                });
    }

    @Test
    void startsWithoutAClientWhenTheApiKeyIsMissing() {
        contextRunner
                .withPropertyValues(
                        "BRANCHLIGHT_OPENAI_QUERY_GENERATION_ENABLED=true",
                        "OPENAI_QUERY_MODEL=test-query-model")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context)
                            .doesNotHaveBean(OpenAIClient.class);
                    assertThat(context)
                            .doesNotHaveBean(QueryVariantGenerator.class);
                });
    }

    @Test
    void treatsABlankApiKeyAsMissing() {
        contextRunner
                .withPropertyValues(
                        "BRANCHLIGHT_OPENAI_QUERY_GENERATION_ENABLED=true",
                        "OPENAI_API_KEY=   ",
                        "OPENAI_QUERY_MODEL=test-query-model")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context)
                            .doesNotHaveBean(OpenAIClient.class);
                    assertThat(context)
                            .doesNotHaveBean(QueryVariantGenerator.class);
                });
    }

    @Test
    void startsWithQueryGenerationDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(OpenAIClient.class);
            assertThat(context)
                    .doesNotHaveBean(QueryVariantGenerator.class);

            var properties = context.getBean(
                    OpenAIQueryGenerationProperties.class);
            assertThat(properties.enabled()).isFalse();
            assertThat(properties.model()).isEmpty();
        });
    }

    @Test
    void failsClearlyWhenEnabledWithoutAModel(
            CapturedOutput output) {
        contextRunner
                .withPropertyValues(
                        "BRANCHLIGHT_OPENAI_QUERY_GENERATION_ENABLED=true",
                        "OPENAI_API_KEY=" + FAKE_API_KEY)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(
                                    IllegalArgumentException.class)
                            .hasStackTraceContaining(
                                    "model must not be blank");
                    assertThat(
                            context.getStartupFailure().toString())
                            .doesNotContain(FAKE_API_KEY);
                });

        assertThat(output).doesNotContain(FAKE_API_KEY);
    }
}

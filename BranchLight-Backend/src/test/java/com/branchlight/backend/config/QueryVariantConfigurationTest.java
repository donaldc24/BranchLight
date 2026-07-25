package com.branchlight.backend.config;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.openai.client.OpenAIClient;
import com.branchlight.backend.search.query.DeterministicQueryVariantGenerator;
import com.branchlight.backend.search.query.FallbackQueryVariantGenerator;
import com.branchlight.backend.search.query.QueryPurpose;
import com.branchlight.backend.search.query.QueryVariantGenerator;
import com.branchlight.backend.search.query.QueryVariantValidator;
import com.branchlight.backend.search.query.openai.OpenAiQueryVariantGenerator;

import static org.assertj.core.api.Assertions.assertThat;

class QueryVariantConfigurationTest {

    private static final String FAKE_API_KEY =
            "test-only-openai-key-never-log";

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withInitializer(
                            new ConfigDataApplicationContextInitializer())
                    .withUserConfiguration(
                            OpenAIConfiguration.class,
                            QueryVariantConfiguration.class);

    @Test
    void configuresDeterministicFallbackWhenOpenAiIsDisabled() {
        contextRunner
                .withPropertyValues(
                        "BRANCHLIGHT_OPENAI_QUERY_GENERATION_ENABLED=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context)
                            .doesNotHaveBean(OpenAIClient.class);
                    assertThat(context).doesNotHaveBean(
                            OpenAiQueryVariantGenerator.class);
                    assertThat(context).hasSingleBean(
                            DeterministicQueryVariantGenerator.class);
                    assertThat(context).hasSingleBean(
                            FallbackQueryVariantGenerator.class);
                    assertThat(context.getBean(
                            QueryVariantGenerator.class))
                            .isInstanceOf(
                                    FallbackQueryVariantGenerator.class);

                    var variants = context
                            .getBean(QueryVariantGenerator.class)
                            .generate("virtual threads");

                    assertThat(variants).hasSize(5);
                    assertThat(variants)
                            .extracting(variant -> variant.purpose())
                            .containsExactlyInAnyOrderElementsOf(
                                    EnumSet.allOf(QueryPurpose.class));
                });
    }

    @Test
    void configuresOpenAiAsPrimaryDelegateWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "BRANCHLIGHT_OPENAI_QUERY_GENERATION_ENABLED=true",
                        "OPENAI_API_KEY=" + FAKE_API_KEY,
                        "OPENAI_QUERY_MODEL=test-query-model")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OpenAIClient.class);
                    assertThat(context).hasSingleBean(
                            OpenAiQueryVariantGenerator.class);
                    assertThat(context).hasSingleBean(
                            DeterministicQueryVariantGenerator.class);
                    assertThat(context).hasSingleBean(
                            FallbackQueryVariantGenerator.class);
                    assertThat(context.getBeansOfType(
                            QueryVariantGenerator.class))
                            .hasSize(3);
                    assertThat(context.getBean(
                            QueryVariantGenerator.class))
                            .isInstanceOf(
                                    FallbackQueryVariantGenerator.class);
                });
    }

    @Test
    void configuresTheMaximumQueryLengthFromTheEnvironment() {
        contextRunner
                .withPropertyValues(
                        "BRANCHLIGHT_QUERY_VARIANT_MAX_LENGTH=75")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context)
                            .hasSingleBean(QueryVariantValidator.class);
                    assertThat(context
                            .getBean(QueryVariantValidator.class)
                            .maximumQueryLength())
                            .isEqualTo(75);
                });
    }
}

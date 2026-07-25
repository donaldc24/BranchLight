package com.branchlight.backend.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.branchlight.backend.search.query.DeterministicQueryVariantGenerator;
import com.branchlight.backend.search.query.FallbackQueryVariantGenerator;
import com.branchlight.backend.search.query.QueryVariantValidator;
import com.branchlight.backend.search.query.openai.OpenAiQueryVariantGenerator;

@Configuration(proxyBeanMethods = false)
public class QueryVariantConfiguration {

    @Bean
    DeterministicQueryVariantGenerator
            deterministicQueryVariantGenerator() {
        return new DeterministicQueryVariantGenerator();
    }

    @Bean
    QueryVariantValidator queryVariantValidator(
            @Value("${branchlight.query-variants"
                    + ".maximum-query-length:400}")
            int maximumQueryLength) {
        return new QueryVariantValidator(maximumQueryLength);
    }

    @Bean
    @Primary
    FallbackQueryVariantGenerator queryVariantGenerator(
            ObjectProvider<OpenAiQueryVariantGenerator>
                    openAiQueryVariantGenerator,
            DeterministicQueryVariantGenerator
                    deterministicQueryVariantGenerator,
            QueryVariantValidator queryVariantValidator) {
        OpenAiQueryVariantGenerator primary =
                openAiQueryVariantGenerator.getIfAvailable();

        if (primary == null) {
            return FallbackQueryVariantGenerator.withDisabledPrimary(
                    deterministicQueryVariantGenerator,
                    queryVariantValidator);
        }

        return new FallbackQueryVariantGenerator(
                primary,
                deterministicQueryVariantGenerator,
                queryVariantValidator);
    }
}

package com.branchlight.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.branchlight.backend.search.query.openai.OpenAiQueryVariantGenerator;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenAIQueryGenerationProperties.class)
public class OpenAIConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(
            prefix = "branchlight.openai.query-generation",
            name = "enabled",
            havingValue = "true")
    @Conditional(OpenAIApiKeyPresentCondition.class)
    OpenAIClient openAIClient(
            @Value("${OPENAI_API_KEY}") String apiKey) {
        return OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "branchlight.openai.query-generation",
            name = "enabled",
            havingValue = "true")
    @Conditional(OpenAIApiKeyPresentCondition.class)
    OpenAiQueryVariantGenerator openAiQueryVariantGenerator(
            OpenAIClient openAIClient,
            OpenAIQueryGenerationProperties properties) {
        return new OpenAiQueryVariantGenerator(
                openAIClient,
                properties.model());
    }

    static final class OpenAIApiKeyPresentCondition implements Condition {

        @Override
        public boolean matches(
                ConditionContext context,
                AnnotatedTypeMetadata metadata) {
            String apiKey = context.getEnvironment()
                    .getProperty("OPENAI_API_KEY");
            return apiKey != null && !apiKey.isBlank();
        }
    }
}

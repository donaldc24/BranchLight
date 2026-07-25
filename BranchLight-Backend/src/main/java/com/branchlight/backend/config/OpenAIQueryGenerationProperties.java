package com.branchlight.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.openai.models.ReasoningEffort;
import com.openai.models.responses.ResponseTextConfig;

@ConfigurationProperties(
        prefix = "branchlight.openai.query-generation")
public record OpenAIQueryGenerationProperties(
        boolean enabled,
        String model,
        boolean priority,
        Integer maxOutputTokens,
        ReasoningEffortSetting reasoningEffort,
        VerbositySetting verbosity) {

    public OpenAIQueryGenerationProperties {
        model = model == null ? "" : model;
        maxOutputTokens = maxOutputTokens == null ? 256 : maxOutputTokens;
        reasoningEffort = reasoningEffort == null
            ? ReasoningEffortSetting.MINIMAL
                : reasoningEffort;
        verbosity = verbosity == null
                ? VerbositySetting.LOW
                : verbosity;

        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException(
                    "maxOutputTokens must be greater than zero");
        }
    }

    public enum ReasoningEffortSetting {
        NONE(ReasoningEffort.NONE),
        MINIMAL(ReasoningEffort.MINIMAL),
        LOW(ReasoningEffort.LOW),
        MEDIUM(ReasoningEffort.MEDIUM),
        HIGH(ReasoningEffort.HIGH),
        XHIGH(ReasoningEffort.XHIGH),
        MAX(ReasoningEffort.MAX);

        private final ReasoningEffort openAiValue;

        ReasoningEffortSetting(ReasoningEffort openAiValue) {
            this.openAiValue = openAiValue;
        }

        public ReasoningEffort openAiValue() {
            return openAiValue;
        }
    }

    public enum VerbositySetting {
        LOW(ResponseTextConfig.Verbosity.LOW),
        MEDIUM(ResponseTextConfig.Verbosity.MEDIUM),
        HIGH(ResponseTextConfig.Verbosity.HIGH);

        private final ResponseTextConfig.Verbosity openAiValue;

        VerbositySetting(ResponseTextConfig.Verbosity openAiValue) {
            this.openAiValue = openAiValue;
        }

        public ResponseTextConfig.Verbosity openAiValue() {
            return openAiValue;
        }
    }
}

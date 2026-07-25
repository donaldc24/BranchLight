package com.branchlight.backend.search.query.openai;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.branchlight.backend.search.query.GeneratedQuery;
import com.branchlight.backend.search.query.QueryPurpose;
import com.openai.client.OpenAIClient;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseTextConfig;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.openai.models.responses.StructuredResponseOutputItem;
import com.openai.models.responses.StructuredResponseOutputMessage;
import com.openai.services.blocking.ResponseService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpenAiQueryVariantGeneratorTest {

    private OpenAIClient openAIClient;
    private ResponseService responseService;
    private OpenAiQueryVariantGenerator generator;

    @BeforeEach
    void setUp() {
        openAIClient = mock(OpenAIClient.class);
        responseService = mock(ResponseService.class);
        when(openAIClient.responses()).thenReturn(responseService);
        generator = new OpenAiQueryVariantGenerator(
                openAIClient,
                "test-query-model");
    }

    @Test
    void makesOneStructuredRequestAndMapsOneQueryPerPurpose() {
        String originalQuery =
                "  intitle:\"exact phrase\" -obsolete filetype:pdf  ";
        var variants = variants(
                "intitle:\"exact phrase\" -obsolete filetype:pdf "
                        + "official primary material",
                "intitle:\"exact phrase\" -obsolete filetype:pdf "
                        + "clear overview",
                "intitle:\"exact phrase\" -obsolete filetype:pdf "
                        + "step-by-step examples",
                "intitle:\"exact phrase\" -obsolete filetype:pdf "
                        + "limitations tradeoffs",
                "intitle:\"exact phrase\" -obsolete filetype:pdf "
                        + "firsthand discussion");
        stubResponse(structuredResponse(variants));

        List<GeneratedQuery> generatedQueries =
                generator.generate(originalQuery);

        assertThat(generatedQueries).containsExactly(
                new GeneratedQuery(
                        variants.authoritative,
                        QueryPurpose.AUTHORITATIVE),
                new GeneratedQuery(
                        variants.explanatory,
                        QueryPurpose.EXPLANATORY),
                new GeneratedQuery(
                        variants.practical,
                        QueryPurpose.PRACTICAL),
                new GeneratedQuery(
                        variants.critical,
                        QueryPurpose.CRITICAL),
                new GeneratedQuery(
                        variants.humanDiscussion,
                        QueryPurpose.HUMAN_DISCUSSION));

        ArgumentCaptor<StructuredResponseCreateParams<
                OpenAiQueryVariantsResponse>> captor =
                createParamsCaptor();
        verify(responseService, times(1)).create(captor.capture());

        var params = captor.getValue();
        var rawParams = params.rawParams();
        var textFormat = rawParams.text()
                .orElseThrow()
                .format()
                .orElseThrow();

        assertThat(params.responseType())
                .isEqualTo(OpenAiQueryVariantsResponse.class);
        assertThat(rawParams.input().orElseThrow().asText())
                .isEqualTo(originalQuery.strip());
        assertThat(rawParams.model().orElseThrow().asString())
                .isEqualTo("test-query-model");
        assertThat(rawParams.maxOutputTokens()).contains(96L);
        assertThat(rawParams.reasoning()
                .orElseThrow()
                .effort()).contains(ReasoningEffort.NONE);
        assertThat(rawParams.store()).contains(false);
        assertThat(rawParams.serviceTier()).isEmpty();
        assertThat(rawParams.text()
                .orElseThrow()
                .verbosity()).contains(ResponseTextConfig.Verbosity.LOW);
        assertThat(textFormat.isJsonSchema()).isTrue();
        assertThat(textFormat.asJsonSchema().strict()).contains(true);
        assertThat(rawParams.instructions().orElseThrow())
                .contains(
                        "search-engine queries, not answers",
                        "domain-agnostic",
                        "specific domains",
                        "websites",
                        "factual assumptions",
                        "original subject",
                        "important constraints",
                        "quoted phrases",
                        "explicit negation",
                        "explicit search operators",
                        "meaningfully different",
                        "unchanged original query",
                        "exactly five search-query variants",
                        "under 12 words",
                        "Do not explain")
                .doesNotContain(originalQuery.strip());
        verify(openAIClient, times(1)).responses();
    }

    @Test
    void requestsPriorityProcessingOnlyWhenEnabled() {
        generator = new OpenAiQueryVariantGenerator(
                openAIClient,
                "test-query-model",
                true);
        stubResponse(structuredResponse(validVariants()));

        generator.generate("sample query");

        ArgumentCaptor<StructuredResponseCreateParams<
                OpenAiQueryVariantsResponse>> captor =
                createParamsCaptor();
        verify(responseService).create(captor.capture());
        assertThat(captor.getValue()
                .rawParams()
                .serviceTier()).contains(
                        ResponseCreateParams.ServiceTier.PRIORITY);
    }

    @Test
    void appliesConfiguredGenerationSettings() {
        generator = new OpenAiQueryVariantGenerator(
                openAIClient,
                "test-query-model",
                128,
                ReasoningEffort.LOW,
                ResponseTextConfig.Verbosity.HIGH,
                false);
        stubResponse(structuredResponse(validVariants()));

        generator.generate("sample query");

        ArgumentCaptor<StructuredResponseCreateParams<
                OpenAiQueryVariantsResponse>> captor =
                createParamsCaptor();
        verify(responseService).create(captor.capture());
        var rawParams = captor.getValue().rawParams();
        assertThat(rawParams.maxOutputTokens()).contains(128L);
        assertThat(rawParams.reasoning()
                .orElseThrow()
                .effort()).contains(ReasoningEffort.LOW);
        assertThat(rawParams.text()
                .orElseThrow()
                .verbosity()).contains(ResponseTextConfig.Verbosity.HIGH);
    }

    @Test
    void rejectsInvalidInputBeforeUsingTheClient() {
        assertThatThrownBy(() -> generator.generate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("originalQuery must not be null");

        for (String query : new String[]{"", " ", "\t\r\n"}) {
            assertThatThrownBy(() -> generator.generate(query))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("originalQuery must not be blank");
        }

        verifyNoInteractions(responseService);
    }

    @Test
    void rejectsInvalidModelConfiguration() {
        assertThatThrownBy(() -> new OpenAiQueryVariantGenerator(
                openAIClient,
                null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("model must not be null");
        assertThatThrownBy(() -> new OpenAiQueryVariantGenerator(
                openAIClient,
                "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("model must not be blank");
    }

    @Test
    void rejectsBlankGeneratedQueries() {
        var variants = validVariants();
        variants.critical = "  ";
        stubResponse(structuredResponse(variants));

        assertThatThrownBy(() -> generator.generate("sample query"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "OpenAI returned a blank query for CRITICAL");
    }

    @Test
    void rejectsDuplicateGeneratedQueriesIgnoringCaseAndWhitespace() {
        var variants = validVariants();
        variants.explanatory = "  PRIMARY   SOURCES  ";
        stubResponse(structuredResponse(variants));

        assertThatThrownBy(() -> generator.generate("sample query"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "OpenAI returned duplicate query variants");
    }

    @Test
    void rejectsTheUnchangedOriginalQueryIgnoringCaseAndWhitespace() {
        var variants = validVariants();
        variants.practical = "  SAMPLE   QUERY  ";
        stubResponse(structuredResponse(variants));

        assertThatThrownBy(() -> generator.generate("sample query"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "OpenAI returned the unchanged original query");
    }

    @Test
    void requiresExactlyOneStructuredOutput() {
        stubResponse(structuredResponse(List.of()));

        assertThatThrownBy(() -> generator.generate("sample query"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "OpenAI response must contain exactly one "
                                + "structured query variant set");

        stubResponse(structuredResponse(
                List.of(validVariants(), validVariants())));

        assertThatThrownBy(() -> generator.generate("sample query"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "OpenAI response must contain exactly one "
                                + "structured query variant set");
    }

    @Test
    void propagatesOpenAiFailuresWithoutFallback() {
        RuntimeException failure =
                new RuntimeException("mock OpenAI failure");
        when(responseService.create(anyStructuredParams()))
                .thenThrow(failure);

        assertThatThrownBy(() -> generator.generate("sample query"))
                .isSameAs(failure);

        verify(responseService, times(1))
                .create(anyStructuredParams());
    }

    private void stubResponse(
            StructuredResponse<OpenAiQueryVariantsResponse> response) {
        when(responseService.create(anyStructuredParams()))
                .thenReturn(response);
    }

    private OpenAiQueryVariantsResponse validVariants() {
        return variants(
                "primary sources",
                "clear explanation",
                "practical examples",
                "limitations and risks",
                "firsthand discussion");
    }

    private OpenAiQueryVariantsResponse variants(
            String authoritative,
            String explanatory,
            String practical,
            String critical,
            String humanDiscussion) {
        var response = new OpenAiQueryVariantsResponse();
        response.authoritative = authoritative;
        response.explanatory = explanatory;
        response.practical = practical;
        response.critical = critical;
        response.humanDiscussion = humanDiscussion;
        return response;
    }

    @SuppressWarnings("unchecked")
    private StructuredResponse<OpenAiQueryVariantsResponse>
            structuredResponse(
                    OpenAiQueryVariantsResponse output) {
        return structuredResponse(List.of(output));
    }

    @SuppressWarnings("unchecked")
    private StructuredResponse<OpenAiQueryVariantsResponse>
            structuredResponse(
                    List<OpenAiQueryVariantsResponse> outputs) {
        StructuredResponse<OpenAiQueryVariantsResponse> response =
                mock(StructuredResponse.class);
        StructuredResponseOutputItem<OpenAiQueryVariantsResponse> item =
                mock(StructuredResponseOutputItem.class);
        StructuredResponseOutputMessage<OpenAiQueryVariantsResponse> message =
                mock(StructuredResponseOutputMessage.class);
        List<StructuredResponseOutputMessage.Content<
                OpenAiQueryVariantsResponse>> contents =
                new ArrayList<>();

        for (OpenAiQueryVariantsResponse output : outputs) {
            StructuredResponseOutputMessage.Content<
                    OpenAiQueryVariantsResponse> content =
                    mock(StructuredResponseOutputMessage.Content.class);
            when(content.outputText()).thenReturn(Optional.of(output));
            contents.add(content);
        }

        when(response.output()).thenReturn(List.of(item));
        when(item.message()).thenReturn(Optional.of(message));
        when(message.content()).thenReturn(contents);
        return response;
    }

    private StructuredResponseCreateParams<OpenAiQueryVariantsResponse>
            anyStructuredParams() {
        return any();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<StructuredResponseCreateParams<
            OpenAiQueryVariantsResponse>> createParamsCaptor() {
        return ArgumentCaptor.forClass(
                (Class) StructuredResponseCreateParams.class);
    }
}

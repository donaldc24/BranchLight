package com.branchlight.backend.search.query;

import java.net.SocketTimeoutException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class FallbackQueryVariantGeneratorTest {

    private static final String ORIGINAL_QUERY =
            "\"private prompt sentinel\" -excluded";
    private static final String SENSITIVE_FAILURE =
            "secret-api-key-sentinel";

    private QueryVariantGenerator primary;
    private QueryVariantGenerator fallback;
    private QueryVariantValidator validator;

    @BeforeEach
    void setUp() {
        primary = mock(QueryVariantGenerator.class);
        fallback = mock(QueryVariantGenerator.class);
        validator = new QueryVariantValidator(200);
    }

    @Test
    void returnsAValidPrimaryResultWithoutUsingFallback(
            CapturedOutput output) {
        List<GeneratedQuery> primaryResult =
                validQueries("primary");
        when(primary.generate(ORIGINAL_QUERY))
                .thenReturn(primaryResult);
        var generator = generator();

        List<GeneratedQuery> result =
                generator.generate(ORIGINAL_QUERY);

        assertThat(result).isSameAs(primaryResult);
        verify(primary).generate(ORIGINAL_QUERY);
        verifyNoInteractions(fallback);
        assertThat(output).doesNotContain("Query variant fallback");
    }

    @Test
    void usesFallbackWhenThePrimaryRequestTimesOut(
            CapturedOutput output) {
        List<GeneratedQuery> fallbackResult =
                validQueries("timeout fallback");
        when(primary.generate(ORIGINAL_QUERY))
                .thenThrow(new RuntimeException(
                        new SocketTimeoutException(
                                SENSITIVE_FAILURE)));
        when(fallback.generate(ORIGINAL_QUERY))
                .thenReturn(fallbackResult);
        var generator = generator();

        List<GeneratedQuery> result =
                generator.generate(ORIGINAL_QUERY);

        assertThat(result).isSameAs(fallbackResult);
        verify(fallback).generate(ORIGINAL_QUERY);
        assertSafeCategory(output, "PRIMARY_TIMEOUT");
    }

    @Test
    void usesFallbackWhenThePrimaryThrows(
            CapturedOutput output) {
        List<GeneratedQuery> fallbackResult =
                validQueries("exception fallback");
        when(primary.generate(ORIGINAL_QUERY))
                .thenThrow(new IllegalStateException(
                        SENSITIVE_FAILURE));
        when(fallback.generate(ORIGINAL_QUERY))
                .thenReturn(fallbackResult);
        var generator = generator();

        List<GeneratedQuery> result =
                generator.generate(ORIGINAL_QUERY);

        assertThat(result).isSameAs(fallbackResult);
        verify(fallback).generate(ORIGINAL_QUERY);
        assertSafeCategory(output, "PRIMARY_EXCEPTION");
    }

    @Test
    void usesFallbackWhenThePrimaryResultIsInvalid(
            CapturedOutput output) {
        List<GeneratedQuery> invalidResult =
                validQueries("invalid primary")
                        .subList(0, 4);
        List<GeneratedQuery> fallbackResult =
                validQueries("validation fallback");
        when(primary.generate(ORIGINAL_QUERY))
                .thenReturn(invalidResult);
        when(fallback.generate(ORIGINAL_QUERY))
                .thenReturn(fallbackResult);
        var generator = generator();

        List<GeneratedQuery> result =
                generator.generate(ORIGINAL_QUERY);

        assertThat(result).isSameAs(fallbackResult);
        verify(fallback).generate(ORIGINAL_QUERY);
        assertSafeCategory(
                output,
                "PRIMARY_VALIDATION_FAILED");
    }

    @Test
    void usesFallbackWhenPrimaryGenerationIsDisabled(
            CapturedOutput output) {
        List<GeneratedQuery> fallbackResult =
                validQueries("disabled fallback");
        when(fallback.generate(ORIGINAL_QUERY))
                .thenReturn(fallbackResult);
        var generator =
                FallbackQueryVariantGenerator.withDisabledPrimary(
                        fallback,
                        validator);

        List<GeneratedQuery> result =
                generator.generate(ORIGINAL_QUERY);

        assertThat(result).isSameAs(fallbackResult);
        verify(fallback).generate(ORIGINAL_QUERY);
        verifyNoInteractions(primary);
        assertSafeCategory(output, "PRIMARY_DISABLED");
    }

    @Test
    void returnsTheSuccessfulFallbackResultUnchanged() {
        List<GeneratedQuery> fallbackResult =
                validQueries("unchanged fallback");
        when(primary.generate(ORIGINAL_QUERY))
                .thenThrow(new IllegalStateException("failure"));
        when(fallback.generate(ORIGINAL_QUERY))
                .thenReturn(fallbackResult);
        var generator = generator();

        List<GeneratedQuery> result =
                generator.generate(ORIGINAL_QUERY);

        assertThat(result).isSameAs(fallbackResult);
        assertThat(result)
                .containsExactlyElementsOf(fallbackResult);
    }

    @Test
    void rejectsInvalidCallerInputWithoutInvokingEitherGenerator() {
        var generator = generator();

        assertThatThrownBy(() -> generator.generate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("originalQuery must not be null");
        assertThatThrownBy(() -> generator.generate(" \t "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("originalQuery must not be blank");
        verifyNoInteractions(primary, fallback);
    }

    private FallbackQueryVariantGenerator generator() {
        return new FallbackQueryVariantGenerator(
                primary,
                fallback,
                validator);
    }

    private List<GeneratedQuery> validQueries(String prefix) {
        return List.of(
                generated(prefix, QueryPurpose.AUTHORITATIVE),
                generated(prefix, QueryPurpose.EXPLANATORY),
                generated(prefix, QueryPurpose.PRACTICAL),
                generated(prefix, QueryPurpose.CRITICAL),
                generated(prefix, QueryPurpose.HUMAN_DISCUSSION));
    }

    private GeneratedQuery generated(
            String prefix,
            QueryPurpose purpose) {
        return new GeneratedQuery(
                prefix + " " + purpose.name(),
                purpose);
    }

    private void assertSafeCategory(
            CapturedOutput output,
            String expectedCategory) {
        assertThat(output)
                .contains(
                        "Query variant fallback: "
                                + expectedCategory)
                .doesNotContain(ORIGINAL_QUERY)
                .doesNotContain(SENSITIVE_FAILURE);
    }
}

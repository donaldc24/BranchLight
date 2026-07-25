package com.branchlight.backend.search.query;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FallbackQueryVariantGenerator
        implements QueryVariantGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            FallbackQueryVariantGenerator.class);

    private final Optional<QueryVariantGenerator> primary;
    private final QueryVariantGenerator fallback;
    private final QueryVariantValidator validator;

    public FallbackQueryVariantGenerator(
            QueryVariantGenerator primary,
            QueryVariantGenerator fallback,
            QueryVariantValidator validator) {
        this(
                Optional.of(Objects.requireNonNull(
                        primary,
                        "primary must not be null")),
                fallback,
                validator);
    }

    private FallbackQueryVariantGenerator(
            Optional<QueryVariantGenerator> primary,
            QueryVariantGenerator fallback,
            QueryVariantValidator validator) {
        this.primary = Objects.requireNonNull(
                primary,
                "primary must not be null");
        this.fallback = Objects.requireNonNull(
                fallback,
                "fallback must not be null");
        this.validator = Objects.requireNonNull(
                validator,
                "validator must not be null");
    }

    public static FallbackQueryVariantGenerator withDisabledPrimary(
            QueryVariantGenerator fallback,
            QueryVariantValidator validator) {
        return new FallbackQueryVariantGenerator(
                Optional.empty(),
                fallback,
                validator);
    }

    @Override
    public List<GeneratedQuery> generate(String originalQuery) {
        Objects.requireNonNull(
                originalQuery,
                "originalQuery must not be null");

        if (originalQuery.isBlank()) {
            throw new IllegalArgumentException(
                    "originalQuery must not be blank");
        }

        if (primary.isEmpty()) {
            return generateFallback(
                    originalQuery,
                    FailureCategory.PRIMARY_DISABLED);
        }

        List<GeneratedQuery> primaryResult;
        try {
            primaryResult = primary.orElseThrow().generate(
                    originalQuery);
        } catch (RuntimeException exception) {
            FailureCategory category = isTimeout(exception)
                    ? FailureCategory.PRIMARY_TIMEOUT
                    : FailureCategory.PRIMARY_EXCEPTION;
            return generateFallback(originalQuery, category);
        }

        QueryVariantValidationResult validationResult =
                validator.validate(
                        originalQuery,
                        toCandidates(primaryResult));
        if (!validationResult.isValid()) {
            return generateFallback(
                    originalQuery,
                    FailureCategory.PRIMARY_VALIDATION_FAILED);
        }

        return primaryResult;
    }

    private List<QueryVariantCandidate> toCandidates(
            List<GeneratedQuery> generatedQueries) {
        if (generatedQueries == null) {
            return null;
        }

        List<QueryVariantCandidate> candidates =
                new ArrayList<>(generatedQueries.size());
        for (GeneratedQuery generatedQuery : generatedQueries) {
            if (generatedQuery == null) {
                candidates.add(null);
                continue;
            }

            QueryPurpose purpose = generatedQuery.purpose();
            candidates.add(new QueryVariantCandidate(
                    generatedQuery.queryText(),
                    purpose == null ? null : purpose.name()));
        }
        return candidates;
    }

    private List<GeneratedQuery> generateFallback(
            String originalQuery,
            FailureCategory category) {
        if (category == FailureCategory.PRIMARY_DISABLED) {
            LOGGER.info("Query variant fallback: {}", category);
        } else {
            LOGGER.warn("Query variant fallback: {}", category);
        }
        return fallback.generate(originalQuery);
    }

    private boolean isTimeout(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof TimeoutException
                    || current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException
                    || current.getClass()
                            .getSimpleName()
                            .endsWith("TimeoutException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private enum FailureCategory {
        PRIMARY_DISABLED,
        PRIMARY_TIMEOUT,
        PRIMARY_EXCEPTION,
        PRIMARY_VALIDATION_FAILED
    }
}

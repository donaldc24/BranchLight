package com.branchlight.backend.search.query;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.branchlight.backend.search.query.QueryVariantValidationFailure.BlankQueryText;
import com.branchlight.backend.search.query.QueryVariantValidationFailure.DuplicatePurpose;
import com.branchlight.backend.search.query.QueryVariantValidationFailure.DuplicateQuery;
import com.branchlight.backend.search.query.QueryVariantValidationFailure.MissingPurpose;
import com.branchlight.backend.search.query.QueryVariantValidationFailure.MissingVariantList;
import com.branchlight.backend.search.query.QueryVariantValidationFailure.NullVariant;
import com.branchlight.backend.search.query.QueryVariantValidationFailure.QueryTooLong;
import com.branchlight.backend.search.query.QueryVariantValidationFailure.UnchangedOriginalQuery;
import com.branchlight.backend.search.query.QueryVariantValidationFailure.UnknownPurpose;
import com.branchlight.backend.search.query.QueryVariantValidationFailure.VariantCount;

public final class QueryVariantValidator {

    private static final int REQUIRED_VARIANT_COUNT =
            QueryPurpose.values().length;

    private final int maximumQueryLength;

    public QueryVariantValidator(int maximumQueryLength) {
        if (maximumQueryLength <= 0) {
            throw new IllegalArgumentException(
                    "maximumQueryLength must be greater than zero");
        }

        this.maximumQueryLength = maximumQueryLength;
    }

    public int maximumQueryLength() {
        return maximumQueryLength;
    }

    public QueryVariantValidationResult validate(
            String originalQuery,
            List<QueryVariantCandidate> variants) {
        Objects.requireNonNull(
                originalQuery,
                "originalQuery must not be null");

        if (originalQuery.isBlank()) {
            throw new IllegalArgumentException(
                    "originalQuery must not be blank");
        }

        List<QueryVariantValidationFailure> failures =
                new ArrayList<>();
        if (variants == null) {
            failures.add(new MissingVariantList());
        } else if (variants.size() != REQUIRED_VARIANT_COUNT) {
            failures.add(new VariantCount(
                    REQUIRED_VARIANT_COUNT,
                    variants.size()));
        }

        Map<QueryPurpose, List<Integer>> purposeIndexes =
                new EnumMap<>(QueryPurpose.class);
        Map<String, Integer> firstQueryIndexes = new HashMap<>();

        if (variants != null) {
            for (int index = 0; index < variants.size(); index++) {
                validateVariant(
                        originalQuery,
                        variants.get(index),
                        index,
                        purposeIndexes,
                        firstQueryIndexes,
                        failures);
            }
        }

        for (QueryPurpose purpose : QueryPurpose.values()) {
            List<Integer> indexes = purposeIndexes.get(purpose);

            if (indexes == null) {
                failures.add(new MissingPurpose(purpose));
            } else if (indexes.size() > 1) {
                failures.add(new DuplicatePurpose(purpose, indexes));
            }
        }

        return new QueryVariantValidationResult(failures);
    }

    private void validateVariant(
            String originalQuery,
            QueryVariantCandidate variant,
            int index,
            Map<QueryPurpose, List<Integer>> purposeIndexes,
            Map<String, Integer> firstQueryIndexes,
            List<QueryVariantValidationFailure> failures) {
        if (variant == null) {
            failures.add(new NullVariant(index));
            return;
        }

        QueryPurpose purpose = parsePurpose(
                variant.purpose(),
                index,
                failures);
        if (purpose != null) {
            purposeIndexes
                    .computeIfAbsent(
                            purpose,
                            ignored -> new ArrayList<>())
                    .add(index);
        }

        String queryText = variant.queryText();
        if (queryText == null || queryText.isBlank()) {
            failures.add(new BlankQueryText(index));
        }

        if (queryText == null) {
            return;
        }

        Integer firstIndex = firstQueryIndexes.putIfAbsent(
                queryText,
                index);
        if (firstIndex != null) {
            failures.add(new DuplicateQuery(firstIndex, index));
        }

        if (queryText.equals(originalQuery)) {
            failures.add(new UnchangedOriginalQuery(index));
        }

        int queryLength = queryText.codePointCount(
                0,
                queryText.length());
        if (queryLength > maximumQueryLength) {
            failures.add(new QueryTooLong(
                    index,
                    queryLength,
                    maximumQueryLength));
        }
    }

    private QueryPurpose parsePurpose(
            String suppliedPurpose,
            int index,
            List<QueryVariantValidationFailure> failures) {
        if (suppliedPurpose == null) {
            failures.add(new UnknownPurpose(index, null));
            return null;
        }

        try {
            return QueryPurpose.valueOf(suppliedPurpose);
        } catch (IllegalArgumentException exception) {
            failures.add(new UnknownPurpose(
                    index,
                    suppliedPurpose));
            return null;
        }
    }
}

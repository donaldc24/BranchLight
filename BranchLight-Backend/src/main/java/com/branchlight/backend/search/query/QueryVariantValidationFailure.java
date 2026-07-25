package com.branchlight.backend.search.query;

import java.util.List;
import java.util.Objects;

public sealed interface QueryVariantValidationFailure
        permits QueryVariantValidationFailure.MissingVariantList,
        QueryVariantValidationFailure.VariantCount,
        QueryVariantValidationFailure.NullVariant,
        QueryVariantValidationFailure.MissingPurpose,
        QueryVariantValidationFailure.DuplicatePurpose,
        QueryVariantValidationFailure.UnknownPurpose,
        QueryVariantValidationFailure.BlankQueryText,
        QueryVariantValidationFailure.DuplicateQuery,
        QueryVariantValidationFailure.UnchangedOriginalQuery,
        QueryVariantValidationFailure.QueryTooLong {

    String message();

    record MissingVariantList()
            implements QueryVariantValidationFailure {

        @Override
        public String message() {
            return "The generated variant list is missing.";
        }
    }

    record VariantCount(
            int expected,
            int actual)
            implements QueryVariantValidationFailure {

        @Override
        public String message() {
            return "Expected exactly " + expected
                    + " variants but received " + actual + ".";
        }
    }

    record NullVariant(int variantIndex)
            implements QueryVariantValidationFailure {

        @Override
        public String message() {
            return "Variant " + variantIndex + " is null.";
        }
    }

    record MissingPurpose(QueryPurpose purpose)
            implements QueryVariantValidationFailure {

        public MissingPurpose {
            Objects.requireNonNull(purpose, "purpose must not be null");
        }

        @Override
        public String message() {
            return "Missing query variant for " + purpose + ".";
        }
    }

    record DuplicatePurpose(
            QueryPurpose purpose,
            List<Integer> variantIndexes)
            implements QueryVariantValidationFailure {

        public DuplicatePurpose {
            Objects.requireNonNull(purpose, "purpose must not be null");
            variantIndexes = List.copyOf(variantIndexes);
        }

        @Override
        public String message() {
            return "Expected one query variant for " + purpose
                    + " but found variants at indexes "
                    + variantIndexes + ".";
        }
    }

    record UnknownPurpose(
            int variantIndex,
            String suppliedPurpose)
            implements QueryVariantValidationFailure {

        @Override
        public String message() {
            return "Variant " + variantIndex
                    + " has unknown purpose "
                    + (suppliedPurpose == null
                            ? "<null>"
                            : "'" + suppliedPurpose + "'")
                    + ".";
        }
    }

    record BlankQueryText(int variantIndex)
            implements QueryVariantValidationFailure {

        @Override
        public String message() {
            return "Variant " + variantIndex
                    + " has null or blank query text.";
        }
    }

    record DuplicateQuery(
            int firstVariantIndex,
            int duplicateVariantIndex)
            implements QueryVariantValidationFailure {

        @Override
        public String message() {
            return "Variant " + duplicateVariantIndex
                    + " exactly duplicates query text from variant "
                    + firstVariantIndex + ".";
        }
    }

    record UnchangedOriginalQuery(int variantIndex)
            implements QueryVariantValidationFailure {

        @Override
        public String message() {
            return "Variant " + variantIndex
                    + " exactly duplicates the unchanged original query.";
        }
    }

    record QueryTooLong(
            int variantIndex,
            int actualLength,
            int maximumLength)
            implements QueryVariantValidationFailure {

        @Override
        public String message() {
            return "Variant " + variantIndex + " has length "
                    + actualLength + ", exceeding maximum "
                    + maximumLength + ".";
        }
    }
}

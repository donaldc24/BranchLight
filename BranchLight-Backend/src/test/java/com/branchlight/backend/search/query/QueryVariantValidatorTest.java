package com.branchlight.backend.search.query;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryVariantValidatorTest {

    private static final int MAXIMUM_QUERY_LENGTH = 40;

    private final QueryVariantValidator validator =
            new QueryVariantValidator(MAXIMUM_QUERY_LENGTH);

    @Test
    void acceptsExactlyOneUniqueVariantForEveryPurpose() {
        var variants = new ArrayList<>(List.of(
                candidate("clear overview", QueryPurpose.EXPLANATORY),
                candidate("firsthand discussion",
                        QueryPurpose.HUMAN_DISCUSSION),
                candidate("official sources",
                        QueryPurpose.AUTHORITATIVE),
                candidate("limitations", QueryPurpose.CRITICAL),
                candidate("practical examples",
                        QueryPurpose.PRACTICAL)));
        var originalVariants = List.copyOf(variants);

        QueryVariantValidationResult result = validator.validate(
                "original query",
                variants);

        assertThat(result.isValid()).isTrue();
        assertThat(result.failures()).isEmpty();
        assertThat(variants).containsExactlyElementsOf(originalVariants);
    }

    @Test
    void reportsAnIncorrectVariantCount() {
        var variants = new ArrayList<>(validVariants());
        variants.remove(4);

        QueryVariantValidationResult result = validator.validate(
                "original query",
                variants);

        assertThat(result.failures()).containsExactly(
                new VariantCount(5, 4),
                new MissingPurpose(QueryPurpose.HUMAN_DISCUSSION));
    }

    @Test
    void reportsMissingAndDuplicatePurposes() {
        var variants = new ArrayList<>(validVariants());
        variants.set(
                4,
                candidate("another critical query",
                        QueryPurpose.CRITICAL));

        QueryVariantValidationResult result = validator.validate(
                "original query",
                variants);

        assertThat(result.failures()).containsExactly(
                new DuplicatePurpose(
                        QueryPurpose.CRITICAL,
                        List.of(3, 4)),
                new MissingPurpose(QueryPurpose.HUMAN_DISCUSSION));
    }

    @Test
    void reportsUnknownPurposesWithoutNormalizingThem() {
        for (String suppliedPurpose : new String[]{
                "NOT_A_PURPOSE",
                "authoritative",
                " AUTHORITATIVE ",
                null
        }) {
            var variants = new ArrayList<>(validVariants());
            variants.set(
                    0,
                    new QueryVariantCandidate(
                            "official sources",
                            suppliedPurpose));

            QueryVariantValidationResult result = validator.validate(
                    "original query",
                    variants);

            assertThat(result.failures()).containsExactly(
                    new UnknownPurpose(0, suppliedPurpose),
                    new MissingPurpose(QueryPurpose.AUTHORITATIVE));
        }
    }

    @Test
    void reportsNullEmptyAndWhitespaceOnlyQueryText() {
        for (String queryText : new String[]{
                null,
                "",
                " ",
                "\t\r\n"
        }) {
            var variants = new ArrayList<>(validVariants());
            variants.set(
                    2,
                    new QueryVariantCandidate(
                            queryText,
                            QueryPurpose.PRACTICAL.name()));

            QueryVariantValidationResult result = validator.validate(
                    "original query",
                    variants);

            assertThat(result.failures()).containsExactly(
                    new BlankQueryText(2));
        }
    }

    @Test
    void reportsOnlyExactDuplicateQueries() {
        var variants = new ArrayList<>(validVariants());
        variants.set(
                1,
                candidate("official sources",
                        QueryPurpose.EXPLANATORY));

        QueryVariantValidationResult duplicateResult =
                validator.validate("original query", variants);

        assertThat(duplicateResult.failures()).containsExactly(
                new DuplicateQuery(0, 1));

        variants.set(
                0,
                candidate("Same query", QueryPurpose.AUTHORITATIVE));
        variants.set(
                1,
                candidate("same query", QueryPurpose.EXPLANATORY));
        variants.set(
                2,
                candidate("Same query ", QueryPurpose.PRACTICAL));

        assertThat(validator.validate("original query", variants).isValid())
                .isTrue();
    }

    @Test
    void reportsTheUnchangedOriginalQuery() {
        var variants = new ArrayList<>(validVariants());
        variants.set(
                3,
                candidate("original query", QueryPurpose.CRITICAL));

        QueryVariantValidationResult result = validator.validate(
                "original query",
                variants);

        assertThat(result.failures()).containsExactly(
                new UnchangedOriginalQuery(3));
    }

    @Test
    void enforcesTheConfiguredMaximumAtTheUnicodeCodePointBoundary() {
        var lengthTenValidator = new QueryVariantValidator(10);
        var variants = new ArrayList<>(List.of(
                candidate("1234567890", QueryPurpose.AUTHORITATIVE),
                candidate("b", QueryPurpose.EXPLANATORY),
                candidate("c", QueryPurpose.PRACTICAL),
                candidate("d", QueryPurpose.CRITICAL),
                candidate("e", QueryPurpose.HUMAN_DISCUSSION)));

        assertThat(lengthTenValidator.maximumQueryLength()).isEqualTo(10);
        assertThat(lengthTenValidator
                .validate("original query", variants)
                .isValid())
                .isTrue();

        variants.set(
                0,
                candidate("12345678901", QueryPurpose.AUTHORITATIVE));

        assertThat(lengthTenValidator
                .validate("original query", variants)
                .failures())
                .containsExactly(new QueryTooLong(0, 11, 10));

        var codePointValidator = new QueryVariantValidator(2);
        var shortVariants = List.of(
                candidate("😀a", QueryPurpose.AUTHORITATIVE),
                candidate("b", QueryPurpose.EXPLANATORY),
                candidate("c", QueryPurpose.PRACTICAL),
                candidate("d", QueryPurpose.CRITICAL),
                candidate("e", QueryPurpose.HUMAN_DISCUSSION));

        assertThat(codePointValidator
                .validate("original", shortVariants)
                .isValid())
                .isTrue();
    }

    @Test
    void rejectsNonPositiveMaximumQueryLengths() {
        for (int maximum : new int[]{0, -1}) {
            assertThatThrownBy(() -> new QueryVariantValidator(maximum))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(
                            "maximumQueryLength must be greater than zero");
        }
    }

    @Test
    void accumulatesEveryIndependentValidationFailure() {
        var shortValidator = new QueryVariantValidator(10);
        var variants = List.of(
                candidate(" ", QueryPurpose.AUTHORITATIVE),
                candidate("dup", QueryPurpose.AUTHORITATIVE),
                candidate("dup", QueryPurpose.EXPLANATORY),
                candidate("orig", QueryPurpose.PRACTICAL),
                candidate("crit", QueryPurpose.CRITICAL),
                new QueryVariantCandidate(
                        "12345678901",
                        "NOT_A_PURPOSE"));

        QueryVariantValidationResult result = shortValidator.validate(
                "orig",
                variants);

        assertThat(result.failures()).containsExactly(
                new VariantCount(5, 6),
                new BlankQueryText(0),
                new DuplicateQuery(1, 2),
                new UnchangedOriginalQuery(3),
                new UnknownPurpose(5, "NOT_A_PURPOSE"),
                new QueryTooLong(5, 11, 10),
                new DuplicatePurpose(
                        QueryPurpose.AUTHORITATIVE,
                        List.of(0, 1)),
                new MissingPurpose(QueryPurpose.HUMAN_DISCUSSION));
        assertThat(result.failures())
                .allSatisfy(failure ->
                        assertThat(failure.message()).isNotBlank());
    }

    @Test
    void reportsNullResultsAndNullVariantEntries() {
        QueryVariantValidationResult nullResult = validator.validate(
                "original query",
                null);

        assertThat(nullResult.failures()).containsExactly(
                new MissingVariantList(),
                new MissingPurpose(QueryPurpose.AUTHORITATIVE),
                new MissingPurpose(QueryPurpose.EXPLANATORY),
                new MissingPurpose(QueryPurpose.PRACTICAL),
                new MissingPurpose(QueryPurpose.CRITICAL),
                new MissingPurpose(QueryPurpose.HUMAN_DISCUSSION));

        var variants = new ArrayList<>(validVariants());
        variants.set(4, null);

        QueryVariantValidationResult nullVariantResult =
                validator.validate("original query", variants);

        assertThat(nullVariantResult.failures()).containsExactly(
                new NullVariant(4),
                new MissingPurpose(QueryPurpose.HUMAN_DISCUSSION));
    }

    @Test
    void returnsAnImmutableDefensiveCopyOfFailures() {
        var sourceFailures =
                new ArrayList<QueryVariantValidationFailure>();
        var result = new QueryVariantValidationResult(sourceFailures);
        sourceFailures.add(new BlankQueryText(0));

        assertThat(result.failures()).isEmpty();
        assertThatThrownBy(() ->
                result.failures().add(new BlankQueryText(1)))
                .isInstanceOf(UnsupportedOperationException.class);

        var indexes = new ArrayList<>(List.of(0, 1));
        var duplicatePurpose = new DuplicatePurpose(
                QueryPurpose.AUTHORITATIVE,
                indexes);
        indexes.add(2);

        assertThat(duplicatePurpose.variantIndexes())
                .containsExactly(0, 1);
        assertThatThrownBy(() ->
                duplicatePurpose.variantIndexes().add(2))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidOriginalQueryAsCallerInput() {
        assertThatThrownBy(() ->
                validator.validate(null, validVariants()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("originalQuery must not be null");

        for (String originalQuery : new String[]{"", " ", "\t\r\n"}) {
            assertThatThrownBy(() ->
                    validator.validate(
                            originalQuery,
                            validVariants()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("originalQuery must not be blank");
        }
    }

    private List<QueryVariantCandidate> validVariants() {
        return List.of(
                candidate("official sources",
                        QueryPurpose.AUTHORITATIVE),
                candidate("clear explanation",
                        QueryPurpose.EXPLANATORY),
                candidate("practical examples",
                        QueryPurpose.PRACTICAL),
                candidate("limitations", QueryPurpose.CRITICAL),
                candidate("firsthand discussion",
                        QueryPurpose.HUMAN_DISCUSSION));
    }

    private QueryVariantCandidate candidate(
            String queryText,
            QueryPurpose purpose) {
        return new QueryVariantCandidate(
                queryText,
                purpose.name());
    }
}

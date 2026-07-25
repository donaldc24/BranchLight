package com.branchlight.backend.search.query;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicQueryVariantGeneratorTest {

    private final DeterministicQueryVariantGenerator generator =
            new DeterministicQueryVariantGenerator();

    @Test
    void generatesOneDeterministicVariantForEachPurpose() {
        var expected = List.of(
                new GeneratedQuery(
                        "sample query original official primary direct sources",
                        QueryPurpose.AUTHORITATIVE),
                new GeneratedQuery(
                        "sample query clear explanation overview",
                        QueryPurpose.EXPLANATORY),
                new GeneratedQuery(
                        "sample query examples procedures guides "
                                + "practical application",
                        QueryPurpose.PRACTICAL),
                new GeneratedQuery(
                        "sample query limitations risks counterarguments "
                                + "tradeoffs",
                        QueryPurpose.CRITICAL),
                new GeneratedQuery(
                        "sample query firsthand experiences "
                                + "substantive discussion",
                        QueryPurpose.HUMAN_DISCUSSION));

        assertEquals(expected, generator.generate("sample query"));
        assertEquals(expected, generator.generate("sample query"));
    }

    @Test
    void preservesQuotedPhrasesAndExplicitSearchOperators() {
        String originalQuery =
                "  intitle:\"shared understanding\" filetype:pdf -draft  ";
        String unchangedOriginalQuery = originalQuery;

        var generatedQueries = generator.generate(originalQuery);

        assertEquals(unchangedOriginalQuery, originalQuery);
        for (GeneratedQuery generatedQuery : generatedQueries) {
            assertAll(
                    () -> assertTrue(generatedQuery.queryText().startsWith(
                            originalQuery.strip() + " ")),
                    () -> assertTrue(generatedQuery.queryText().contains(
                            "\"shared understanding\"")),
                    () -> assertTrue(generatedQuery.queryText().contains(
                            "intitle:\"shared understanding\"")),
                    () -> assertTrue(generatedQuery.queryText().contains(
                            "filetype:pdf")),
                    () -> assertTrue(generatedQuery.queryText().contains(
                            "-draft")));
        }
    }

    @Test
    void alwaysReturnsFiveNonBlankQueriesWithUniqueText() {
        var generatedQueries = generator.generate(
                "original official explanation examples risks discussion");

        assertAll(
                () -> assertEquals(5, generatedQueries.size()),
                () -> assertTrue(generatedQueries.stream()
                        .noneMatch(query -> query.queryText().isBlank())),
                () -> assertEquals(
                        5,
                        generatedQueries.stream()
                                .map(GeneratedQuery::queryText)
                                .distinct()
                                .count()),
                () -> assertEquals(
                        List.of(QueryPurpose.values()),
                        generatedQueries.stream()
                                .map(GeneratedQuery::purpose)
                                .toList()));
    }

    @Test
    void rejectsNullOriginalQuery() {
        var exception = assertThrows(
                NullPointerException.class,
                () -> generator.generate(null));

        assertEquals("originalQuery must not be null", exception.getMessage());
    }

    @Test
    void rejectsBlankOriginalQuery() {
        for (String originalQuery : new String[]{"", " ", "\t\r\n"}) {
            var exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> generator.generate(originalQuery));

            assertEquals(
                    "originalQuery must not be blank",
                    exception.getMessage());
        }
    }
}

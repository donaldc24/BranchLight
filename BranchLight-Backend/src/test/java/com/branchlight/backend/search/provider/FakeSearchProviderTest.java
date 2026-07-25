package com.branchlight.backend.search.provider;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeSearchProviderTest {

    private static final RawSearchResult FIRST_RESULT = new RawSearchResult(
            URI.create("https://example.com/first"),
            "First result",
            "First snippet",
            1,
            LocalDate.of(2026, 7, 24),
            "virtual threads",
            "Find the primary source");

    private static final RawSearchResult SECOND_RESULT = new RawSearchResult(
            URI.create("https://example.com/second"),
            "Second result",
            "Second snippet",
            2,
            null,
            "virtual threads explained",
            "Find an accessible explanation");

    @Test
    void returnsConfiguredResultsInOrderUpToTheLimit() {
        var provider = new FakeSearchProvider(
                List.of(FIRST_RESULT, SECOND_RESULT));

        var results = provider.search("virtual threads", 1);

        assertEquals(List.of(FIRST_RESULT), results);
        assertEquals(
                List.of(new FakeSearchProvider.SearchInvocation(
                        "virtual threads",
                        1)),
                provider.invocations());
    }

    @Test
    void returnsNoResultsWhenTheLimitIsZero() {
        var provider = new FakeSearchProvider(List.of(FIRST_RESULT));

        var results = provider.search("virtual threads", 0);

        assertTrue(results.isEmpty());
        assertEquals(0, provider.invocations().get(0).resultLimit());
    }

    @Test
    void rawResultSupportsAnOptionalPublicationDateAndRetrievalMetadata() {
        assertAll(
                () -> assertNull(SECOND_RESULT.publicationDate()),
                () -> assertEquals(
                        "virtual threads explained",
                        SECOND_RESULT.retrievalQuery()),
                () -> assertEquals(
                        "Find an accessible explanation",
                        SECOND_RESULT.retrievalPurpose()),
                () -> assertEquals(2, SECOND_RESULT.providerRank()));
    }
}

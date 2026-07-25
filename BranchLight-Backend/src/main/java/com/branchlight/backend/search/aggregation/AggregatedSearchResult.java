package com.branchlight.backend.search.aggregation;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record AggregatedSearchResult(
        URI url,
        String title,
        int providerRank,
        LocalDate publicationDate,
        List<String> snippets,
        List<RetrievalMetadata> retrievals) {

    public AggregatedSearchResult {
        Objects.requireNonNull(url, "url must not be null");
        Objects.requireNonNull(title, "title must not be null");
        snippets = List.copyOf(Objects.requireNonNull(
                snippets,
                "snippets must not be null"));
        retrievals = List.copyOf(Objects.requireNonNull(
                retrievals,
                "retrievals must not be null"));
    }
}

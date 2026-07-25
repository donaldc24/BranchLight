package com.branchlight.backend.search.provider;

import java.net.URI;
import java.time.LocalDate;
import java.util.Objects;

public record RawSearchResult(
        URI url,
        String title,
        String snippet,
        int providerRank,
        LocalDate publicationDate,
        String retrievalQuery,
        String retrievalPurpose) {

    public RawSearchResult {
        Objects.requireNonNull(url, "url must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(snippet, "snippet must not be null");
        Objects.requireNonNull(
                retrievalQuery,
                "retrievalQuery must not be null");
        Objects.requireNonNull(
                retrievalPurpose,
                "retrievalPurpose must not be null");
    }
}

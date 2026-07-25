package com.branchlight.backend.search.ranking;

import java.util.List;
import java.util.Objects;

import com.branchlight.backend.search.aggregation.AggregatedSearchResult;
import com.branchlight.backend.search.content.ExtractedDocument;
import com.branchlight.backend.search.content.Passage;

public record CandidateDocument(
        AggregatedSearchResult searchResult,
        ExtractedDocument document,
        List<Passage> passages) {

    public CandidateDocument {
        Objects.requireNonNull(
                searchResult,
                "searchResult must not be null");
        Objects.requireNonNull(document, "document must not be null");
        passages = List.copyOf(Objects.requireNonNull(
                passages,
                "passages must not be null"));
        if (passages.stream().anyMatch(passage -> passage == null
                || !passage.documentId().equals(document.documentId()))) {
            throw new IllegalArgumentException(
                    "passages must belong to the candidate document");
        }
    }
}
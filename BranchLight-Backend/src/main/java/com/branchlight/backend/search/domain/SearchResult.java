package com.branchlight.backend.search.domain;

import java.net.URI;
import java.util.Objects;

public record SearchResult(
        SearchRole role,
        String title,
        URI url,
        String domain,
        String snippet,
        String selectionReason,
        Double score) {

    public SearchResult {
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(url, "url must not be null");
        Objects.requireNonNull(domain, "domain must not be null");
        Objects.requireNonNull(snippet, "snippet must not be null");
        Objects.requireNonNull(selectionReason, "selectionReason must not be null");
    }
}

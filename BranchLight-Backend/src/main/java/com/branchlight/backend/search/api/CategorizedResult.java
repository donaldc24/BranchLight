package com.branchlight.backend.search.api;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.branchlight.backend.search.domain.SearchRole;

public record CategorizedResult(
        SearchRole role,
        String title,
        String url,
        String domain,
        String snippet,
        String selectionReason,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double score) {

    public CategorizedResult {
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(url, "url must not be null");
        Objects.requireNonNull(domain, "domain must not be null");
        Objects.requireNonNull(snippet, "snippet must not be null");
        Objects.requireNonNull(selectionReason, "selectionReason must not be null");
    }
}

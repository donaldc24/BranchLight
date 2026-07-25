package com.branchlight.backend.search.query;

/**
 * An intentionally unvalidated query variant received from a generator.
 */
public record QueryVariantCandidate(
        String queryText,
        String purpose) {
}

package com.branchlight.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
        prefix = "branchlight.search.preliminary-ranking")
public record PreliminaryCandidateRankingProperties(
        int maximumCandidates,
        int minimumDistinctTitleTerms,
        int minimumDistinctSnippetTerms,
        double titleLexicalOverlapWeight,
        double snippetLexicalOverlapWeight,
        double providerRankPriorWeight,
        double generatedQueryDiscoveryWeight,
        double retrievalPurposeDiversityWeight,
        double titleSpecificityWeight,
        double lowQualityPenaltyWeight) {
}

package com.branchlight.backend.search.ranking;

public record PreliminaryCandidateRankingWeights(
        double titleLexicalOverlap,
        double snippetLexicalOverlap,
        double providerRankPrior,
        double generatedQueryDiscovery,
        double retrievalPurposeDiversity,
        double titleSpecificity,
        double lowQualityPenalty) {

    public PreliminaryCandidateRankingWeights {
        requireValidWeight(
                titleLexicalOverlap,
                "titleLexicalOverlap");
        requireValidWeight(
                snippetLexicalOverlap,
                "snippetLexicalOverlap");
        requireValidWeight(
                providerRankPrior,
                "providerRankPrior");
        requireValidWeight(
                generatedQueryDiscovery,
                "generatedQueryDiscovery");
        requireValidWeight(
                retrievalPurposeDiversity,
                "retrievalPurposeDiversity");
        requireValidWeight(
                titleSpecificity,
                "titleSpecificity");
        requireValidWeight(
                lowQualityPenalty,
                "lowQualityPenalty");
    }

    private static void requireValidWeight(
            double weight,
            String name) {
        if (!Double.isFinite(weight) || weight < 0.0) {
            throw new IllegalArgumentException(
                    name + " must be finite and non-negative");
        }
    }
}

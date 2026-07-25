package com.branchlight.backend.search.ranking;

public record RelevanceWeights(
        double title,
        double snippet,
        double heading,
        double strongestPassage,
        double secondStrongestPassage,
        double keywordCoverage,
        double phraseCoverage,
        double providerRankPrior) {

    public static final RelevanceWeights DEFAULTS = new RelevanceWeights(
            0.16,
            0.10,
            0.12,
            0.20,
            0.10,
            0.18,
            0.11,
            0.03);

    public RelevanceWeights {
        requireValid(title, "title");
        requireValid(snippet, "snippet");
        requireValid(heading, "heading");
        requireValid(strongestPassage, "strongestPassage");
        requireValid(
                secondStrongestPassage,
                "secondStrongestPassage");
        requireValid(keywordCoverage, "keywordCoverage");
        requireValid(phraseCoverage, "phraseCoverage");
        requireValid(providerRankPrior, "providerRankPrior");
        if (title
            + snippet
            + heading
            + strongestPassage
            + secondStrongestPassage
            + keywordCoverage
            + phraseCoverage
            + providerRankPrior == 0.0) {
            throw new IllegalArgumentException(
                    "at least one relevance weight must be positive");
        }
    }

    public double total() {
        return title
                + snippet
                + heading
                + strongestPassage
                + secondStrongestPassage
                + keywordCoverage
                + phraseCoverage
                + providerRankPrior;
    }

    private static void requireValid(double weight, String name) {
        if (!Double.isFinite(weight) || weight < 0.0) {
            throw new IllegalArgumentException(
                    name + " must be finite and non-negative");
        }
    }
}
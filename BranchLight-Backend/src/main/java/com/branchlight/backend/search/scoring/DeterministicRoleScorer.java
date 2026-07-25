package com.branchlight.backend.search.scoring;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.branchlight.backend.search.domain.SearchRole;
import com.branchlight.backend.search.eligibility.RoleEligibilityResult;
import com.branchlight.backend.search.features.SourceFeature;
import com.branchlight.backend.search.features.SourceFeatureSet;

public final class DeterministicRoleScorer {

    private final RoleScoringConfiguration configuration;
    private final Clock clock;

    public DeterministicRoleScorer() {
        this(RoleScoringConfiguration.DEFAULTS, Clock.systemUTC());
    }

    public DeterministicRoleScorer(
            RoleScoringConfiguration configuration) {
        this(configuration, Clock.systemUTC());
    }

    public DeterministicRoleScorer(
            RoleScoringConfiguration configuration,
            Clock clock) {
        this.configuration = Objects.requireNonNull(
                configuration,
                "configuration must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public CandidateDeterministicRoleScores score(
            RoleScoringInput input) {
        Objects.requireNonNull(input, "input must not be null");
        var scores = new EnumMap<SearchRole, DeterministicRoleScore>(
                SearchRole.class);
        for (SearchRole role : SearchRole.values()) {
            RoleEligibilityResult eligibility =
                    input.eligibility().role(role);
            scores.put(role, eligibility.eligible()
                    ? scoreEligible(role, eligibility, input)
                    : ineligible(role, eligibility));
        }
        return new CandidateDeterministicRoleScores(
                input.features().documentId(),
                scores);
    }

    public List<CandidateDeterministicRoleScores> scoreAll(
            List<RoleScoringInput> candidates) {
        Objects.requireNonNull(
                candidates,
                "candidates must not be null");
        return candidates.stream()
                .map(candidate -> score(Objects.requireNonNull(
                        candidate,
                        "candidate must not be null")))
                .toList();
    }

    public RoleScoringConfiguration configuration() {
        return configuration;
    }

    private DeterministicRoleScore scoreEligible(
            SearchRole role,
            RoleEligibilityResult eligibility,
            RoleScoringInput input) {
        SourceFeatureSet features = input.features();
        double relevance = input.candidate().overallPageRelevance();
        double roleFeatureScore = weightedFeatureScore(
                features,
                configuration.featuresFor(role),
                false);
        double provenanceQualityScore =
                provenanceQualityScore(features);
        double providerRankPrior = providerRankPrior(input.candidate()
                .candidate()
                .searchResult()
                .providerRank());
        LocalDate publicationDate = input.candidate()
                .candidate()
                .searchResult()
                .publicationDate();
        boolean freshnessAvailable = publicationDate != null;
        double freshness = freshnessAvailable
                ? freshness(publicationDate)
                : 0.0;
        double retrievalPurposeMatch = input.candidate()
                .candidate()
                .searchResult()
                .retrievals()
                .stream()
                .anyMatch(retrieval -> retrieval.purpose()
                        .equalsIgnoreCase(role.name()))
                                ? 1.0
                                : 0.0;
        double riskScore = weightedFeatureScore(
                features,
                configuration.riskFeatureWeights(),
                false);

        double effectiveWeight = configuration.relevanceWeight()
                + configuration.roleFeaturesWeight()
                + configuration.provenanceQualityWeight()
                + configuration.providerRankPriorWeight()
                + configuration.retrievalPurposeMatchWeight()
                + (freshnessAvailable
                        ? configuration.freshnessWeight()
                        : 0.0);
        var breakdown = new RoleScoreBreakdown(
                component(
                        relevance,
                        configuration.relevanceWeight(),
                        effectiveWeight,
                        true),
                component(
                        roleFeatureScore,
                        configuration.roleFeaturesWeight(),
                        effectiveWeight,
                        true),
                component(
                        provenanceQualityScore,
                        configuration.provenanceQualityWeight(),
                        effectiveWeight,
                        true),
                component(
                        providerRankPrior,
                        configuration.providerRankPriorWeight(),
                        effectiveWeight,
                        true),
                component(
                        freshness,
                        configuration.freshnessWeight(),
                        effectiveWeight,
                        freshnessAvailable),
                component(
                        retrievalPurposeMatch,
                        configuration.retrievalPurposeMatchWeight(),
                        effectiveWeight,
                        true),
                new RoleScoreBreakdown.RiskPenalty(
                        riskScore,
                        configuration.riskPenaltyWeight(),
                        riskScore * configuration.riskPenaltyWeight()));
        double finalScore = breakdown.finalScore();
        return new DeterministicRoleScore(
                role,
                finalScore,
                breakdown,
                reason(role, finalScore, relevance, riskScore, features),
                eligibility);
    }

    private DeterministicRoleScore ineligible(
            SearchRole role,
            RoleEligibilityResult eligibility) {
        String rejectedBy = eligibility.rejectingFeatureNames().isEmpty()
                ? "eligibility thresholds"
                : String.join(",", eligibility.rejectingFeatureNames());
        return new DeterministicRoleScore(
                role,
                null,
                null,
                "Ineligible for " + role.name() + ": " + rejectedBy + ".",
                eligibility);
    }

    private double provenanceQualityScore(SourceFeatureSet features) {
        double weightedValue = 0.0;
        double totalWeight = 0.0;
        for (Map.Entry<SourceFeature, Double> entry
                : configuration.provenanceFeatureWeights().entrySet()) {
            weightedValue += features.value(entry.getKey())
                    .normalizedValue() * entry.getValue();
            totalWeight += entry.getValue();
        }
        for (Map.Entry<SourceFeature, Double> entry
                : configuration.qualityFeatureWeights().entrySet()) {
            weightedValue += (1.0 - features.value(entry.getKey())
                    .normalizedValue()) * entry.getValue();
            totalWeight += entry.getValue();
        }
        return totalWeight == 0.0 ? 0.0 : weightedValue / totalWeight;
    }

    private static double weightedFeatureScore(
            SourceFeatureSet features,
            Map<SourceFeature, Double> weights,
            boolean invert) {
        double weightedValue = 0.0;
        double totalWeight = 0.0;
        for (Map.Entry<SourceFeature, Double> entry : weights.entrySet()) {
            double value = features.value(entry.getKey()).normalizedValue();
            weightedValue += (invert ? 1.0 - value : value)
                    * entry.getValue();
            totalWeight += entry.getValue();
        }
        return totalWeight == 0.0 ? 0.0 : weightedValue / totalWeight;
    }

    private RoleScoreBreakdown.ScoreComponent component(
            double score,
            double configuredWeight,
            double effectiveWeight,
            boolean available) {
        if (!available) {
            return new RoleScoreBreakdown.ScoreComponent(
                    0.0,
                    0.0,
                    0.0,
                    false);
        }
        double normalizedWeight = configuredWeight / effectiveWeight;
        return new RoleScoreBreakdown.ScoreComponent(
                score,
                normalizedWeight,
                score * normalizedWeight,
                true);
    }

    private double freshness(LocalDate publicationDate) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        long ageDays = Math.max(
                0,
                ChronoUnit.DAYS.between(publicationDate, today));
        return clamp(1.0
                - (double) ageDays / configuration.freshnessHorizonDays());
    }

    private String reason(
            SearchRole role,
            double finalScore,
            double relevance,
            double risk,
            SourceFeatureSet features) {
        var strongestFeatures = new ArrayList<>(
                configuration.featuresFor(role).keySet());
        strongestFeatures.sort(Comparator
                .comparingDouble((SourceFeature feature) ->
                        features.value(feature).normalizedValue()
                                * configuration.featuresFor(role)
                                        .get(feature))
                .reversed()
                .thenComparing(SourceFeature::name));
        String featureNames = strongestFeatures.stream()
                .filter(feature -> features.value(feature)
                        .normalizedValue() > 0.0)
                .limit(2)
                .map(SourceFeature::name)
                .reduce((left, right) -> left + "," + right)
                .orElse("NONE");
        return String.format(
                Locale.ROOT,
                "%s score=%.3f; relevance=%.3f; features=%s; risk=%.3f.",
                role.name(),
                finalScore,
                relevance,
                featureNames,
                risk);
    }

    private static double providerRankPrior(int providerRank) {
        return providerRank > 0 ? 1.0 / providerRank : 0.0;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
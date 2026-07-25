package com.branchlight.backend.search.scoring;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.branchlight.backend.search.aggregation.AggregatedSearchResult;
import com.branchlight.backend.search.aggregation.RetrievalMetadata;
import com.branchlight.backend.search.content.ExtractedBlock;
import com.branchlight.backend.search.content.ExtractedDocument;
import com.branchlight.backend.search.content.Passage;
import com.branchlight.backend.search.content.SourcePosition;
import com.branchlight.backend.search.domain.SearchRole;
import com.branchlight.backend.search.eligibility.CandidateRoleEligibility;
import com.branchlight.backend.search.eligibility.RoleEligibilityResult;
import com.branchlight.backend.search.features.SourceFeature;
import com.branchlight.backend.search.features.SourceFeatureSet;
import com.branchlight.backend.search.features.SourceFeatureValue;
import com.branchlight.backend.search.ranking.CandidateDocument;
import com.branchlight.backend.search.ranking.RelevanceScoreBreakdown;
import com.branchlight.backend.search.ranking.ScoredCandidateDocument;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicRoleScorerTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-24T12:00:00Z"),
            ZoneOffset.UTC);

    private final DeterministicRoleScorer scorer =
            new DeterministicRoleScorer(
                    RoleScoringConfiguration.DEFAULTS,
                    CLOCK);

    @Test
    void calculatesNormalizedScoresForEveryEligibleRole() {
        var values = new EnumMap<SourceFeature, Double>(
                SourceFeature.class);
        for (SourceFeature feature : SourceFeature.values()) {
            if (feature.group()
                    != com.branchlight.backend.search.features
                            .SourceFeatureGroup.QUALITY_AND_RISK) {
                values.put(feature, 0.8);
            }
        }
        RoleScoringInput input = input(
                "all-roles",
                0.75,
                values,
                EnumSet.allOf(SearchRole.class),
                2,
                LocalDate.of(2026, 7, 1),
                List.of(
                        retrieval(SearchRole.AUTHORITATIVE),
                        retrieval(SearchRole.EXPLANATORY),
                        retrieval(SearchRole.PRACTICAL),
                        retrieval(SearchRole.CRITICAL),
                        retrieval(SearchRole.HUMAN_DISCUSSION)));

        CandidateDeterministicRoleScores result = scorer.score(input);

        assertThat(result.roleScores()).hasSize(SearchRole.values().length);
        for (SearchRole role : SearchRole.values()) {
            DeterministicRoleScore roleScore = result.role(role);
            assertThat(roleScore.finalScore()).as(role.name())
                    .isBetween(0.0, 1.0);
            assertThat(roleScore.scoreBreakdown().finalScore())
                    .isEqualTo(roleScore.finalScore());
            assertThat(roleScore.scoreBreakdown()
                    .originalQueryRelevance()
                    .score()).isEqualTo(0.75);
            assertThat(roleScore.scoreBreakdown()
                    .roleFeatures()
                    .score()).isBetween(0.799, 0.801);
            assertThat(roleScore.reason())
                    .startsWith(role.name() + " score=")
                    .contains("relevance=", "features=", "risk=");
            assertThat(roleScore.eligibilityResult().eligible()).isTrue();
        }
    }

    @Test
    void appliesRoleSpecificFeatureSets() {
        RoleScoringInput input = input(
                "role-specific",
                0.70,
                Map.of(
                        SourceFeature.ORDERED_STEPS,
                        1.0,
                        SourceFeature.WORKED_EXAMPLES,
                        1.0),
                EnumSet.of(SearchRole.PRACTICAL),
                1,
                null,
                List.of());

        CandidateDeterministicRoleScores result = scorer.score(input);

        assertThat(result.role(SearchRole.PRACTICAL)
                .scoreBreakdown()
                .roleFeatures()
                .score()).isGreaterThan(0.0);
        assertThat(result.role(SearchRole.AUTHORITATIVE).finalScore())
                .isNull();
    }

    @Test
    void riskPenaltiesReduceOtherwiseEquivalentScores() {
        Map<SourceFeature, Double> positiveFeatures = Map.of(
                SourceFeature.DEFINITIONS_PRESENT,
                0.9,
                SourceFeature.EXAMPLES_PRESENT,
                0.9,
                SourceFeature.CONCEPTUAL_PROGRESSION,
                0.9);
        var riskyFeatures = new EnumMap<SourceFeature, Double>(
                positiveFeatures);
        riskyFeatures.put(SourceFeature.DUPLICATED_TEXT, 0.8);
        riskyFeatures.put(SourceFeature.EXCESSIVE_ADVERTISEMENTS, 0.8);
        riskyFeatures.put(SourceFeature.UNSUPPORTED_CERTAINTY, 0.8);
        RoleScoringInput clean = input(
                "clean",
                0.75,
                positiveFeatures,
                EnumSet.of(SearchRole.EXPLANATORY),
                3,
                null,
                List.of());
        RoleScoringInput risky = input(
                "risky",
                0.75,
                riskyFeatures,
                EnumSet.of(SearchRole.EXPLANATORY),
                3,
                null,
                List.of());

        DeterministicRoleScore cleanScore = scorer.score(clean)
                .role(SearchRole.EXPLANATORY);
        DeterministicRoleScore riskyScore = scorer.score(risky)
                .role(SearchRole.EXPLANATORY);

        assertThat(riskyScore.finalScore())
                .isLessThan(cleanScore.finalScore());
        assertThat(riskyScore.scoreBreakdown()
                .riskPenalty()
                .contribution()).isPositive();
        assertThat(cleanScore.scoreBreakdown()
                .riskPenalty()
                .contribution()).isZero();
    }

    @Test
    void returnsEligibilityWithoutScoringIneligibleRoles() {
        RoleScoringInput input = input(
                "ineligible",
                0.20,
                Map.of(),
                EnumSet.noneOf(SearchRole.class),
                1,
                null,
                List.of());

        DeterministicRoleScore result = scorer.score(input)
                .role(SearchRole.CRITICAL);

        assertThat(result.finalScore()).isNull();
        assertThat(result.scoreBreakdown()).isNull();
        assertThat(result.eligibilityResult().eligible()).isFalse();
        assertThat(result.reason()).startsWith("Ineligible for CRITICAL:");
    }

    @Test
    void retrievalPurposeProvidesOnlySmallSupport() {
        Map<SourceFeature, Double> values = Map.of(
                SourceFeature.ORDERED_STEPS,
                0.8,
                SourceFeature.WORKED_EXAMPLES,
                0.8);
        RoleScoringInput withoutMatch = input(
                "without-match",
                0.65,
                values,
                EnumSet.of(SearchRole.PRACTICAL),
                4,
                null,
                List.of());
        RoleScoringInput withMatch = input(
                "with-match",
                0.65,
                values,
                EnumSet.of(SearchRole.PRACTICAL),
                4,
                null,
                List.of(retrieval(SearchRole.PRACTICAL)));

        DeterministicRoleScore unmatched = scorer.score(withoutMatch)
                .role(SearchRole.PRACTICAL);
        DeterministicRoleScore matched = scorer.score(withMatch)
                .role(SearchRole.PRACTICAL);

        assertThat(matched.finalScore()).isGreaterThan(unmatched.finalScore());
        assertThat(matched.finalScore() - unmatched.finalScore())
                .isLessThanOrEqualTo(0.03);
        assertThat(matched.scoreBreakdown()
                .retrievalPurposeMatch()
                .normalizedWeight()).isLessThanOrEqualTo(0.03);
    }

    @Test
    void excludesUnavailableFreshnessFromTheWeightDenominator() {
        RoleScoringInput input = input(
                "undated",
                0.70,
                Map.of(
                        SourceFeature.LIMITATIONS_SECTIONS,
                        0.9,
                        SourceFeature.TRADEOFFS,
                        0.9),
                EnumSet.of(SearchRole.CRITICAL),
                2,
                null,
                List.of());

        RoleScoreBreakdown breakdown = scorer.score(input)
                .role(SearchRole.CRITICAL)
                .scoreBreakdown();

        assertThat(breakdown.freshness().available()).isFalse();
        assertThat(breakdown.freshness().normalizedWeight()).isZero();
        assertThat(breakdown.originalQueryRelevance().normalizedWeight())
                .isGreaterThan(
                        RoleScoringConfiguration.DEFAULTS.relevanceWeight());
    }

    private static RoleScoringInput input(
            String documentId,
            double relevance,
            Map<SourceFeature, Double> configuredFeatures,
            EnumSet<SearchRole> eligibleRoles,
            int providerRank,
            LocalDate publicationDate,
            List<RetrievalMetadata> retrievals) {
        SourceFeatureSet features = features(
                documentId,
                configuredFeatures);
        ScoredCandidateDocument candidate = candidate(
                documentId,
                relevance,
                providerRank,
                publicationDate,
                retrievals);
        CandidateRoleEligibility eligibility = eligibility(
                documentId,
                eligibleRoles);
        return new RoleScoringInput(candidate, features, eligibility);
    }

    private static SourceFeatureSet features(
            String documentId,
            Map<SourceFeature, Double> configured) {
        var values = new EnumMap<SourceFeature, SourceFeatureValue>(
                SourceFeature.class);
        for (SourceFeature feature : SourceFeature.values()) {
            double value = configured.getOrDefault(feature, 0.0);
            values.put(feature, new SourceFeatureValue(value, value));
        }
        return new SourceFeatureSet(documentId, values);
    }

    private static ScoredCandidateDocument candidate(
            String documentId,
            double relevance,
            int providerRank,
            LocalDate publicationDate,
            List<RetrievalMetadata> retrievals) {
        String text = "Candidate document content.";
        SourcePosition position = new SourcePosition(0, text.length());
        var document = new ExtractedDocument(
                documentId,
                List.of(ExtractedBlock.paragraph(text, position)));
        var passage = new Passage(
                documentId,
                List.of(),
                text,
                position,
                3);
        var searchResult = new AggregatedSearchResult(
                URI.create("https://example.test/" + documentId),
                "Candidate title",
                providerRank,
                publicationDate,
                List.of("Candidate snippet"),
                retrievals);
        var candidate = new CandidateDocument(
                searchResult,
                document,
                List.of(passage));
        return new ScoredCandidateDocument(
                candidate,
                relevance,
                0.0,
                0.0,
                List.of(),
                emptyRelevanceBreakdown());
    }

    private static CandidateRoleEligibility eligibility(
            String documentId,
            EnumSet<SearchRole> eligibleRoles) {
        var roles = new EnumMap<SearchRole, RoleEligibilityResult>(
                SearchRole.class);
        for (SearchRole role : SearchRole.values()) {
            boolean eligible = eligibleRoles.contains(role);
            roles.put(role, new RoleEligibilityResult(
                    role,
                    eligible,
                    eligible ? 0.8 : 0.2,
                    eligible ? List.of("ROLE_FEATURE") : List.of(),
                    eligible ? List.of() : List.of("MISSING_ROLE_FEATURE"),
                    List.of("deterministic test eligibility")));
        }
        return new CandidateRoleEligibility(documentId, roles);
    }

    private static RetrievalMetadata retrieval(SearchRole role) {
        return new RetrievalMetadata("supporting query", role.name());
    }

    private static RelevanceScoreBreakdown emptyRelevanceBreakdown() {
        var component = new RelevanceScoreBreakdown.ScoreComponent(
                0.0,
                0.0,
                0.0);
        return new RelevanceScoreBreakdown(
                component,
                component,
                component,
                component,
                component,
                component,
                component,
                component);
    }
}
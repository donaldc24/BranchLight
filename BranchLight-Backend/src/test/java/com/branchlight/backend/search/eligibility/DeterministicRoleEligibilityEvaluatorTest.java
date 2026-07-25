package com.branchlight.backend.search.eligibility;

import java.net.URI;
import java.util.EnumMap;
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
import com.branchlight.backend.search.features.SourceFeature;
import com.branchlight.backend.search.features.SourceFeatureSet;
import com.branchlight.backend.search.features.SourceFeatureValue;
import com.branchlight.backend.search.ranking.CandidateDocument;
import com.branchlight.backend.search.ranking.RelevanceScoreBreakdown;
import com.branchlight.backend.search.ranking.ScoredCandidateDocument;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicRoleEligibilityEvaluatorTest {

    private final DeterministicRoleEligibilityEvaluator evaluator =
            new DeterministicRoleEligibilityEvaluator();

    @Test
    void marksCandidatesEligibleFromRelevantRoleEvidence() {
        assertEligible(
                SearchRole.AUTHORITATIVE,
                SourceFeature.IDENTIFIED_AUTHOR,
                SourceFeature.REFERENCES_OR_CITATIONS_PRESENT,
                SourceFeature.ORIGINAL_OR_FIRST_PARTY_MATERIAL);
        assertEligible(
                SearchRole.EXPLANATORY,
                SourceFeature.DEFINITIONS_PRESENT,
                SourceFeature.EXAMPLES_PRESENT,
                SourceFeature.CONCEPTUAL_PROGRESSION);
        assertEligible(
                SearchRole.PRACTICAL,
                SourceFeature.ORDERED_STEPS,
                SourceFeature.WORKED_EXAMPLES);
        assertEligible(
                SearchRole.CRITICAL,
                SourceFeature.LIMITATIONS_SECTIONS,
                SourceFeature.TRADEOFFS);
        assertEligible(
                SearchRole.HUMAN_DISCUSSION,
                SourceFeature.MULTIPLE_PARTICIPANTS,
                SourceFeature.REPLIES_OR_COMMENTS,
                SourceFeature.FIRST_PERSON_EXPERIENCE);
    }

    @Test
    void rejectsIrrelevantThinCandidatesWithDiagnosticReasons() {
        RoleEligibilityInput input = input(
                "thin",
                0.10,
                features(
                        "thin",
                        Map.of(
                                SourceFeature.THIN_CONTENT,
                                1.0,
                                SourceFeature.DEFINITIONS_PRESENT,
                                0.9,
                                SourceFeature.EXAMPLES_PRESENT,
                                0.9)),
                List.of());

        CandidateRoleEligibility result = evaluator.evaluate(input);

        assertThat(result.roles()).hasSize(SearchRole.values().length);
        assertThat(result.roles().values())
                .allSatisfy(role -> {
                    assertThat(role.eligible()).isFalse();
                    assertThat(role.rejectingFeatureNames())
                            .contains(
                                    DeterministicRoleEligibilityEvaluator
                                            .RELEVANCE_SIGNAL,
                                    SourceFeature.THIN_CONTENT.name());
                    assertThat(role.diagnostics())
                            .anyMatch(reason -> reason.contains(
                                    "is below minimum"))
                            .anyMatch(reason -> reason.contains(
                                    "meets rejection threshold"));
                });
    }

    @Test
    void reportsAmbiguousEvidenceWithoutForcingEligibility() {
        RoleEligibilityInput input = input(
                "ambiguous",
                0.30,
                features(
                        "ambiguous",
                        Map.of(
                                SourceFeature.DEFINITIONS_PRESENT,
                                0.20,
                                SourceFeature.EXAMPLES_PRESENT,
                                0.20)),
                List.of());

        RoleEligibilityResult result = evaluator.evaluate(input)
                .role(SearchRole.EXPLANATORY);

        assertThat(result.eligible()).isFalse();
        assertThat(result.confidence()).isBetween(0.20, 0.34);
        assertThat(result.supportingFeatureNames()).containsExactly(
                DeterministicRoleEligibilityEvaluator.RELEVANCE_SIGNAL,
                SourceFeature.DEFINITIONS_PRESENT.name(),
                SourceFeature.EXAMPLES_PRESENT.name());
        assertThat(result.diagnostics())
                .anyMatch(reason -> reason.startsWith(
                        "COMBINED_CONFIDENCE="));
    }

    @Test
    void honorsConfiguredRulesAndThresholds() {
        var configured = new EnumMap<SearchRole, RoleEligibilityRule>(
                RoleEligibilityRules.DEFAULTS.rules());
        configured.put(
                SearchRole.PRACTICAL,
                new RoleEligibilityRule(
                        0.10,
                        0.10,
                        1,
                        1.0,
                        Map.of(
                                SourceFeature.ORDERED_STEPS,
                                new FeatureThreshold(0.90, 2.0)),
                        Map.of()));
        var customEvaluator = new DeterministicRoleEligibilityEvaluator(
                new RoleEligibilityRules(configured));
        RoleEligibilityInput input = input(
                "configured",
                0.80,
                features(
                        "configured",
                        Map.of(SourceFeature.ORDERED_STEPS, 0.80)),
                List.of());

        RoleEligibilityResult result = customEvaluator.evaluate(input)
                .role(SearchRole.PRACTICAL);

        assertThat(result.eligible()).isFalse();
        assertThat(result.rejectingFeatureNames())
                .containsExactly(SourceFeature.ORDERED_STEPS.name());
    }

    @Test
    void retrievalPurposeDoesNotAffectEligibility() {
        SourceFeatureSet features = features(
                "same-document",
                Map.of(
                        SourceFeature.ORDERED_STEPS,
                        0.9,
                        SourceFeature.WORKED_EXAMPLES,
                        0.8));
        RoleEligibilityInput originalRetrieval = input(
                "same-document",
                0.70,
                features,
                List.of(new RetrievalMetadata(
                        "query",
                        "ORIGINAL")));
        RoleEligibilityInput roleNamedRetrieval = input(
                "same-document",
                0.70,
                features,
                List.of(new RetrievalMetadata(
                        "unrelated variant",
                        SearchRole.PRACTICAL.name())));

        CandidateRoleEligibility first = evaluator.evaluate(
                originalRetrieval);
        CandidateRoleEligibility second = evaluator.evaluate(
                roleNamedRetrieval);

        assertThat(first).isEqualTo(second);
    }

    private void assertEligible(
            SearchRole role,
            SourceFeature... evidence) {
        var values = new EnumMap<SourceFeature, Double>(
                SourceFeature.class);
        for (SourceFeature feature : evidence) {
            values.put(feature, 0.9);
        }
        String documentId = role.name().toLowerCase();
        RoleEligibilityResult result = evaluator.evaluate(input(
                documentId,
                0.75,
                features(documentId, values),
                List.of())).role(role);

        assertThat(result.eligible()).as(role.name()).isTrue();
        assertThat(result.confidence()).as(role.name())
                .isGreaterThanOrEqualTo(
                        RoleEligibilityRules.DEFAULTS
                                .rule(role)
                                .minimumConfidence());
        assertThat(result.supportingFeatureNames())
                .contains(
                        DeterministicRoleEligibilityEvaluator
                                .RELEVANCE_SIGNAL);
        assertThat(result.rejectingFeatureNames()).isEmpty();
    }

    private static SourceFeatureSet features(
            String documentId,
            Map<SourceFeature, Double> configuredValues) {
        var values = new EnumMap<SourceFeature, SourceFeatureValue>(
                SourceFeature.class);
        for (SourceFeature feature : SourceFeature.values()) {
            double value = configuredValues.getOrDefault(feature, 0.0);
            values.put(feature, new SourceFeatureValue(value, value));
        }
        return new SourceFeatureSet(documentId, values);
    }

    private static RoleEligibilityInput input(
            String documentId,
            double relevance,
            SourceFeatureSet features,
            List<RetrievalMetadata> retrievals) {
        String text = "Relevant candidate document content.";
        SourcePosition position = new SourcePosition(0, text.length());
        var document = new ExtractedDocument(
                documentId,
                List.of(ExtractedBlock.paragraph(text, position)));
        var passage = new Passage(
                documentId,
                List.of(),
                text,
                position,
                4);
        var searchResult = new AggregatedSearchResult(
                URI.create("https://example.test/" + documentId),
                "Candidate title",
                1,
                null,
                List.of("Candidate snippet"),
                retrievals);
        var candidate = new CandidateDocument(
                searchResult,
                document,
                List.of(passage));
        var scoredCandidate = new ScoredCandidateDocument(
                candidate,
                relevance,
                0.0,
                0.0,
                List.of(),
                emptyBreakdown());
        return new RoleEligibilityInput(scoredCandidate, features);
    }

    private static RelevanceScoreBreakdown emptyBreakdown() {
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
package com.branchlight.backend.search.optimization;

import java.net.URI;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.branchlight.backend.search.aggregation.AggregatedSearchResult;
import com.branchlight.backend.search.content.ExtractedBlock;
import com.branchlight.backend.search.content.ExtractedDocument;
import com.branchlight.backend.search.content.Passage;
import com.branchlight.backend.search.content.SourcePosition;
import com.branchlight.backend.search.domain.SearchRole;
import com.branchlight.backend.search.eligibility.RoleEligibilityResult;
import com.branchlight.backend.search.ranking.CandidateDocument;
import com.branchlight.backend.search.ranking.RelevanceScoreBreakdown;
import com.branchlight.backend.search.ranking.ScoredCandidateDocument;
import com.branchlight.backend.search.scoring.CandidateDeterministicRoleScores;
import com.branchlight.backend.search.scoring.DeterministicRoleScore;
import com.branchlight.backend.search.scoring.RoleScoreBreakdown;

import static org.assertj.core.api.Assertions.assertThat;

class ResultSetOptimizerTest {

    @Test
    void assignsOneMultiRoleLeaderToOnlyOneCategory() {
        ResultSetOptimizer optimizer = optimizer(0.20, 0.0);
        OptimizationCandidate leader = candidate(
                "leader",
                "leader.test",
                "Broad leader",
                Map.of(
                        SearchRole.AUTHORITATIVE,
                        0.95,
                        SearchRole.EXPLANATORY,
                        0.94));
        OptimizationCandidate authorityAlternative = candidate(
                "authority-alternative",
                "authority.test",
                "Authority alternative",
                Map.of(SearchRole.AUTHORITATIVE, 0.85));
        OptimizationCandidate explanationAlternative = candidate(
                "explanation-alternative",
                "explanation.test",
                "Explanation alternative",
                Map.of(SearchRole.EXPLANATORY, 0.70));

        OptimizedResultSet result = optimizer.optimize(List.of(
                leader,
                authorityAlternative,
                explanationAlternative));

        assertThat(result.selectedSources())
                .containsOnlyKeys(
                        SearchRole.AUTHORITATIVE,
                        SearchRole.EXPLANATORY);
        assertThat(result.selectedSources().values())
                .extracting(SelectedRoleSource::documentId)
                .containsExactlyInAnyOrder(
                        "leader",
                        "authority-alternative")
                .doesNotHaveDuplicates();
        assertThat(result.selectedSources()
                .get(SearchRole.EXPLANATORY)
                .documentId()).isEqualTo("leader");
        assertThat(result.closestRejectedAlternatives()
                .get(SearchRole.AUTHORITATIVE)
                .reason()).contains("assigned to EXPLANATORY");
    }

    @Test
    void findsAGloballyBetterCombinationThanGreedyAssignment() {
        ResultSetOptimizer optimizer = optimizer(0.05, 0.0);
        OptimizationCandidate first = candidate(
                "first",
                "first.test",
                "First candidate",
                Map.of(
                        SearchRole.AUTHORITATIVE,
                        0.90,
                        SearchRole.EXPLANATORY,
                        0.89));
        OptimizationCandidate second = candidate(
                "second",
                "second.test",
                "Second candidate",
                Map.of(
                        SearchRole.AUTHORITATIVE,
                        0.88,
                        SearchRole.EXPLANATORY,
                        0.10));

        OptimizedResultSet result = optimizer.optimize(List.of(
                first,
                second));

        assertThat(result.selectedSources()
                .get(SearchRole.AUTHORITATIVE)
                .documentId()).isEqualTo("second");
        assertThat(result.selectedSources()
                .get(SearchRole.EXPLANATORY)
                .documentId()).isEqualTo("first");
        assertThat(result.totalRoleScore()).isEqualTo(1.77);
        assertThat(result.totalSetScore()).isEqualTo(1.77);
        assertThat(result.selectedSources().values())
                .allSatisfy(selection -> assertThat(selection.reason())
                        .contains("globally optimal"));
    }

    @Test
    void leavesCategoryEmptyWhenNoCandidateMeetsThreshold() {
        ResultSetOptimizer optimizer = optimizer(0.50, 0.0);
        OptimizationCandidate belowThreshold = candidate(
                "below-threshold",
                "low.test",
                "Below threshold",
                Map.of(SearchRole.PRACTICAL, 0.49));

        OptimizedResultSet result = optimizer.optimize(
                List.of(belowThreshold));

        assertThat(result.selectedSources()).isEmpty();
        assertThat(result.omittedRoles())
                .containsExactly(SearchRole.values());
        assertThat(result.totalSetScore()).isZero();
        assertThat(result.closestRejectedAlternatives()
                .get(SearchRole.PRACTICAL)
                .documentId()).isEqualTo("below-threshold");
        assertThat(result.closestRejectedAlternatives()
                .get(SearchRole.PRACTICAL)
                .reason()).contains("below the PRACTICAL threshold");
    }

    @Test
    void repeatedDomainsReduceButDoNotProhibitSelection() {
        ResultSetOptimizer optimizer = optimizer(0.20, 0.10);
        OptimizationCandidate authority = candidate(
                "authority",
                "source.example.com",
                "Independent authority",
                Map.of(SearchRole.AUTHORITATIVE, 0.90));
        OptimizationCandidate sameDomainExplanation = candidate(
                "same-domain-explanation",
                "guide.example.com",
                "Detailed explanation",
                Map.of(SearchRole.EXPLANATORY, 0.90));
        OptimizationCandidate differentDomainExplanation = candidate(
                "different-domain-explanation",
                "different.test",
                "Alternative explanation",
                Map.of(SearchRole.EXPLANATORY, 0.75));

        OptimizedResultSet result = optimizer.optimize(List.of(
                authority,
                sameDomainExplanation,
                differentDomainExplanation));

        assertThat(result.selectedSources()
                .get(SearchRole.AUTHORITATIVE)
                .documentId()).isEqualTo("authority");
        assertThat(result.selectedSources()
                .get(SearchRole.EXPLANATORY)
                .documentId()).isEqualTo("same-domain-explanation");
        assertThat(result.totalRoleScore()).isEqualTo(1.80);
        assertThat(result.totalSetPenalty()).isEqualTo(0.10);
        assertThat(result.totalSetScore()).isEqualTo(1.70);
    }

    private static ResultSetOptimizer optimizer(
            double threshold,
            double repeatedDomainPenalty) {
        return new ResultSetOptimizer(
                new ResultSetOptimizerConfiguration(
                        ResultSetOptimizerConfiguration
                                .uniformThresholds(threshold),
                        repeatedDomainPenalty,
                        0.0,
                        0.0,
                        0.0,
                        0.80,
                        0.80));
    }

    private static OptimizationCandidate candidate(
            String documentId,
            String host,
            String title,
            Map<SearchRole, Double> configuredScores) {
        String text = "Candidate source text for optimization.";
        SourcePosition position = new SourcePosition(0, text.length());
        var document = new ExtractedDocument(
                documentId,
                List.of(ExtractedBlock.paragraph(text, position)));
        var passage = new Passage(
                documentId,
                List.of(),
                text,
                position,
                5);
        var searchResult = new AggregatedSearchResult(
                URI.create("https://" + host + "/" + documentId),
                title,
                1,
                null,
                List.of("Distinct snippet for " + documentId),
                List.of());
        var candidateDocument = new CandidateDocument(
                searchResult,
                document,
                List.of(passage));
        var scoredCandidate = new ScoredCandidateDocument(
                candidateDocument,
                0.8,
                0.0,
                0.0,
                List.of(),
                emptyRelevanceBreakdown());
        var roleScores = new EnumMap<SearchRole, DeterministicRoleScore>(
                SearchRole.class);
        for (SearchRole role : SearchRole.values()) {
            Double score = configuredScores.get(role);
            boolean eligible = score != null;
            var eligibility = new RoleEligibilityResult(
                    role,
                    eligible,
                    eligible ? 0.8 : 0.1,
                    eligible ? List.of("ROLE_FEATURE") : List.of(),
                    eligible ? List.of() : List.of("MISSING_ROLE_FEATURE"),
                    List.of("deterministic test eligibility"));
            roleScores.put(role, new DeterministicRoleScore(
                    role,
                    score,
                    eligible ? emptyRoleBreakdown() : null,
                    eligible
                            ? "Eligible deterministic score"
                            : "Ineligible deterministic score",
                    eligibility));
        }
        return new OptimizationCandidate(
                scoredCandidate,
                new CandidateDeterministicRoleScores(
                        documentId,
                        roleScores));
    }

    private static RoleScoreBreakdown emptyRoleBreakdown() {
        var component = new RoleScoreBreakdown.ScoreComponent(
                0.0,
                0.0,
                0.0,
                true);
        return new RoleScoreBreakdown(
                component,
                component,
                component,
                component,
                component,
                component,
                new RoleScoreBreakdown.RiskPenalty(0.0, 0.0, 0.0));
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
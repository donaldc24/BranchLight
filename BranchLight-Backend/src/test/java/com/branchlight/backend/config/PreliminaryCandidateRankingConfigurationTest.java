package com.branchlight.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.branchlight.backend.search.ranking.PreliminaryCandidateRanker;
import com.branchlight.backend.search.ranking.PreliminaryCandidateRankingWeights;

import static org.assertj.core.api.Assertions.assertThat;

class PreliminaryCandidateRankingConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withInitializer(
                            new ConfigDataApplicationContextInitializer())
                    .withUserConfiguration(
                            PreliminaryCandidateRankingConfiguration.class);

    @Test
    void configuresTheDefaultWeightsAndCandidateLimit() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            var ranker = context.getBean(
                    PreliminaryCandidateRanker.class);

            assertThat(ranker.maximumCandidates()).isEqualTo(25);
            assertThat(ranker.minimumDistinctTitleTerms())
                    .isEqualTo(2);
            assertThat(ranker.minimumDistinctSnippetTerms())
                    .isEqualTo(4);
            assertThat(ranker.weights()).isEqualTo(
                    new PreliminaryCandidateRankingWeights(
                            3.0,
                            2.0,
                            1.0,
                            0.25,
                            0.25,
                            0.5,
                            1.0));
        });
    }

    @Test
    void configuresEveryWeightAndTheLimitFromTheEnvironment() {
        contextRunner
                .withPropertyValues(
                        "BRANCHLIGHT_PRELIMINARY_RANKING"
                                + "_MAXIMUM_CANDIDATES=12",
                        "BRANCHLIGHT_PRELIMINARY_RANKING"
                                + "_MINIMUM_DISTINCT_TITLE_TERMS=3",
                        "BRANCHLIGHT_PRELIMINARY_RANKING"
                                + "_MINIMUM_DISTINCT_SNIPPET_TERMS=5",
                        "BRANCHLIGHT_PRELIMINARY_RANKING"
                                + "_TITLE_LEXICAL_OVERLAP_WEIGHT=1.1",
                        "BRANCHLIGHT_PRELIMINARY_RANKING"
                                + "_SNIPPET_LEXICAL_OVERLAP_WEIGHT=1.2",
                        "BRANCHLIGHT_PRELIMINARY_RANKING"
                                + "_PROVIDER_RANK_PRIOR_WEIGHT=1.3",
                        "BRANCHLIGHT_PRELIMINARY_RANKING"
                                + "_GENERATED_QUERY_DISCOVERY_WEIGHT=1.4",
                        "BRANCHLIGHT_PRELIMINARY_RANKING"
                                + "_RETRIEVAL_PURPOSE_DIVERSITY_WEIGHT=1.5",
                        "BRANCHLIGHT_PRELIMINARY_RANKING"
                                + "_TITLE_SPECIFICITY_WEIGHT=1.6",
                        "BRANCHLIGHT_PRELIMINARY_RANKING"
                                + "_LOW_QUALITY_PENALTY_WEIGHT=1.7")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var ranker = context.getBean(
                            PreliminaryCandidateRanker.class);

                    assertThat(ranker.maximumCandidates()).isEqualTo(12);
                    assertThat(ranker.minimumDistinctTitleTerms())
                            .isEqualTo(3);
                    assertThat(ranker.minimumDistinctSnippetTerms())
                            .isEqualTo(5);
                    assertThat(ranker.weights()).isEqualTo(
                            new PreliminaryCandidateRankingWeights(
                                    1.1,
                                    1.2,
                                    1.3,
                                    1.4,
                                    1.5,
                                    1.6,
                                    1.7));
                });
    }
}

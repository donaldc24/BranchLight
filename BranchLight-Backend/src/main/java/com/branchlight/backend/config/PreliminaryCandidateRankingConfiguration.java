package com.branchlight.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.branchlight.backend.search.ranking.PreliminaryCandidateRanker;
import com.branchlight.backend.search.ranking.PreliminaryCandidateRankingWeights;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(
        PreliminaryCandidateRankingProperties.class)
public class PreliminaryCandidateRankingConfiguration {

    @Bean
    PreliminaryCandidateRanker preliminaryCandidateRanker(
            PreliminaryCandidateRankingProperties properties) {
        var weights = new PreliminaryCandidateRankingWeights(
                properties.titleLexicalOverlapWeight(),
                properties.snippetLexicalOverlapWeight(),
                properties.providerRankPriorWeight(),
                properties.generatedQueryDiscoveryWeight(),
                properties.retrievalPurposeDiversityWeight(),
                properties.titleSpecificityWeight(),
                properties.lowQualityPenaltyWeight());
        return new PreliminaryCandidateRanker(
                weights,
                properties.maximumCandidates(),
                properties.minimumDistinctTitleTerms(),
                properties.minimumDistinctSnippetTerms());
    }
}

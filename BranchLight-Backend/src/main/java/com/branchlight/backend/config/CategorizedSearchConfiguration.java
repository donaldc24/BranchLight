package com.branchlight.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.branchlight.backend.search.content.ContentExtractor;
import com.branchlight.backend.search.content.PassageSplitter;
import com.branchlight.backend.search.eligibility.DeterministicRoleEligibilityEvaluator;
import com.branchlight.backend.search.features.SourceFeatureExtractor;
import com.branchlight.backend.search.fetch.PageFetcher;
import com.branchlight.backend.search.optimization.ResultSetOptimizer;
import com.branchlight.backend.search.ranking.LexicalRelevanceScorer;
import com.branchlight.backend.search.ranking.PreliminaryCandidateRanker;
import com.branchlight.backend.search.ranking.RelevanceScorer;
import com.branchlight.backend.search.scoring.DeterministicRoleScorer;
import com.branchlight.backend.search.service.CategorizedSearchService;
import com.branchlight.backend.search.service.SearchExecutionCoordinator;
import com.branchlight.backend.search.service.SearchOrchestrationService;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SearchExecutionProperties.class)
public class CategorizedSearchConfiguration {

    @Bean(destroyMethod = "close")
    SearchExecutionCoordinator searchExecutionCoordinator(
            SearchExecutionProperties properties) {
        return new SearchExecutionCoordinator(
                properties.providerQueryParallelism(),
                properties.pageFetchParallelism(),
                properties.pageProcessingParallelism());
    }

    @Bean
    PassageSplitter passageSplitter() {
        return new PassageSplitter();
    }

    @Bean
    RelevanceScorer relevanceScorer() {
        return new LexicalRelevanceScorer();
    }

    @Bean
    SourceFeatureExtractor sourceFeatureExtractor() {
        return new SourceFeatureExtractor();
    }

    @Bean
    DeterministicRoleEligibilityEvaluator
            deterministicRoleEligibilityEvaluator() {
        return new DeterministicRoleEligibilityEvaluator();
    }

    @Bean
    DeterministicRoleScorer deterministicRoleScorer() {
        return new DeterministicRoleScorer();
    }

    @Bean
    ResultSetOptimizer resultSetOptimizer() {
        return new ResultSetOptimizer();
    }

    @Bean
    CategorizedSearchService categorizedSearchService(
            SearchOrchestrationService searchOrchestrationService,
            PreliminaryCandidateRanker preliminaryCandidateRanker,
            PageFetcher pageFetcher,
            ContentExtractor contentExtractor,
            PassageSplitter passageSplitter,
            RelevanceScorer relevanceScorer,
            SourceFeatureExtractor sourceFeatureExtractor,
            DeterministicRoleEligibilityEvaluator eligibilityEvaluator,
            DeterministicRoleScorer roleScorer,
            ResultSetOptimizer resultSetOptimizer,
            SearchExecutionCoordinator executionCoordinator) {
        return new CategorizedSearchService(
                searchOrchestrationService,
                preliminaryCandidateRanker,
                pageFetcher,
                contentExtractor,
                passageSplitter,
                relevanceScorer,
                sourceFeatureExtractor,
                eligibilityEvaluator,
                roleScorer,
                resultSetOptimizer,
                executionCoordinator);
    }
}
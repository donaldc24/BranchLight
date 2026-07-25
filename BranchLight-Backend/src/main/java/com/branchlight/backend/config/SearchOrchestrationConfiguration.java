package com.branchlight.backend.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.branchlight.backend.search.provider.SearchProvider;
import com.branchlight.backend.search.query.QueryVariantGenerator;
import com.branchlight.backend.search.service.SearchExecutionCoordinator;
import com.branchlight.backend.search.service.SearchOrchestrationService;

@Configuration(proxyBeanMethods = false)
public class SearchOrchestrationConfiguration {

    private static final int RESULTS_PER_QUERY = 10;

    @Bean
    SearchOrchestrationService searchOrchestrationService(
            QueryVariantGenerator queryVariantGenerator,
            ObjectProvider<SearchProvider> searchProviders,
            ObjectProvider<SearchExecutionCoordinator>
                executionCoordinators) {
        return new SearchOrchestrationService(
                queryVariantGenerator,
                requiredSearchProvider(searchProviders),
            RESULTS_PER_QUERY,
            executionCoordinators.getIfAvailable(
                SearchExecutionCoordinator::sequential));
    }

    private SearchProvider requiredSearchProvider(
            ObjectProvider<SearchProvider> searchProviders) {
        return (query, resultLimit) -> {
            SearchProvider searchProvider =
                    searchProviders.getIfAvailable();
            if (searchProvider == null) {
                throw new IllegalStateException(
                        "No SearchProvider is configured");
            }
            return searchProvider.search(query, resultLimit);
        };
    }
}

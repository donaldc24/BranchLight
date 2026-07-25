package com.branchlight.backend.search.service;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchExecutionCoordinatorTest {

    @Test
    void runsUpToConfiguredParallelismAndPreservesInputOrder() {
        var activeTasks = new AtomicInteger();
        var maximumActiveTasks = new AtomicInteger();
        var firstWave = new CyclicBarrier(3);

        try (var coordinator = new SearchExecutionCoordinator(3, 1, 1)) {
            List<Integer> results = coordinator.mapProviderQueries(
                    List.of(3, 1, 2),
                    value -> {
                        int active = activeTasks.incrementAndGet();
                        maximumActiveTasks.accumulateAndGet(
                                active,
                                Math::max);
                        try {
                            firstWave.await(2, TimeUnit.SECONDS);
                            return value * 10;
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        } finally {
                            activeTasks.decrementAndGet();
                        }
                    });

            assertThat(maximumActiveTasks.get()).isEqualTo(3);
            assertThat(results).containsExactly(30, 10, 20);
        }
    }
}
package com.branchlight.backend.search.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public final class SearchExecutionCoordinator implements AutoCloseable {

    private static final int MAXIMUM_PARALLELISM = 32;

    private final ExecutorService providerExecutor;
    private final ExecutorService pageFetchExecutor;
    private final ExecutorService pageProcessingExecutor;

    public SearchExecutionCoordinator(
            int providerQueryParallelism,
            int pageFetchParallelism,
            int pageProcessingParallelism) {
        providerExecutor = executor(
                providerQueryParallelism,
                "branchlight-provider-");
        pageFetchExecutor = executor(
                pageFetchParallelism,
                "branchlight-fetch-");
        pageProcessingExecutor = executor(
                pageProcessingParallelism,
                "branchlight-process-");
    }

    public static SearchExecutionCoordinator sequential() {
        return new SearchExecutionCoordinator(1, 1, 1);
    }

    public <T, R> List<R> mapProviderQueries(
            List<T> inputs,
            Function<T, R> operation) {
        return mapOrdered(inputs, operation, providerExecutor);
    }

    public <T, R> List<R> mapPageFetches(
            List<T> inputs,
            Function<T, R> operation) {
        return mapOrdered(inputs, operation, pageFetchExecutor);
    }

    public <T, R> List<R> mapPageProcessing(
            List<T> inputs,
            Function<T, R> operation) {
        return mapOrdered(inputs, operation, pageProcessingExecutor);
    }

    private static <T, R> List<R> mapOrdered(
            List<T> inputs,
            Function<T, R> operation,
            ExecutorService executor) {
        Objects.requireNonNull(inputs, "inputs must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        if (executor == null) {
            return inputs.stream().map(operation).toList();
        }

        var futures = new ArrayList<Future<R>>(inputs.size());
        for (T input : inputs) {
            futures.add(executor.submit(() -> operation.apply(input)));
        }
        var results = new ArrayList<R>(inputs.size());
        for (Future<R> future : futures) {
            try {
                results.add(future.get());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Parallel search execution was interrupted",
                        exception);
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException(
                        "Parallel search execution failed",
                        cause);
            }
        }
        return List.copyOf(results);
    }

    private static ExecutorService executor(
            int parallelism,
            String threadPrefix) {
        if (parallelism < 1 || parallelism > MAXIMUM_PARALLELISM) {
            throw new IllegalArgumentException(
                    "parallelism must be between 1 and 32");
        }
        if (parallelism == 1) {
            return null;
        }
        var sequence = new AtomicInteger();
        return Executors.newFixedThreadPool(parallelism, runnable -> {
            Thread thread = new Thread(
                    runnable,
                    threadPrefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void close() {
        shutdown(providerExecutor);
        shutdown(pageFetchExecutor);
        shutdown(pageProcessingExecutor);
    }

    private static void shutdown(ExecutorService executor) {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
package com.branchlight.backend.search.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.branchlight.backend.search.aggregation.AggregatedSearchResult;
import com.branchlight.backend.search.aggregation.SearchResultAggregator;
import com.branchlight.backend.search.content.ContentExtractionSuccess;
import com.branchlight.backend.search.content.ContentExtractor;
import com.branchlight.backend.search.content.ExtractedBlock;
import com.branchlight.backend.search.content.ExtractedDocument;
import com.branchlight.backend.search.content.PassageSplitter;
import com.branchlight.backend.search.content.SourcePosition;
import com.branchlight.backend.search.domain.SearchResult;
import com.branchlight.backend.search.eligibility.DeterministicRoleEligibilityEvaluator;
import com.branchlight.backend.search.eligibility.RoleEligibilityInput;
import com.branchlight.backend.search.features.SourceFeatureExtractor;
import com.branchlight.backend.search.features.SourceFeatureInput;
import com.branchlight.backend.search.fetch.PageFetchResult;
import com.branchlight.backend.search.fetch.PageFetchFailure;
import com.branchlight.backend.search.fetch.PageFetchFailureType;
import com.branchlight.backend.search.fetch.PageFetchSuccess;
import com.branchlight.backend.search.fetch.PageFetcher;
import com.branchlight.backend.search.optimization.OptimizationCandidate;
import com.branchlight.backend.search.optimization.ResultSetOptimizer;
import com.branchlight.backend.search.ranking.CandidateDocument;
import com.branchlight.backend.search.ranking.PreliminaryCandidateRanker;
import com.branchlight.backend.search.ranking.RelevanceQuery;
import com.branchlight.backend.search.ranking.RelevanceScorer;
import com.branchlight.backend.search.ranking.ScoredCandidateDocument;
import com.branchlight.backend.search.scoring.DeterministicRoleScorer;
import com.branchlight.backend.search.scoring.RoleScoringInput;

public final class CategorizedSearchService implements SearchService {

    private static final int MAXIMUM_SNIPPET_LENGTH = 320;

        private static final Logger LOGGER = LoggerFactory.getLogger(
                        CategorizedSearchService.class);

    private final SearchOrchestrationService searchOrchestrationService;
    private final PreliminaryCandidateRanker preliminaryCandidateRanker;
    private final PageFetcher pageFetcher;
    private final ContentExtractor contentExtractor;
    private final PassageSplitter passageSplitter;
    private final RelevanceScorer relevanceScorer;
    private final SourceFeatureExtractor sourceFeatureExtractor;
    private final DeterministicRoleEligibilityEvaluator eligibilityEvaluator;
    private final DeterministicRoleScorer roleScorer;
    private final ResultSetOptimizer resultSetOptimizer;
        private final SearchExecutionCoordinator executionCoordinator;

    public CategorizedSearchService(
            SearchOrchestrationService searchOrchestrationService,
            PreliminaryCandidateRanker preliminaryCandidateRanker,
            PageFetcher pageFetcher,
            ContentExtractor contentExtractor,
            PassageSplitter passageSplitter,
            RelevanceScorer relevanceScorer,
            SourceFeatureExtractor sourceFeatureExtractor,
            DeterministicRoleEligibilityEvaluator eligibilityEvaluator,
            DeterministicRoleScorer roleScorer,
            ResultSetOptimizer resultSetOptimizer) {
        this(
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
                SearchExecutionCoordinator.sequential());
    }

    public CategorizedSearchService(
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
        this.searchOrchestrationService = Objects.requireNonNull(
                searchOrchestrationService,
                "searchOrchestrationService must not be null");
        this.preliminaryCandidateRanker = Objects.requireNonNull(
                preliminaryCandidateRanker,
                "preliminaryCandidateRanker must not be null");
        this.pageFetcher = Objects.requireNonNull(
                pageFetcher,
                "pageFetcher must not be null");
        this.contentExtractor = Objects.requireNonNull(
                contentExtractor,
                "contentExtractor must not be null");
        this.passageSplitter = Objects.requireNonNull(
                passageSplitter,
                "passageSplitter must not be null");
        this.relevanceScorer = Objects.requireNonNull(
                relevanceScorer,
                "relevanceScorer must not be null");
        this.sourceFeatureExtractor = Objects.requireNonNull(
                sourceFeatureExtractor,
                "sourceFeatureExtractor must not be null");
        this.eligibilityEvaluator = Objects.requireNonNull(
                eligibilityEvaluator,
                "eligibilityEvaluator must not be null");
        this.roleScorer = Objects.requireNonNull(
                roleScorer,
                "roleScorer must not be null");
        this.resultSetOptimizer = Objects.requireNonNull(
                resultSetOptimizer,
                "resultSetOptimizer must not be null");
        this.executionCoordinator = Objects.requireNonNull(
                executionCoordinator,
                "executionCoordinator must not be null");
    }

    @Override
    public List<SearchResult> search(String originalQuery) {
                long searchStarted = System.nanoTime();
        Objects.requireNonNull(
                originalQuery,
                "originalQuery must not be null");
        if (originalQuery.isBlank()) {
            throw new IllegalArgumentException(
                    "originalQuery must not be blank");
        }

        long stageStarted = System.nanoTime();
        List<AggregatedSearchResult> aggregated =
                searchOrchestrationService.search(originalQuery);
        logStage("retrieval", stageStarted, aggregated.size());

        stageStarted = System.nanoTime();
        var ranked = preliminaryCandidateRanker.rank(
                originalQuery,
                aggregated);
        logStage("preliminary-ranking", stageStarted, ranked.size());

        List<String> pageUrls = ranked.stream()
                .map(result -> result.candidate().url().toString())
                .toList();
        stageStarted = System.nanoTime();
        List<PageFetchResult> fetchedPages =
                executionCoordinator.mapPageFetches(
                        pageUrls,
                        this::fetchPage);
        logStage("page-fetch", stageStarted, fetchedPages.size());

        var fetchedCandidates = new ArrayList<FetchedCandidate>(
                ranked.size());
        for (int index = 0; index < ranked.size(); index++) {
            fetchedCandidates.add(new FetchedCandidate(
                    ranked.get(index).candidate(),
                    fetchedPages.get(index)));
        }
        stageStarted = System.nanoTime();
        List<Optional<PreparedDocument>> preparedResults =
                executionCoordinator.mapPageProcessing(
                        fetchedCandidates,
                        this::preparePage);

        var preparedByDocumentId =
                new LinkedHashMap<String, PreparedDocument>();
        var candidateDocuments = new ArrayList<CandidateDocument>();
        for (Optional<PreparedDocument> result : preparedResults) {
            result.ifPresent(prepared -> {
                preparedByDocumentId.put(
                        prepared.candidate().document().documentId(),
                        prepared);
                candidateDocuments.add(prepared.candidate());
            });
        }
        logStage("page-prepare", stageStarted, candidateDocuments.size());

        if (candidateDocuments.isEmpty()) {
            logCompleted(searchStarted, 0);
            return List.of();
        }

        stageStarted = System.nanoTime();
        List<String> supportingVariants = supportingVariants(aggregated);
        List<ScoredCandidateDocument> relevantDocuments =
                relevanceScorer.score(
                        new RelevanceQuery(
                                originalQuery,
                                supportingVariants),
                        candidateDocuments);
        logStage("relevance", stageStarted, relevantDocuments.size());

        stageStarted = System.nanoTime();
        List<Optional<OptimizationCandidate>> evaluatedResults =
                executionCoordinator.mapPageProcessing(
                        relevantDocuments,
                        relevantDocument -> evaluatePage(
                                relevantDocument,
                                preparedByDocumentId));
        var optimizationCandidates = new ArrayList<OptimizationCandidate>();
        evaluatedResults.forEach(result ->
                result.ifPresent(optimizationCandidates::add));
        logStage(
                "deterministic-evaluation",
                stageStarted,
                optimizationCandidates.size());

        stageStarted = System.nanoTime();
        var optimized = resultSetOptimizer.optimize(
                optimizationCandidates);
        logStage(
                "result-set-optimization",
                stageStarted,
                optimized.selectedSources().size());
        Map<String, OptimizationCandidate> candidatesById =
                new LinkedHashMap<>();
        optimizationCandidates.forEach(candidate ->
                candidatesById.put(candidate.documentId(), candidate));
        List<SearchResult> results = optimized.selectedSources()
                .entrySet()
                .stream()
                .map(entry -> toSearchResult(
                        entry.getKey(),
                        entry.getValue(),
                        candidatesById.get(
                                entry.getValue().documentId())))
                .toList();
        logCompleted(searchStarted, results.size());
        return results;
    }

    private PageFetchResult fetchPage(String url) {
        long started = System.nanoTime();
        try {
            PageFetchResult result = Objects.requireNonNull(
                    pageFetcher.fetch(url),
                    "fetch result must not be null");
            LOGGER.info(
                    "search.page.fetch url={} outcome={} durationMs={}",
                    url,
                    result instanceof PageFetchSuccess
                            ? "success"
                            : "failure",
                    elapsedMillis(started));
            return result;
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "search.page.fetch url={} outcome=exception durationMs={}",
                    url,
                    elapsedMillis(started),
                    exception);
            return new PageFetchFailure(
                    url == null ? "" : url,
                    null,
                    PageFetchFailureType.UNEXPECTED_ERROR,
                    null,
                    "Unexpected page fetch failure",
                    List.of());
        }
    }

    private Optional<PreparedDocument> preparePage(
            FetchedCandidate fetchedCandidate) {
        long started = System.nanoTime();
        String url = fetchedCandidate.searchResult().url().toString();
        if (!(fetchedCandidate.fetchedPage()
                instanceof PageFetchSuccess success)) {
            LOGGER.info(
                    "search.page.prepare url={} outcome=skipped durationMs={}",
                    url,
                    elapsedMillis(started));
            return Optional.empty();
        }
        try {
            var extractionResult = contentExtractor.extract(success);
            if (!(extractionResult
                    instanceof ContentExtractionSuccess extraction)) {
                LOGGER.info(
                        "search.page.prepare url={} outcome=extraction-failure durationMs={}",
                        url,
                        elapsedMillis(started));
                return Optional.empty();
            }
            ExtractedDocument document = toExtractedDocument(
                    fetchedCandidate.searchResult(),
                    extraction);
            var passages = passageSplitter.split(document);
            if (passages.isEmpty()) {
                LOGGER.info(
                        "search.page.prepare url={} outcome=no-passages durationMs={}",
                        url,
                        elapsedMillis(started));
                return Optional.empty();
            }
            var candidateDocument = new CandidateDocument(
                    fetchedCandidate.searchResult(),
                    document,
                    passages);
            LOGGER.info(
                    "search.page.prepare url={} outcome=success passageCount={} durationMs={}",
                    url,
                    passages.size(),
                    elapsedMillis(started));
            return Optional.of(new PreparedDocument(
                    candidateDocument,
                    extraction,
                    success.content()));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "search.page.prepare url={} outcome=exception durationMs={}",
                    url,
                    elapsedMillis(started),
                    exception);
            return Optional.empty();
        }
    }

    private Optional<OptimizationCandidate> evaluatePage(
            ScoredCandidateDocument relevantDocument,
            Map<String, PreparedDocument> preparedByDocumentId) {
        long started = System.nanoTime();
        String documentId = relevantDocument.candidate()
                .document()
                .documentId();
        PreparedDocument prepared = preparedByDocumentId.get(documentId);
        if (prepared == null) {
            return Optional.empty();
        }
        try {
            var features = sourceFeatureExtractor.extract(
                    new SourceFeatureInput(
                            prepared.candidate(),
                            prepared.extraction(),
                            prepared.sourceContent()));
            var eligibility = eligibilityEvaluator.evaluate(
                    new RoleEligibilityInput(
                            relevantDocument,
                            features));
            var roleScores = roleScorer.score(new RoleScoringInput(
                    relevantDocument,
                    features,
                    eligibility));
            LOGGER.info(
                    "search.page.evaluate url={} outcome=success durationMs={}",
                    documentId,
                    elapsedMillis(started));
            return Optional.of(new OptimizationCandidate(
                    relevantDocument,
                    roleScores));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "search.page.evaluate url={} outcome=exception durationMs={}",
                    documentId,
                    elapsedMillis(started),
                    exception);
            return Optional.empty();
        }
    }

    private static void logStage(
            String stage,
            long started,
            int resultCount) {
        LOGGER.info(
                "search.stage completed stage={} resultCount={} durationMs={}",
                stage,
                resultCount,
                elapsedMillis(started));
    }

    private static void logCompleted(long started, int resultCount) {
        LOGGER.info(
                "search.completed resultCount={} durationMs={}",
                resultCount,
                elapsedMillis(started));
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    private static ExtractedDocument toExtractedDocument(
            AggregatedSearchResult searchResult,
            ContentExtractionSuccess extraction) {
        var blocks = new ArrayList<ExtractedBlock>();
        var headings = extraction.headings().iterator();
        var nextHeading = headings.hasNext() ? headings.next() : null;
        int cursor = 0;
        for (String line : extraction.mainText().split("\\R+")) {
            String text = line.strip();
            if (text.isEmpty()) {
                continue;
            }
            int start = extraction.mainText().indexOf(text, cursor);
            if (start < 0) {
                start = cursor;
            }
            int end = start + text.length();
            SourcePosition position = new SourcePosition(start, end);
            if (nextHeading != null && text.equals(nextHeading.text())) {
                blocks.add(ExtractedBlock.heading(
                        nextHeading.level(),
                        text,
                        position));
                nextHeading = headings.hasNext() ? headings.next() : null;
            } else if (looksLikeListItem(text)) {
                blocks.add(ExtractedBlock.list(text, position));
            } else {
                blocks.add(ExtractedBlock.paragraph(text, position));
            }
            cursor = end;
        }
        if (blocks.stream().noneMatch(block -> block.type()
                != ExtractedBlock.Type.HEADING)) {
            blocks.add(ExtractedBlock.paragraph(
                    extraction.mainText(),
                    new SourcePosition(
                            0,
                            extraction.mainText().length())));
        }
        return new ExtractedDocument(
                searchResult.url().toString(),
                blocks);
    }

    private static boolean looksLikeListItem(String text) {
        return text.matches("^(?:[-*+] |\\d+[.)] ).+");
    }

    private static List<String> supportingVariants(
            List<AggregatedSearchResult> results) {
        var variants = new LinkedHashSet<String>();
        results.stream()
                .flatMap(result -> result.retrievals().stream())
                .filter(retrieval -> !SearchResultAggregator
                        .ORIGINAL_QUERY_PURPOSE
                        .equals(retrieval.purpose()))
                .map(retrieval -> retrieval.query())
                .forEach(variants::add);
        return List.copyOf(variants);
    }

    private static SearchResult toSearchResult(
            com.branchlight.backend.search.domain.SearchRole role,
            com.branchlight.backend.search.optimization.SelectedRoleSource
                    selection,
            OptimizationCandidate candidate) {
        AggregatedSearchResult searchResult = candidate.candidate()
                .candidate()
                .searchResult();
        String snippet = searchResult.snippets().stream()
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElseGet(() -> candidate.candidate()
                        .topRelevantPassages()
                        .stream()
                        .findFirst()
                        .map(passage -> passage.passage().text())
                        .orElse(""));
        String reason = selection.reason()
                + " "
                + candidate.roleScores().role(role).reason();
        return new SearchResult(
                role,
                searchResult.title(),
                selection.sourceUrl(),
                domain(selection.sourceUrl()),
                truncate(snippet),
                reason,
                selection.roleScore());
    }

    private static String domain(URI uri) {
        return uri.getHost() == null ? uri.getAuthority() : uri.getHost();
    }

    private static String truncate(String value) {
        if (value.length() <= MAXIMUM_SNIPPET_LENGTH) {
            return value;
        }
        return value.substring(0, MAXIMUM_SNIPPET_LENGTH - 3).stripTrailing()
                + "...";
    }

    private record PreparedDocument(
            CandidateDocument candidate,
            ContentExtractionSuccess extraction,
            String sourceContent) {
    }

    private record FetchedCandidate(
            AggregatedSearchResult searchResult,
            PageFetchResult fetchedPage) {
    }
}
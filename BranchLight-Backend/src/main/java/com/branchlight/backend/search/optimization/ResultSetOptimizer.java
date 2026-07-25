package com.branchlight.backend.search.optimization;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import com.branchlight.backend.search.domain.SearchRole;
import com.branchlight.backend.search.scoring.DeterministicRoleScore;

public final class ResultSetOptimizer {

    public static final int MAXIMUM_CANDIDATES = 15;

    private static final double COMPARISON_EPSILON = 1.0e-12;
    private static final Pattern TERM_PATTERN =
            Pattern.compile("[\\p{L}\\p{N}]+");

    private final ResultSetOptimizerConfiguration configuration;

    public ResultSetOptimizer() {
        this(ResultSetOptimizerConfiguration.DEFAULTS);
    }

    public ResultSetOptimizer(
            ResultSetOptimizerConfiguration configuration) {
        this.configuration = Objects.requireNonNull(
                configuration,
                "configuration must not be null");
    }

    public OptimizedResultSet optimize(
            List<OptimizationCandidate> inputCandidates) {
        Objects.requireNonNull(
                inputCandidates,
                "inputCandidates must not be null");
        if (inputCandidates.size() > MAXIMUM_CANDIDATES) {
            throw new IllegalArgumentException(
                    "inputCandidates must contain at most 15 candidates");
        }

        var documentIds = new HashSet<String>();
        var candidates = inputCandidates.stream()
                .map(candidate -> Objects.requireNonNull(
                        candidate,
                        "candidate must not be null"))
                .sorted(Comparator.comparing(
                        OptimizationCandidate::documentId))
                .toList();
        for (OptimizationCandidate candidate : candidates) {
            if (!documentIds.add(candidate.documentId())) {
                throw new IllegalArgumentException(
                        "candidate document IDs must be unique");
            }
        }

        var best = new BestAssignment();
        search(
                0,
                candidates,
                new EnumMap<>(SearchRole.class),
                new HashSet<>(),
                0.0,
                0.0,
                best);
        return result(candidates, best);
    }

    public ResultSetOptimizerConfiguration configuration() {
        return configuration;
    }

    private void search(
            int roleIndex,
            List<OptimizationCandidate> candidates,
            EnumMap<SearchRole, OptimizationCandidate> assignment,
            Set<String> usedDocumentIds,
            double roleScore,
            double setPenalty,
            BestAssignment best) {
        if (roleIndex == SearchRole.values().length) {
            best.consider(assignment, roleScore, setPenalty);
            return;
        }

        SearchRole role = SearchRole.values()[roleIndex];
        search(
                roleIndex + 1,
                candidates,
                assignment,
                usedDocumentIds,
                roleScore,
                setPenalty,
                best);

        for (OptimizationCandidate candidate : candidates) {
            if (usedDocumentIds.contains(candidate.documentId())
                    || !acceptable(candidate, role)) {
                continue;
            }
            double incrementalPenalty = assignment.values().stream()
                    .mapToDouble(selected -> pairPenalty(
                            candidate,
                            selected))
                    .sum();
            assignment.put(role, candidate);
            usedDocumentIds.add(candidate.documentId());
            search(
                    roleIndex + 1,
                    candidates,
                    assignment,
                    usedDocumentIds,
                    roleScore + roleScore(candidate, role),
                    setPenalty + incrementalPenalty,
                    best);
            usedDocumentIds.remove(candidate.documentId());
            assignment.remove(role);
        }
    }

    private boolean acceptable(
            OptimizationCandidate candidate,
            SearchRole role) {
        DeterministicRoleScore score = candidate.roleScores().role(role);
        return score.eligibilityResult().eligible()
                && score.finalScore() != null
                && score.finalScore() >= configuration.minimumScore(role);
    }

    private double pairPenalty(
            OptimizationCandidate first,
            OptimizationCandidate second) {
        double penalty = 0.0;
        if (configuration.repeatedRootDomainPenalty() > 0.0
                && rootDomain(first).equals(rootDomain(second))) {
            penalty += configuration.repeatedRootDomainPenalty();
        }
        if (configuration.similarTitlePenalty() > 0.0
                && similarity(
                        title(first),
                        title(second))
                        >= configuration.titleSimilarityThreshold()) {
            penalty += configuration.similarTitlePenalty();
        }
        if (configuration.similarSnippetPenalty() > 0.0
                && similarity(
                        snippets(first),
                        snippets(second))
                        >= configuration.snippetSimilarityThreshold()) {
            penalty += configuration.similarSnippetPenalty();
        }
        if (configuration.identicalRetrievalPathPenalty() > 0.0
                && identicalRetrievalPath(first, second)) {
            penalty += configuration.identicalRetrievalPathPenalty();
        }
        return penalty;
    }

    private OptimizedResultSet result(
            List<OptimizationCandidate> candidates,
            BestAssignment best) {
        var selected = new EnumMap<SearchRole, SelectedRoleSource>(
                SearchRole.class);
        var omitted = new ArrayList<SearchRole>();
        var selectedRoleByDocument = new java.util.HashMap<
                String,
                SearchRole>();
        best.assignment.forEach((role, candidate) ->
                selectedRoleByDocument.put(candidate.documentId(), role));

        for (SearchRole role : SearchRole.values()) {
            OptimizationCandidate candidate = best.assignment.get(role);
            if (candidate == null) {
                omitted.add(role);
                continue;
            }
            double score = roleScore(candidate, role);
            String reason = String.format(
                    Locale.ROOT,
                    "Selected for %s with role score %.3f in the globally optimal one-source-per-role assignment; set objective %.3f after %.3f penalties.",
                    role.name(),
                    score,
                    best.objective,
                    best.setPenalty);
            selected.put(role, new SelectedRoleSource(
                    role,
                    candidate.documentId(),
                    sourceUrl(candidate),
                    score,
                    reason));
        }

        var alternatives = new EnumMap<SearchRole, RejectedAlternative>(
                SearchRole.class);
        for (SearchRole role : SearchRole.values()) {
            closestAlternative(
                    role,
                    candidates,
                    best.assignment.get(role),
                    selectedRoleByDocument).ifPresent(alternative ->
                            alternatives.put(role, alternative));
        }
        return new OptimizedResultSet(
                selected,
                omitted,
                best.objective,
                best.roleScore,
                best.setPenalty,
                alternatives);
    }

    private java.util.Optional<RejectedAlternative> closestAlternative(
            SearchRole role,
            List<OptimizationCandidate> candidates,
            OptimizationCandidate selected,
            Map<String, SearchRole> selectedRoleByDocument) {
        return candidates.stream()
                .filter(candidate -> selected == null
                        || !candidate.documentId().equals(
                                selected.documentId()))
                .sorted(Comparator
                        .comparingDouble((OptimizationCandidate candidate) ->
                                alternativeRank(candidate, role))
                        .reversed()
                        .thenComparing(OptimizationCandidate::documentId))
                .findFirst()
                .map(candidate -> new RejectedAlternative(
                        candidate.documentId(),
                        sourceUrl(candidate),
                        candidate.roleScores().role(role).finalScore(),
                        candidate.roleScores()
                                .role(role)
                                .eligibilityResult()
                                .confidence(),
                        alternativeReason(
                                candidate,
                                role,
                                selectedRoleByDocument)));
    }

    private String alternativeReason(
            OptimizationCandidate candidate,
            SearchRole role,
            Map<String, SearchRole> selectedRoleByDocument) {
        DeterministicRoleScore score = candidate.roleScores().role(role);
        if (!score.eligibilityResult().eligible()) {
            String reasons = score.eligibilityResult()
                    .rejectingFeatureNames()
                    .isEmpty()
                            ? "eligibility rules"
                            : String.join(
                                    ",",
                                    score.eligibilityResult()
                                            .rejectingFeatureNames());
            return "Ineligible for " + role.name() + ": " + reasons + ".";
        }
        if (score.finalScore() < configuration.minimumScore(role)) {
            return String.format(
                    Locale.ROOT,
                    "Score %.3f is below the %s threshold %.3f.",
                    score.finalScore(),
                    role.name(),
                    configuration.minimumScore(role));
        }
        SearchRole assignedRole = selectedRoleByDocument.get(
                candidate.documentId());
        if (assignedRole != null) {
            return "Eligible for " + role.name()
                    + " but assigned to " + assignedRole.name()
                    + " by the one-source constraint.";
        }
        return "Eligible alternative not chosen by the global set objective.";
    }

    private static double alternativeRank(
            OptimizationCandidate candidate,
            SearchRole role) {
        DeterministicRoleScore score = candidate.roleScores().role(role);
        return score.finalScore() != null
                ? 1.0 + score.finalScore()
                : score.eligibilityResult().confidence();
    }

    private static double roleScore(
            OptimizationCandidate candidate,
            SearchRole role) {
        return candidate.roleScores().role(role).finalScore();
    }

    private static URI sourceUrl(OptimizationCandidate candidate) {
        return candidate.candidate()
                .candidate()
                .searchResult()
                .url();
    }

    private static String title(OptimizationCandidate candidate) {
        return candidate.candidate()
                .candidate()
                .searchResult()
                .title();
    }

    private static String snippets(OptimizationCandidate candidate) {
        return String.join(
                " ",
                candidate.candidate()
                        .candidate()
                        .searchResult()
                        .snippets());
    }

    private static String rootDomain(OptimizationCandidate candidate) {
        String host = sourceUrl(candidate).getHost();
        if (host == null) {
            return sourceUrl(candidate).toString().toLowerCase(Locale.ROOT);
        }
        host = host.toLowerCase(Locale.ROOT).replaceFirst("\\.$", "");
        if (host.matches("[0-9.]+") || host.contains(":")) {
            return host;
        }
        String[] labels = host.split("\\.");
        if (labels.length <= 2) {
            return host;
        }
        return labels[labels.length - 2] + "." + labels[labels.length - 1];
    }

    private static double similarity(String first, String second) {
        Set<String> firstTerms = terms(first);
        Set<String> secondTerms = terms(second);
        if (firstTerms.isEmpty() || secondTerms.isEmpty()) {
            return 0.0;
        }
        var intersection = new HashSet<>(firstTerms);
        intersection.retainAll(secondTerms);
        var union = new HashSet<>(firstTerms);
        union.addAll(secondTerms);
        return (double) intersection.size() / union.size();
    }

    private static Set<String> terms(String text) {
        var matcher = TERM_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        var terms = new LinkedHashSet<String>();
        while (matcher.find()) {
            terms.add(matcher.group());
        }
        return terms;
    }

    private static boolean identicalRetrievalPath(
            OptimizationCandidate first,
            OptimizationCandidate second) {
        Set<String> firstPath = retrievalPath(first);
        return !firstPath.isEmpty()
                && firstPath.equals(retrievalPath(second));
    }

    private static Set<String> retrievalPath(
            OptimizationCandidate candidate) {
        var queries = new LinkedHashSet<String>();
        candidate.candidate()
                .candidate()
                .searchResult()
                .retrievals()
                .forEach(retrieval -> queries.add(
                        retrieval.query()
                                .strip()
                                .toLowerCase(Locale.ROOT)));
        return queries;
    }

    private static final class BestAssignment {

        private EnumMap<SearchRole, OptimizationCandidate> assignment =
                new EnumMap<>(SearchRole.class);
        private double roleScore;
        private double setPenalty;
        private double objective;
        private String signature = "";

        private void consider(
                EnumMap<SearchRole, OptimizationCandidate> candidateAssignment,
                double candidateRoleScore,
                double candidatePenalty) {
                        double candidateObjective = candidateRoleScore - candidatePenalty;
            String candidateSignature = signature(candidateAssignment);
            boolean better = candidateObjective
                    > objective + COMPARISON_EPSILON
                    || (Math.abs(candidateObjective - objective)
                            <= COMPARISON_EPSILON
                            && candidateRoleScore
                                    > roleScore + COMPARISON_EPSILON)
                    || (Math.abs(candidateObjective - objective)
                            <= COMPARISON_EPSILON
                            && Math.abs(candidateRoleScore - roleScore)
                                    <= COMPARISON_EPSILON
                            && candidateAssignment.size()
                                    > assignment.size())
                    || (Math.abs(candidateObjective - objective)
                            <= COMPARISON_EPSILON
                            && Math.abs(candidateRoleScore - roleScore)
                                    <= COMPARISON_EPSILON
                            && candidateAssignment.size()
                                    == assignment.size()
                            && (signature.isEmpty()
                                    || candidateSignature.compareTo(signature)
                                            < 0));
            if (better) {
                assignment = new EnumMap<>(candidateAssignment);
                roleScore = candidateRoleScore;
                setPenalty = candidatePenalty;
                objective = candidateObjective;
                signature = candidateSignature;
            }
        }

        private static String signature(
                Map<SearchRole, OptimizationCandidate> assignment) {
            var parts = new ArrayList<String>();
            for (SearchRole role : SearchRole.values()) {
                OptimizationCandidate candidate = assignment.get(role);
                parts.add(role.name() + "=" + (candidate == null
                        ? "~"
                        : candidate.documentId()));
            }
            return String.join("|", parts);
        }
    }
}
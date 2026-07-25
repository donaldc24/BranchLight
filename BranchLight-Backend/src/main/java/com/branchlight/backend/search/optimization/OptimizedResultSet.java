package com.branchlight.backend.search.optimization;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.branchlight.backend.search.domain.SearchRole;

public record OptimizedResultSet(
        Map<SearchRole, SelectedRoleSource> selectedSources,
        List<SearchRole> omittedRoles,
        double totalSetScore,
        double totalRoleScore,
        double totalSetPenalty,
        Map<SearchRole, RejectedAlternative> closestRejectedAlternatives) {

    public OptimizedResultSet {
        selectedSources = immutableMap(
                selectedSources,
                "selectedSources");
        omittedRoles = List.copyOf(Objects.requireNonNull(
                omittedRoles,
                "omittedRoles must not be null"));
        closestRejectedAlternatives = immutableMap(
                closestRejectedAlternatives,
                "closestRejectedAlternatives");
        requireNonNegative(totalSetScore, "totalSetScore");
        requireNonNegative(totalRoleScore, "totalRoleScore");
        requireNonNegative(totalSetPenalty, "totalSetPenalty");
        if (selectedSources.size() + omittedRoles.size()
                != SearchRole.values().length) {
            throw new IllegalArgumentException(
                    "every search role must be selected or omitted");
        }
    }

    private static <T> Map<SearchRole, T> immutableMap(
            Map<SearchRole, T> values,
            String name) {
        Objects.requireNonNull(values, name + " must not be null");
        var copy = new EnumMap<SearchRole, T>(SearchRole.class);
        copy.putAll(values);
        if (copy.containsValue(null)) {
            throw new IllegalArgumentException(
                    name + " must not contain null values");
        }
        return Collections.unmodifiableMap(copy);
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(
                    name + " must be finite and non-negative");
        }
    }
}
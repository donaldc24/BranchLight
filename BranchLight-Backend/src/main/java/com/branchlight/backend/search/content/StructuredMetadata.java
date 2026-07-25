package com.branchlight.backend.search.content;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record StructuredMetadata(
        String format,
        List<String> types,
        Map<String, List<String>> properties,
        String rawContent) {

    public StructuredMetadata {
        Objects.requireNonNull(format, "format must not be null");
        Objects.requireNonNull(rawContent, "rawContent must not be null");
        if (format.isBlank()) {
            throw new IllegalArgumentException(
                    "format must not be blank");
        }
        if (rawContent.isBlank()) {
            throw new IllegalArgumentException(
                    "rawContent must not be blank");
        }

        types = List.copyOf(Objects.requireNonNull(
                types,
                "types must not be null"));
        Objects.requireNonNull(
                properties,
                "properties must not be null");
        var copiedProperties =
                new LinkedHashMap<String, List<String>>();
        properties.forEach((name, values) -> {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException(
                        "property names must not be blank");
            }
            copiedProperties.put(
                    name,
                    List.copyOf(Objects.requireNonNull(
                            values,
                            "property values must not be null")));
        });
        properties = Collections.unmodifiableMap(copiedProperties);
    }

    @Override
    public String toString() {
        return "StructuredMetadata[format="
                + format
                + ", types="
                + types
                + ", propertyCount="
                + properties.size()
                + ", rawContent=<redacted>]";
    }
}

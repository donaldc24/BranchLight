package com.branchlight.backend.search.content;

import java.util.Objects;

public record OriginalMetadataEntry(
        String source,
        String name,
        String value) {

    public OriginalMetadataEntry {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(value, "value must not be null");
        if (source.isBlank()) {
            throw new IllegalArgumentException(
                    "source must not be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException(
                    "name must not be blank");
        }
    }

    @Override
    public String toString() {
        return "OriginalMetadataEntry[source="
                + source
                + ", name="
                + name
                + ", value=<redacted>]";
    }
}

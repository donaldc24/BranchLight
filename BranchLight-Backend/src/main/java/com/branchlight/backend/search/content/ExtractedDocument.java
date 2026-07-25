package com.branchlight.backend.search.content;

import java.util.List;
import java.util.Objects;

public record ExtractedDocument(
        String documentId,
        List<ExtractedBlock> blocks) {

    public ExtractedDocument {
        Objects.requireNonNull(documentId, "documentId must not be null");
        blocks = List.copyOf(Objects.requireNonNull(
                blocks,
                "blocks must not be null"));
        if (documentId.isBlank()) {
            throw new IllegalArgumentException(
                    "documentId must not be blank");
        }
    }
}
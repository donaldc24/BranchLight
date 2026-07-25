package com.branchlight.backend.search.content;

import java.util.Objects;

public record ExtractedBlock(
        Type type,
        String text,
        SourcePosition position,
        int headingLevel) {

    public ExtractedBlock {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(position, "position must not be null");
        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (type == Type.HEADING
                && (headingLevel < 1 || headingLevel > 6)) {
            throw new IllegalArgumentException(
                    "headingLevel must be between 1 and 6 for headings");
        }
        if (type != Type.HEADING && headingLevel != 0) {
            throw new IllegalArgumentException(
                    "headingLevel must be zero for non-headings");
        }
    }

    public static ExtractedBlock heading(
            int level,
            String text,
            SourcePosition position) {
        return new ExtractedBlock(
                Type.HEADING,
                text,
                position,
                level);
    }

    public static ExtractedBlock paragraph(
            String text,
            SourcePosition position) {
        return new ExtractedBlock(
                Type.PARAGRAPH,
                text,
                position,
                0);
    }

    public static ExtractedBlock list(
            String text,
            SourcePosition position) {
        return new ExtractedBlock(
                Type.LIST,
                text,
                position,
                0);
    }

    public enum Type {
        HEADING,
        PARAGRAPH,
        LIST
    }
}
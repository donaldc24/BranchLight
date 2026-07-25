package com.branchlight.backend.search.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class PassageSplitter {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final PassageSplitterOptions options;

    public PassageSplitter() {
        this(PassageSplitterOptions.DEFAULTS);
    }

    public PassageSplitter(PassageSplitterOptions options) {
        this.options = Objects.requireNonNull(
                options,
                "options must not be null");
    }

    public PassageSplitter(
            int targetWordCount,
            int maximumWordCount,
            int overlapWordCount) {
        this(new PassageSplitterOptions(
                targetWordCount,
                maximumWordCount,
                overlapWordCount));
    }

    public List<Passage> split(ExtractedDocument document) {
        Objects.requireNonNull(document, "document must not be null");

        var passages = new ArrayList<Passage>();
        var headings = new ArrayList<Heading>();
        var pending = new ArrayList<TextSegment>();

        for (ExtractedBlock block : document.blocks()) {
            if (block.type() == ExtractedBlock.Type.HEADING) {
            emit(document.documentId(), headings, pending, passages);
                updateHeadingPath(
                headings,
                        block.headingLevel(),
                        normalize(block.text()));
                continue;
            }

            List<TextSegment> segments = splitBlock(block);
            for (int index = 0; index < segments.size(); index++) {
                TextSegment segment = segments.get(index);
                int pendingWordCount = countWords(pending);
                if (!pending.isEmpty()
                        && (pendingWordCount >= options.targetWordCount()
                                || pendingWordCount + segment.wordCount()
                                        > options.maximumWordCount())) {
                    emit(
                            document.documentId(),
                            headings,
                            pending,
                            passages);
                }

                pending.add(segment);
                if (index < segments.size() - 1) {
                    emit(
                            document.documentId(),
                            headings,
                            pending,
                            passages);
                }
            }
        }

        emit(document.documentId(), headings, pending, passages);
        return List.copyOf(passages);
    }

    private List<TextSegment> splitBlock(ExtractedBlock block) {
        String text = normalize(block.text());
        int wordCount = countWords(text);
        if (wordCount <= options.maximumWordCount()) {
            return List.of(new TextSegment(
                    text,
                    block.position(),
                    wordCount));
        }

        List<TextSegment> sentences = sentences(text, block.position());
        var chunks = new ArrayList<TextSegment>();
        var pending = new ArrayList<TextSegment>();

        for (TextSegment sentence : sentences) {
            if (sentence.wordCount() > options.maximumWordCount()) {
                flushSegments(pending, chunks);
                chunks.addAll(splitWords(sentence));
                continue;
            }

            if (!pending.isEmpty()
                    && countWords(pending) + sentence.wordCount()
                            > options.maximumWordCount()) {
                TextSegment previous = combine(pending);
                chunks.add(previous);
                pending.clear();
                addOverlap(previous, pending);
            }
            if (!pending.isEmpty()
                    && countWords(pending) + sentence.wordCount()
                            > options.maximumWordCount()) {
                pending.clear();
            }
            pending.add(sentence);
        }

        flushSegments(pending, chunks);
        return List.copyOf(chunks);
    }

    private List<TextSegment> sentences(
            String text,
            SourcePosition blockPosition) {
        var sentences = new ArrayList<TextSegment>();
        int sentenceStart = 0;
        for (int index = 0; index < text.length(); index++) {
            if (!isSentenceTerminator(text.charAt(index))) {
                continue;
            }
            int sentenceEnd = index + 1;
            while (sentenceEnd < text.length()
                    && isClosingPunctuation(text.charAt(sentenceEnd))) {
                sentenceEnd++;
            }
            if (sentenceEnd == text.length()
                    || Character.isWhitespace(text.charAt(sentenceEnd))) {
                addSentence(
                        text,
                        sentenceStart,
                        sentenceEnd,
                        blockPosition,
                        sentences);
                sentenceStart = sentenceEnd;
                while (sentenceStart < text.length()
                        && Character.isWhitespace(
                                text.charAt(sentenceStart))) {
                    sentenceStart++;
                }
                index = sentenceStart - 1;
            }
        }
        if (sentenceStart < text.length()) {
            addSentence(
                    text,
                    sentenceStart,
                    text.length(),
                    blockPosition,
                    sentences);
        }
        return List.copyOf(sentences);
    }

    private static void addSentence(
            String text,
            int start,
            int end,
            SourcePosition blockPosition,
            List<TextSegment> sentences) {
        String sentence = normalize(text.substring(start, end));
        if (sentence.isBlank()) {
            return;
        }
        sentences.add(new TextSegment(
                sentence,
                new SourcePosition(
                        blockPosition.startOffset() + start,
                        blockPosition.startOffset() + end),
                countWords(sentence)));
    }

    private static boolean isSentenceTerminator(char character) {
        return character == '.' || character == '!' || character == '?';
    }

    private static boolean isClosingPunctuation(char character) {
        return character == '"'
                || character == '\''
                || character == ')'
                || character == ']';
    }

    private List<TextSegment> splitWords(TextSegment segment) {
        String[] words = WHITESPACE.split(segment.text());
        var chunks = new ArrayList<TextSegment>();
        int step = options.maximumWordCount()
                - options.overlapWordCount();

        for (int start = 0; start < words.length; start += step) {
            int end = Math.min(
                    start + options.maximumWordCount(),
                    words.length);
            String text = String.join(
                    " ",
                    List.of(words).subList(start, end));
            chunks.add(new TextSegment(
                    text,
                    segment.position(),
                    end - start));
            if (end == words.length) {
                break;
            }
        }
        return List.copyOf(chunks);
    }

    private void addOverlap(
            TextSegment previous,
            List<TextSegment> destination) {
        if (options.overlapWordCount() == 0) {
            return;
        }
        String[] words = WHITESPACE.split(previous.text());
        int start = Math.max(
                0,
                words.length - options.overlapWordCount());
        String overlap = String.join(
                " ",
                List.of(words).subList(start, words.length));
        destination.add(new TextSegment(
                overlap,
                previous.position(),
                words.length - start));
    }

    private static void flushSegments(
            List<TextSegment> pending,
            List<TextSegment> destination) {
        if (pending.isEmpty()) {
            return;
        }
        destination.add(combine(pending));
        pending.clear();
    }

    private static TextSegment combine(List<TextSegment> segments) {
        String text = segments.stream()
                .map(TextSegment::text)
                .reduce((left, right) -> left + " " + right)
                .orElseThrow();
        return new TextSegment(
                text,
                new SourcePosition(
                        segments.get(0).position().startOffset(),
                        segments.get(segments.size() - 1)
                                .position()
                                .endOffset()),
                countWords(segments));
    }

    private static void emit(
            String documentId,
            List<Heading> headings,
            List<TextSegment> pending,
            List<Passage> passages) {
        if (pending.isEmpty()) {
            return;
        }
        TextSegment combined = combineWithParagraphs(pending);
        passages.add(new Passage(
                documentId,
            headings.stream().map(Heading::text).toList(),
                combined.text(),
                combined.position(),
                combined.wordCount()));
        pending.clear();
    }

    private static TextSegment combineWithParagraphs(
            List<TextSegment> segments) {
        String text = segments.stream()
                .map(TextSegment::text)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElseThrow();
        return new TextSegment(
                text,
                new SourcePosition(
                        segments.get(0).position().startOffset(),
                        segments.get(segments.size() - 1)
                                .position()
                                .endOffset()),
                countWords(segments));
    }

    private static void updateHeadingPath(
            List<Heading> headings,
            int level,
            String heading) {
        while (!headings.isEmpty()
                && headings.get(headings.size() - 1).level() >= level) {
            headings.remove(headings.size() - 1);
        }
        headings.add(new Heading(level, heading));
    }

    private static int countWords(List<TextSegment> segments) {
        return segments.stream()
                .mapToInt(TextSegment::wordCount)
                .sum();
    }

    private static int countWords(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return 0;
        }
        return WHITESPACE.split(normalized).length;
    }

    private static String normalize(String text) {
        return WHITESPACE.matcher(text.strip()).replaceAll(" ");
    }

    private record TextSegment(
            String text,
            SourcePosition position,
            int wordCount) {
    }

    private record Heading(int level, String text) {
    }
}
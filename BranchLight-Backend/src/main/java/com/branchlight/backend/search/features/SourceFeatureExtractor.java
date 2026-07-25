package com.branchlight.backend.search.features;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.branchlight.backend.search.content.ContentExtractionSuccess;

public final class SourceFeatureExtractor {

    private static final Pattern WORD_PATTERN =
            Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Pattern REFERENCE_HEADING = pattern(
            "\\b(references?|citations?|sources?|bibliography|notes)\\b");
    private static final Pattern FIRST_PARTY = pattern(
            "\\b(i|we|my|our)\\b.{0,50}\\b(observed|measured|collected|created|built|tested|recorded|interviewed)\\b");
    private static final Pattern DEFINITION = pattern(
            "\\b(means|refers to|is defined as|can be defined as)\\b");
    private static final Pattern EXAMPLE = pattern(
            "\\b(for example|for instance|e\\.g\\.|example)\\b");
    private static final Pattern EXPLANATORY_HEADING = pattern(
            "^(what|why|how)\\b|\\b(overview|understanding|explained|explanation)\\b");
    private static final Pattern SUMMARY_HEADING = pattern(
            "\\b(summary|introduction|overview|conclusion|in brief)\\b");
    private static final Pattern PROGRESSION = pattern(
            "\\b(first|second|next|then|subsequently|finally|therefore|as a result)\\b");
    private static final Pattern EXPECTED_RESULT = pattern(
            "\\b(expected (result|output|outcome)|should (see|produce|return)|resulting output)\\b");
    private static final Pattern LIMITATION = pattern(
            "\\b(limitations?|caveats?|boundaries|constraints?)\\b");
    private static final Pattern RISK = pattern(
            "\\b(risks?|hazards?|dangers?|adverse effects?)\\b");
    private static final Pattern DRAWBACK = pattern(
            "\\b(drawbacks?|disadvantages?|downsides?|shortcomings?)\\b");
    private static final Pattern COUNTERARGUMENT = pattern(
            "\\b(counterarguments?|opposing view|critics? (argue|contend)|on the other hand)\\b");
    private static final Pattern TRADEOFF = pattern(
            "\\b(trade[ -]?offs?|balance between|at the expense of)\\b");
    private static final Pattern FAILURE = pattern(
            "\\b(failure cases?|failure modes?|when .{0,30} fails?|can fail)\\b");
    private static final Pattern UNCERTAINTY = pattern(
            "\\b(may|might|could|possibly|perhaps|uncertain|unclear|likely|unlikely|appears?|suggests?)\\b");
    private static final Pattern METHOD = pattern(
            "\\b(methods?|methodology|data|sample|measurement|analysis)\\b");
    private static final Pattern FIRST_PERSON_EXPERIENCE = pattern(
            "\\b(i|we|my|our)\\b.{0,50}\\b(experienced|observed|found|tried|used|built|created|tested|measured|learned)\\b");
    private static final Pattern DIFFERING_VIEWPOINT = pattern(
            "\\b(however|but|although|whereas|disagree|different view|on the other hand|in contrast)\\b");
    private static final Pattern CERTAINTY = pattern(
            "\\b(proves?|guarantees?|always|never|undeniably|certainly|without doubt|cannot fail)\\b");
    private static final Pattern SENSATIONAL = pattern(
            "\\b(shocking|unbelievable|incredible|ultimate|secret|must see|mind-blowing)\\b");
    private static final Pattern CHECKBOX_TEXT = pattern(
            "^\\s*(\\[\\s?\\]|\\[x\\])\\s+");
    private static final Pattern CONFIGURATION_LINE = Pattern.compile(
            "(?m)^\\s*[\\p{L}_][\\p{L}\\p{N}_.-]*\\s*[:=]\\s*\\S+");
    private static final Pattern AFFILIATE_PARAMETER = pattern(
            "[?&](affiliate|aff_id|partner_id|referral)=[^&]+");
    private static final Pattern DOWNLOAD_LABEL = pattern(
            "\\b(download|repository|source code|project files?)\\b");
    private static final Pattern DOWNLOAD_EXTENSION = pattern(
            "\\.(zip|tar|gz|tgz|pdf|epub)(?:$|[?#])");

    public SourceFeatureSet extract(SourceFeatureInput input) {
        ContentExtractionSuccess extraction = input.extraction();
        Document document = Jsoup.parse(
                input.sourceContent(),
                extraction.sourceUrl().toString());
        String text = extraction.mainText();
        List<String> words = words(text);
        List<String> headings = extraction.headings().stream()
                .map(heading -> heading.text())
                .toList();
        Elements links = document.select("a[href]");
        var values = new EnumMap<SourceFeature, SourceFeatureValue>(
                SourceFeature.class);

        double citationCount = document.select(
                "cite, a[role=doc-biblioref], a[href^=#ref], "
                        + "[class*=citation], [class*=bibliograph]").size()
                + countMatching(headings, REFERENCE_HEADING);
        putBoolean(values, SourceFeature.IDENTIFIED_AUTHOR,
                extraction.author() != null);
        putBoolean(values, SourceFeature.IDENTIFIED_PUBLISHER,
                extraction.publisher() != null);
        putBoolean(values, SourceFeature.PUBLICATION_DATE_PRESENT,
                extraction.publicationDate() != null);
        putBoolean(values, SourceFeature.MODIFIED_DATE_PRESENT,
                extraction.modifiedDate() != null);
        put(values, SourceFeature.REFERENCES_OR_CITATIONS_PRESENT,
                citationCount, saturate(citationCount, 3.0));
        put(values, SourceFeature.STRUCTURED_METADATA_PRESENT,
                extraction.structuredMetadata().size(),
                saturate(extraction.structuredMetadata().size(), 1.0));
        double originalMaterial = countMatches(text, FIRST_PARTY);
        put(values, SourceFeature.ORIGINAL_OR_FIRST_PARTY_MATERIAL,
                originalMaterial, saturate(originalMaterial, 3.0));

        double definitions = document.select("dfn").size()
                + countMatches(text, DEFINITION);
        double examples = countMatching(headings, EXAMPLE)
                + countMatches(text, EXAMPLE);
        double explanatoryHeadings = countMatching(
                headings,
                EXPLANATORY_HEADING);
        double summaries = document.select("summary").size()
                + countMatching(headings, SUMMARY_HEADING);
        double progression = countMatches(text, PROGRESSION)
                + Math.max(0, distinctHeadingLevels(extraction) - 1);
        double readability = fleschReadingEase(words, text);
        double jargonDensity = jargonDensity(words);
        put(values, SourceFeature.DEFINITIONS_PRESENT,
                definitions, saturate(definitions, 3.0));
        put(values, SourceFeature.EXAMPLES_PRESENT,
                examples, saturate(examples, 3.0));
        put(values, SourceFeature.EXPLANATORY_HEADINGS,
                explanatoryHeadings, saturate(explanatoryHeadings, 3.0));
        put(values, SourceFeature.SUMMARY_OR_INTRODUCTION_PRESENT,
                summaries, saturate(summaries, 2.0));
        put(values, SourceFeature.CONCEPTUAL_PROGRESSION,
                progression, saturate(progression, 5.0));
        put(values, SourceFeature.READABILITY_ESTIMATE,
                readability, clamp(readability / 100.0));
        put(values, SourceFeature.JARGON_DENSITY_ESTIMATE,
                jargonDensity, saturate(jargonDensity, 0.30));

        double orderedSteps = document.select("ol > li").size();
        double codeBlocks = extraction.codeBlockCount();
        double workedExamples = workedExampleCount(document, headings);
        double configurationExamples = configurationExampleCount(document);
        double checklists = document.select("input[type=checkbox]").size()
                + document.select("li").stream()
                        .filter(item -> CHECKBOX_TEXT.matcher(item.text())
                                .find())
                        .count();
        double expectedResults = countMatching(headings, EXPECTED_RESULT)
                + countMatches(text, EXPECTED_RESULT);
        double downloadableLinks = downloadableLinkCount(links);
        put(values, SourceFeature.ORDERED_STEPS,
                orderedSteps, saturate(orderedSteps, 5.0));
        put(values, SourceFeature.CODE_BLOCKS,
                codeBlocks, saturate(codeBlocks, 3.0));
        put(values, SourceFeature.WORKED_EXAMPLES,
                workedExamples, saturate(workedExamples, 2.0));
        put(values, SourceFeature.CONFIGURATION_EXAMPLES,
                configurationExamples,
                saturate(configurationExamples, 2.0));
        put(values, SourceFeature.CHECKLISTS,
                checklists, saturate(checklists, 5.0));
        put(values, SourceFeature.EXPECTED_RESULTS,
                expectedResults, saturate(expectedResults, 3.0));
        put(values, SourceFeature.DOWNLOADABLE_OR_REPOSITORY_LINKS,
                downloadableLinks, saturate(downloadableLinks, 3.0));

        double limitations = countMatching(headings, LIMITATION);
        double risks = countMatching(headings, RISK)
                + countMatches(text, RISK);
        double drawbacks = countMatching(headings, DRAWBACK)
                + countMatches(text, DRAWBACK);
        double counterarguments = countMatching(headings, COUNTERARGUMENT)
                + countMatches(text, COUNTERARGUMENT);
        double tradeoffs = countMatching(headings, TRADEOFF)
                + countMatches(text, TRADEOFF);
        double failureCases = countMatching(headings, FAILURE)
                + countMatches(text, FAILURE);
        double uncertainty = countMatches(text, UNCERTAINTY);
        double methodologyLimitations = methodologyLimitationCount(text);
        put(values, SourceFeature.LIMITATIONS_SECTIONS,
                limitations, saturate(limitations, 2.0));
        put(values, SourceFeature.RISKS,
                risks, saturate(risks, 3.0));
        put(values, SourceFeature.DRAWBACKS,
                drawbacks, saturate(drawbacks, 3.0));
        put(values, SourceFeature.COUNTERARGUMENTS,
                counterarguments, saturate(counterarguments, 3.0));
        put(values, SourceFeature.TRADEOFFS,
                tradeoffs, saturate(tradeoffs, 3.0));
        put(values, SourceFeature.FAILURE_CASES,
                failureCases, saturate(failureCases, 3.0));
        put(values, SourceFeature.UNCERTAINTY_LANGUAGE,
                uncertainty, saturate(uncertainty, 8.0));
        put(values, SourceFeature.METHODOLOGY_LIMITATIONS,
                methodologyLimitations,
                saturate(methodologyLimitations, 3.0));

        Elements discussionElements = discussionElements(document);
        double participants = distinctParticipantCount(document);
        double replies = extraction.possibleCommentOrReplyCount();
        double questions = questionAnswerCount(document, headings);
        double firstPerson = countMatches(text, FIRST_PERSON_EXPERIENCE);
        double conversationDepth = conversationDepth(discussionElements);
        double viewpoints = discussionElements.stream()
                .map(Element::text)
                .mapToLong(value -> countMatches(
                        value,
                        DIFFERING_VIEWPOINT))
                .sum();
        put(values, SourceFeature.MULTIPLE_PARTICIPANTS,
                participants, saturate(participants, 3.0));
        put(values, SourceFeature.REPLIES_OR_COMMENTS,
                replies, saturate(replies, 5.0));
        put(values, SourceFeature.QUESTION_AND_ANSWER_STRUCTURE,
                questions, saturate(questions, 3.0));
        put(values, SourceFeature.FIRST_PERSON_EXPERIENCE,
                firstPerson, saturate(firstPerson, 5.0));
        put(values, SourceFeature.CONVERSATION_DEPTH,
                conversationDepth, saturate(conversationDepth, 4.0));
        put(values, SourceFeature.DIFFERING_VIEWPOINTS,
                viewpoints, saturate(viewpoints, 4.0));

        double visibleWords = extraction.visibleWordCount();
        double duplicateRatio = duplicateTextRatio(document);
        double affiliateRatio = affiliateLinkRatio(links);
        double advertisements = advertisementCount(document);
        boolean missingAttribution = extraction.author() == null
                && document.select(
                        "[rel=author], [itemprop=author], .byline, "
                                + "[class*=author]").isEmpty();
        double sensationalTitle = sensationalTitleScore(
                extraction.pageTitle());
        double unsupportedCertainty = citationCount == 0.0
                ? countMatches(text, CERTAINTY)
                : 0.0;
        double keywordShare = maximumKeywordShare(words);
        put(values, SourceFeature.THIN_CONTENT,
                visibleWords, clamp(1.0 - visibleWords / 300.0));
        put(values, SourceFeature.DUPLICATED_TEXT,
                duplicateRatio, clamp(duplicateRatio));
        put(values, SourceFeature.EXCESSIVE_AFFILIATE_LINKS,
                affiliateRatio, saturate(affiliateRatio, 0.20));
        put(values, SourceFeature.EXCESSIVE_ADVERTISEMENTS,
                advertisements, saturate(advertisements, 3.0));
        putBoolean(values, SourceFeature.MISSING_ATTRIBUTION,
                missingAttribution);
        put(values, SourceFeature.SENSATIONAL_TITLE,
                sensationalTitle, saturate(sensationalTitle, 3.0));
        put(values, SourceFeature.UNSUPPORTED_CERTAINTY,
                unsupportedCertainty,
                saturate(unsupportedCertainty, 3.0));
        put(values, SourceFeature.KEYWORD_STUFFING,
                keywordShare,
                clamp((keywordShare - 0.08) / 0.22));

        return new SourceFeatureSet(
                input.candidate().document().documentId(),
                values);
    }

    private static Pattern pattern(String expression) {
        return Pattern.compile(expression,
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static void putBoolean(
            Map<SourceFeature, SourceFeatureValue> values,
            SourceFeature feature,
            boolean present) {
        double value = present ? 1.0 : 0.0;
        put(values, feature, value, value);
    }

    private static void put(
            Map<SourceFeature, SourceFeatureValue> values,
            SourceFeature feature,
            double raw,
            double normalized) {
        values.put(feature, new SourceFeatureValue(raw, normalized));
    }

    private static double saturate(double raw, double threshold) {
        return clamp(raw / threshold);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static long countMatches(String text, Pattern pattern) {
        var matcher = pattern.matcher(text);
        long count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static long countMatching(
            List<String> values,
            Pattern pattern) {
        return values.stream()
                .filter(value -> pattern.matcher(value).find())
                .count();
    }

    private static List<String> words(String text) {
        var matcher = WORD_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        var words = new ArrayList<String>();
        while (matcher.find()) {
            words.add(matcher.group());
        }
        return List.copyOf(words);
    }

    private static int distinctHeadingLevels(
            ContentExtractionSuccess extraction) {
        return (int) extraction.headings().stream()
                .map(heading -> heading.level())
                .distinct()
                .count();
    }

    private static double fleschReadingEase(
            List<String> words,
            String text) {
        if (words.isEmpty()) {
            return 0.0;
        }
        int sentenceCount = Math.max(
                1,
                text.split("[.!?]+(?:\\s+|$)").length);
        int syllables = words.stream()
                .mapToInt(SourceFeatureExtractor::syllables)
                .sum();
        return 206.835
                - 1.015 * words.size() / sentenceCount
                - 84.6 * syllables / words.size();
    }

    private static int syllables(String word) {
        String normalized = word.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z]", "");
        if (normalized.isEmpty()) {
            return 1;
        }
        int count = 0;
        boolean previousVowel = false;
        for (int index = 0; index < normalized.length(); index++) {
            boolean vowel = "aeiouy".indexOf(normalized.charAt(index)) >= 0;
            if (vowel && !previousVowel) {
                count++;
            }
            previousVowel = vowel;
        }
        if (normalized.endsWith("e") && count > 1) {
            count--;
        }
        return Math.max(1, count);
    }

    private static double jargonDensity(List<String> words) {
        if (words.isEmpty()) {
            return 0.0;
        }
        long jargonWords = words.stream()
                .filter(word -> word.length() >= 12
                        || syllables(word) >= 4)
                .count();
        return (double) jargonWords / words.size();
    }

    private static double workedExampleCount(
            Document document,
            List<String> headings) {
        long exampleHeadings = countMatching(headings, EXAMPLE);
        int evidence = document.select(
                "pre, code, table, [class*=equation]").size();
        return Math.min(exampleHeadings, evidence);
    }

    private static long configurationExampleCount(Document document) {
        return document.select("pre, code").stream()
                .filter(block -> countMatches(
                        block.wholeText(),
                        CONFIGURATION_LINE) >= 2)
                .count();
    }

    private static long downloadableLinkCount(Elements links) {
        return links.stream().filter(link -> {
            String href = link.attr("href");
            return link.hasAttr("download")
                    || DOWNLOAD_LABEL.matcher(link.text()).find()
                    || DOWNLOAD_EXTENSION.matcher(href).find();
        }).count();
    }

    private static long methodologyLimitationCount(String text) {
        long count = 0;
        for (String sentence : text.split("(?<=[.!?])\\s+")) {
            if (METHOD.matcher(sentence).find()
                    && LIMITATION.matcher(sentence).find()) {
                count++;
            }
        }
        return count;
    }

    private static Elements discussionElements(Document document) {
        return document.select(
                "[class*=comment], [id*=comment], [class*=reply], "
                        + "[id*=reply], [itemtype*=Comment], "
                        + "article[data-author]");
    }

    private static int distinctParticipantCount(Document document) {
        var participants = new HashSet<String>();
        for (Element element : document.select(
                "[data-author], [rel=author], [itemprop=author], "
                        + ".author, [class*=participant]")) {
            String value = element.hasAttr("data-author")
                    ? element.attr("data-author")
                    : element.text();
            if (!value.isBlank()) {
                participants.add(value.strip().toLowerCase(Locale.ROOT));
            }
        }
        return participants.size();
    }

    private static long questionAnswerCount(
            Document document,
            List<String> headings) {
        return headings.stream().filter(heading -> heading.strip()
                .endsWith("?")).count()
                + document.select(
                        "dt, [itemprop=question], [class*=question]").size();
    }

    private static int conversationDepth(Elements discussionElements) {
        int maximum = 0;
        Set<Element> discussionSet = new HashSet<>(discussionElements);
        for (Element element : discussionElements) {
            int depth = 1;
            Element ancestor = element.parent();
            while (ancestor != null) {
                if (discussionSet.contains(ancestor)) {
                    depth++;
                }
                ancestor = ancestor.parent();
            }
            maximum = Math.max(maximum, depth);
        }
        return maximum;
    }

    private static double duplicateTextRatio(Document document) {
        Elements elements = document.select(
                "main p, main li, article p, article li");
        if (elements.isEmpty()) {
            elements = document.select("p, li");
        }
        var counts = new HashMap<String, Integer>();
        int substantiveCount = 0;
        for (Element element : elements) {
            String normalized = String.join(" ", words(element.text()));
            if (normalized.split("\\s+").length < 5) {
                continue;
            }
            counts.merge(normalized, 1, Integer::sum);
            substantiveCount++;
        }
        if (substantiveCount == 0) {
            return 0.0;
        }
        int duplicates = counts.values().stream()
                .mapToInt(count -> Math.max(0, count - 1))
                .sum();
        return (double) duplicates / substantiveCount;
    }

    private static double affiliateLinkRatio(Elements links) {
        if (links.isEmpty()) {
            return 0.0;
        }
        long affiliateLinks = links.stream()
                .filter(link -> link.attr("rel").toLowerCase(Locale.ROOT)
                        .contains("sponsored")
                        || AFFILIATE_PARAMETER.matcher(
                                link.attr("href")).find())
                .count();
        return (double) affiliateLinks / links.size();
    }

    private static int advertisementCount(Document document) {
        return document.select(
                "[class*=advertisement], [id*=advertisement], "
                        + "[class~=ad], [id~=ad], [data-ad], "
                        + "[aria-label*=advertisement], [class*=sponsored]")
                .size();
    }

    private static double sensationalTitleScore(String title) {
        long exclamations = title.chars()
                .filter(character -> character == '!')
                .count();
        long uppercaseWords = Pattern.compile("\\b[A-Z]{4,}\\b")
                .matcher(title)
                .results()
                .count();
        return exclamations
                + uppercaseWords
                + countMatches(title, SENSATIONAL);
    }

    private static double maximumKeywordShare(List<String> words) {
        List<String> eligible = words.stream()
                .filter(word -> word.length() >= 4)
                .toList();
        if (eligible.isEmpty()) {
            return 0.0;
        }
        var frequencies = new HashMap<String, Integer>();
        eligible.forEach(word -> frequencies.merge(word, 1, Integer::sum));
        int maximum = frequencies.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        return (double) maximum / eligible.size();
    }
}
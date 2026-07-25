package com.branchlight.backend.search.features;

public enum SourceFeature {
    IDENTIFIED_AUTHOR(SourceFeatureGroup.PROVENANCE,
            "Raw is 1 when extracted author metadata is present; normalized equals raw."),
    IDENTIFIED_PUBLISHER(SourceFeatureGroup.PROVENANCE,
            "Raw is 1 when extracted publisher metadata is present; normalized equals raw."),
    PUBLICATION_DATE_PRESENT(SourceFeatureGroup.PROVENANCE,
            "Raw is 1 when an extracted publication date is present; normalized equals raw."),
    MODIFIED_DATE_PRESENT(SourceFeatureGroup.PROVENANCE,
            "Raw is 1 when an extracted modified date is present; normalized equals raw."),
    REFERENCES_OR_CITATIONS_PRESENT(SourceFeatureGroup.PROVENANCE,
            "Raw counts cite elements, bibliographic links, and reference-section headings; normalized saturates at 3."),
    STRUCTURED_METADATA_PRESENT(SourceFeatureGroup.PROVENANCE,
            "Raw counts extracted structured metadata blocks; normalized saturates at 1."),
    ORIGINAL_OR_FIRST_PARTY_MATERIAL(SourceFeatureGroup.PROVENANCE,
            "Raw counts first-person statements paired with observation or creation verbs; normalized saturates at 3."),

    DEFINITIONS_PRESENT(SourceFeatureGroup.EXPLANATION,
            "Raw counts dfn elements and definition phrases such as 'means' or 'refers to'; normalized saturates at 3."),
    EXAMPLES_PRESENT(SourceFeatureGroup.EXPLANATION,
            "Raw counts example headings and general example phrases; normalized saturates at 3."),
    EXPLANATORY_HEADINGS(SourceFeatureGroup.EXPLANATION,
            "Raw counts headings framed as what, why, how, overview, or understanding; normalized saturates at 3."),
    SUMMARY_OR_INTRODUCTION_PRESENT(SourceFeatureGroup.EXPLANATION,
            "Raw counts summary, introduction, overview, or conclusion headings and summary elements; normalized saturates at 2."),
    CONCEPTUAL_PROGRESSION(SourceFeatureGroup.EXPLANATION,
            "Raw combines sequence-transition phrases with distinct heading levels beyond the first; normalized saturates at 5."),
    READABILITY_ESTIMATE(SourceFeatureGroup.EXPLANATION,
            "Raw is Flesch reading ease; normalized clamps raw divided by 100 to [0,1]."),
    JARGON_DENSITY_ESTIMATE(SourceFeatureGroup.EXPLANATION,
            "Raw is the fraction of words with at least four estimated syllables or twelve characters; normalized saturates at a 30% fraction."),

    ORDERED_STEPS(SourceFeatureGroup.PRACTICAL,
            "Raw counts list items inside ordered lists; normalized saturates at 5."),
    CODE_BLOCKS(SourceFeatureGroup.PRACTICAL,
            "Raw is the extracted preformatted code-block count; normalized saturates at 3."),
    WORKED_EXAMPLES(SourceFeatureGroup.PRACTICAL,
            "Raw counts example sections that also contain code, tables, or numeric equations; normalized saturates at 2."),
    CONFIGURATION_EXAMPLES(SourceFeatureGroup.PRACTICAL,
            "Raw counts preformatted blocks with at least two key-value or assignment lines; normalized saturates at 2."),
    CHECKLISTS(SourceFeatureGroup.PRACTICAL,
            "Raw counts checkbox controls and list items beginning with checkbox markers; normalized saturates at 5."),
    EXPECTED_RESULTS(SourceFeatureGroup.PRACTICAL,
            "Raw counts expected-result headings and phrases such as expected output or should see; normalized saturates at 3."),
    DOWNLOADABLE_OR_REPOSITORY_LINKS(SourceFeatureGroup.PRACTICAL,
            "Raw counts links identified structurally or by generic download, repository, or source-code labels; normalized saturates at 3."),

    LIMITATIONS_SECTIONS(SourceFeatureGroup.CRITICAL,
            "Raw counts headings containing limitation, caveat, or boundary cues; normalized saturates at 2."),
    RISKS(SourceFeatureGroup.CRITICAL,
            "Raw counts risk or hazard headings and phrases; normalized saturates at 3."),
    DRAWBACKS(SourceFeatureGroup.CRITICAL,
            "Raw counts drawback, disadvantage, downside, or shortcoming cues; normalized saturates at 3."),
    COUNTERARGUMENTS(SourceFeatureGroup.CRITICAL,
            "Raw counts counterargument and opposing-view cues; normalized saturates at 3."),
    TRADEOFFS(SourceFeatureGroup.CRITICAL,
            "Raw counts tradeoff and balancing-language cues; normalized saturates at 3."),
    FAILURE_CASES(SourceFeatureGroup.CRITICAL,
            "Raw counts failure-case headings and phrases; normalized saturates at 3."),
    UNCERTAINTY_LANGUAGE(SourceFeatureGroup.CRITICAL,
            "Raw counts modal uncertainty terms such as may, might, could, uncertain, or likely; normalized saturates at 8."),
    METHODOLOGY_LIMITATIONS(SourceFeatureGroup.CRITICAL,
            "Raw counts sentences containing both method or data cues and limitation cues; normalized saturates at 3."),

    MULTIPLE_PARTICIPANTS(SourceFeatureGroup.HUMAN_DISCUSSION,
            "Raw is the number of distinct visible participant labels; normalized saturates at 3 participants."),
    REPLIES_OR_COMMENTS(SourceFeatureGroup.HUMAN_DISCUSSION,
            "Raw is the extracted possible comment or reply count; normalized saturates at 5."),
    QUESTION_AND_ANSWER_STRUCTURE(SourceFeatureGroup.HUMAN_DISCUSSION,
            "Raw counts question headings, question elements, and definition-list question terms; normalized saturates at 3."),
    FIRST_PERSON_EXPERIENCE(SourceFeatureGroup.HUMAN_DISCUSSION,
            "Raw counts first-person pronouns near experience or action verbs; normalized saturates at 5."),
    CONVERSATION_DEPTH(SourceFeatureGroup.HUMAN_DISCUSSION,
            "Raw is the maximum nesting depth of comment or reply containers; normalized saturates at depth 4."),
    DIFFERING_VIEWPOINTS(SourceFeatureGroup.HUMAN_DISCUSSION,
            "Raw counts contrast and disagreement cues inside discussion containers; normalized saturates at 4."),

    THIN_CONTENT(SourceFeatureGroup.QUALITY_AND_RISK,
            "Raw is extracted visible word count; normalized risk is one minus raw divided by 300, clamped to [0,1]."),
    DUPLICATED_TEXT(SourceFeatureGroup.QUALITY_AND_RISK,
            "Raw is the fraction of substantive paragraphs or list items duplicated after normalization; normalized equals raw."),
    EXCESSIVE_AFFILIATE_LINKS(SourceFeatureGroup.QUALITY_AND_RISK,
            "Raw is the fraction of links carrying sponsored relation or generic affiliate/referral parameters; normalized risk saturates at a 20% fraction."),
    EXCESSIVE_ADVERTISEMENTS(SourceFeatureGroup.QUALITY_AND_RISK,
            "Raw counts elements with explicit advertisement, sponsored, or ad-slot markers; normalized saturates at 3."),
    MISSING_ATTRIBUTION(SourceFeatureGroup.QUALITY_AND_RISK,
            "Raw is 1 when neither author metadata nor visible byline markup is present; normalized equals raw."),
    SENSATIONAL_TITLE(SourceFeatureGroup.QUALITY_AND_RISK,
            "Raw combines title exclamation marks, mostly-uppercase words, and generic sensational cues; normalized saturates at 3."),
    UNSUPPORTED_CERTAINTY(SourceFeatureGroup.QUALITY_AND_RISK,
            "Raw counts absolute-certainty cues only when no citation evidence exists; normalized saturates at 3."),
    KEYWORD_STUFFING(SourceFeatureGroup.QUALITY_AND_RISK,
            "Raw is the largest frequency share among words of at least four characters; normalized risk rises from 8% and saturates at 30%."),
    ;

    private final SourceFeatureGroup group;
    private final String heuristic;

    SourceFeature(SourceFeatureGroup group, String heuristic) {
        this.group = group;
        this.heuristic = heuristic;
    }

    public SourceFeatureGroup group() {
        return group;
    }

    public String heuristic() {
        return heuristic;
    }
}
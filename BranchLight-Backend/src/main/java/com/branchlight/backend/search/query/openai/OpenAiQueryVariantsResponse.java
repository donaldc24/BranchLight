package com.branchlight.backend.search.query.openai;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription(
        "Five concise search-engine query variants, one for each "
                + "retrieval purpose.")
public final class OpenAiQueryVariantsResponse {

    @JsonPropertyDescription(
            "A query oriented toward original, official, primary, "
                    + "or direct sources.")
    public String authoritative;

    @JsonPropertyDescription(
            "A query oriented toward a clear explanation or overview.")
    public String explanatory;

    @JsonPropertyDescription(
            "A query oriented toward examples, procedures, guides, "
                    + "or practical application.")
    public String practical;

    @JsonPropertyDescription(
            "A query oriented toward limitations, risks, "
                    + "counterarguments, or tradeoffs.")
    public String critical;

    @JsonPropertyDescription(
            "A query oriented toward firsthand experiences "
                    + "or substantive human discussion.")
    public String humanDiscussion;
}

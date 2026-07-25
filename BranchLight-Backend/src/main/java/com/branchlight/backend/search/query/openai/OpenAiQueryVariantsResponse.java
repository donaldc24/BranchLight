package com.branchlight.backend.search.query.openai;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription(
        "Five distinct search-engine queries, each steering retrieval "
                + "toward a different source type.")
public final class OpenAiQueryVariantsResponse {

    @JsonPropertyDescription(
            "A query explicitly targeting official documentation, "
                    + "government, university, standards, primary "
                    + "research, or institutional sources.")
    public String authoritative;

    @JsonPropertyDescription(
            "A query explicitly targeting educational explainers, "
                    + "reference material, or clear overviews.")
    public String explanatory;

    @JsonPropertyDescription(
            "A query explicitly targeting tutorials, repair guides, "
                    + "procedures, demonstrations, or examples.")
    public String practical;

    @JsonPropertyDescription(
            "A query explicitly targeting limitations, risks, failure "
                    + "modes, criticism, or opposing evidence.")
    public String critical;

    @JsonPropertyDescription(
            "A query explicitly targeting forums, community discussions, "
                    + "firsthand experiences, or practitioner accounts.")
    public String humanDiscussion;
}

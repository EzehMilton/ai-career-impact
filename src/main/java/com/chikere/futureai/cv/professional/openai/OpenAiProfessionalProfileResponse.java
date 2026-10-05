package com.chikere.futureai.cv.professional.openai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** Provider-facing schema. Nullable scalar fields mean the CV did not support a value. */
public record OpenAiProfessionalProfileResponse(
        @JsonPropertyDescription("Current or most recent role title, or null when unclear") String currentRole,
        @JsonPropertyDescription("Concise factual summary of the person's current or most recent work") String roleSummary,
        @JsonPropertyDescription("Concrete work responsibilities, one concise item each") List<String> responsibilities,
        @JsonPropertyDescription("Named tools, technologies and technical capabilities") List<String> technicalSkills,
        @JsonPropertyDescription("Communication, collaboration and other human skills explicitly supported") List<String> humanSkills,
        @JsonPropertyDescription("People or organisational leadership responsibilities") List<String> leadershipResponsibilities,
        @JsonPropertyDescription("Decisions or judgements the person is responsible for") List<String> decisionMakingResponsibilities,
        @JsonPropertyDescription("Industries, domains or operating contexts explicitly supported") List<String> domainContext,
        @JsonPropertyDescription("Whole years of experience only when explicitly stated or reliably calculable, otherwise null") Integer yearsExperience,
        @JsonPropertyDescription("Short warnings about ambiguity or missing information") List<String> warnings) {
}

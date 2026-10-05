package com.chikere.futureai.cv.professional.openai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** Provider-facing schema. Nullable scalar fields mean the CV did not support a value. */
public record OpenAiProfessionalProfileResponse(
        @JsonPropertyDescription("Title of the single current dated employment entry, or null if current role is ambiguous") String currentRole,
        @JsonPropertyDescription("Concise factual summary based only on the selected current employment entry") String roleSummary,
        @JsonPropertyDescription("Responsibilities from only the selected current employment entry; empty if it is unclear") List<String> responsibilities,
        @JsonPropertyDescription("Named technical skills from an explicit profile/skills section or clearly demonstrated by the current role") List<String> technicalSkills,
        @JsonPropertyDescription("Human skills from an explicit profile/skills section or clearly demonstrated by the current role") List<String> humanSkills,
        @JsonPropertyDescription("Leadership responsibilities from only the selected current employment entry") List<String> leadershipResponsibilities,
        @JsonPropertyDescription("Decision-making responsibilities from only the selected current employment entry") List<String> decisionMakingResponsibilities,
        @JsonPropertyDescription("Role-specific domain context from only the selected current employment entry") List<String> domainContext,
        @JsonPropertyDescription("Approximate relevant experience in years across clearly dated relevant employment, overlaps counted once; null if uncertain, never zero as a placeholder") Integer yearsExperience,
        @JsonPropertyDescription("Short warnings about ambiguity or missing information") List<String> warnings) {
}

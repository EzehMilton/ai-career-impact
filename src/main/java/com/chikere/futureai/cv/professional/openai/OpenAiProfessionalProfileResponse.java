package com.chikere.futureai.cv.professional.openai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** Provider-facing schema. Nullable scalar fields mean the CV did not support a value. */
public record OpenAiProfessionalProfileResponse(
        @JsonPropertyDescription("Title of the single current dated employment entry, or null if current role is ambiguous") String currentRole,
        @JsonPropertyDescription("Employer of the selected current employment entry as written in the CV, or null if not stated or ambiguous") String currentEmployer,
        @JsonPropertyDescription("Start date of the selected current employment entry as written in the CV, or null if not stated") String currentRoleStart,
        @JsonPropertyDescription("End date of the selected current employment entry as written in the CV; \"Present\" when ongoing; null if not stated") String currentRoleEnd,
        @JsonPropertyDescription("One or two sentences, at least 40 characters, describing what the person does in the selected current employment entry in an ordinary working week, based only on its title, listed technologies and bullet points; no achievements, metrics or career history, and not copied from the CV's personal profile or summary section; written in the third person without 'I' or the person's name (for example 'Designs and maintains…')") String roleSummary,
        @JsonPropertyDescription("At most 12 recurring duties from only the selected current employment entry, each with word-for-word evidence; empty if it is unclear") List<Duty> dayToDayDuties,
        @JsonPropertyDescription("One-off results, awards, completed projects and metrics such as \"reduced costs by 20%\"; never duties") List<String> achievements,
        @JsonPropertyDescription("Named technical skills from an explicit profile/skills section or clearly demonstrated by the current role") List<String> technicalSkills,
        @JsonPropertyDescription("Human skills from an explicit profile/skills section or clearly demonstrated by the current role") List<String> humanSkills,
        @JsonPropertyDescription("Leadership responsibilities from only the selected current employment entry") List<String> leadershipResponsibilities,
        @JsonPropertyDescription("Decision-making responsibilities from only the selected current employment entry") List<String> decisionMakingResponsibilities,
        @JsonPropertyDescription("Role-specific domain context from only the selected current employment entry") List<String> domainContext,
        @JsonPropertyDescription("Approximate relevant experience in years across clearly dated relevant employment, overlaps counted once; null if uncertain, never zero as a placeholder") Integer yearsExperience,
        @JsonPropertyDescription("Short warnings about ambiguity or missing information") List<String> warnings) {

    /** A recurring duty and the CV text that supports it. */
    public record Duty(
            @JsonPropertyDescription("Short, plain description of a task the person does repeatedly in the current role") String duty,
            @JsonPropertyDescription("Short phrase copied word for word from the current-role entry of the CV that supports the duty; never paraphrased") String evidence) {
    }
}

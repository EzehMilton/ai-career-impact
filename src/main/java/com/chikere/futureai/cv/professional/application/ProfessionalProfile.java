package com.chikere.futureai.cv.professional.application;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * The small, reviewable set of professional facts understood from a CV.
 *
 * <p>The employer and dates only tell the user which job was used. They are never sent to an
 * assessment and never persisted.</p>
 */
public record ProfessionalProfile(
        String currentRole,
        String currentEmployer,
        String currentRoleStart,
        String currentRoleEnd,
        String roleSummary,
        List<String> responsibilities,
        List<String> technicalSkills,
        List<String> humanSkills,
        List<String> leadershipResponsibilities,
        List<String> decisionMakingResponsibilities,
        List<String> domainContext,
        OptionalInt yearsExperience,
        ProfessionalProfileCompleteness completeness,
        List<String> warnings) implements Serializable {

    public ProfessionalProfile {
        currentRole = text(currentRole);
        currentEmployer = text(currentEmployer);
        currentRoleStart = text(currentRoleStart);
        currentRoleEnd = text(currentRoleEnd);
        roleSummary = text(roleSummary);
        responsibilities = copy(responsibilities);
        technicalSkills = copy(technicalSkills);
        humanSkills = copy(humanSkills);
        leadershipResponsibilities = copy(leadershipResponsibilities);
        decisionMakingResponsibilities = copy(decisionMakingResponsibilities);
        domainContext = copy(domainContext);
        yearsExperience = Objects.requireNonNull(yearsExperience);
        completeness = Objects.requireNonNull(completeness);
        warnings = copy(warnings);
    }

    private static String text(String value) {
        return value == null ? "" : value.strip();
    }

    private static List<String> copy(List<String> values) {
        return List.copyOf(values == null ? List.of() : values);
    }
}

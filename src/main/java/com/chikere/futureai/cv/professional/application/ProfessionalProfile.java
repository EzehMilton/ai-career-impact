package com.chikere.futureai.cv.professional.application;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * The small, reviewable set of professional facts understood from a CV.
 *
 * <p>The employer, dates and experience basis and exclusions only tell the user which jobs were used. They are
 * never sent to an assessment and never persisted.</p>
 *
 * @param experienceBasis    preformatted descriptions of the jobs counted towards {@code yearsExperience},
 *                           at most ten
 * @param experienceExcluded preformatted descriptions of the dated jobs that were not counted, at most ten
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
        List<String> experienceBasis,
        List<String> experienceExcluded,
        ProfessionalProfileCompleteness completeness,
        List<String> warnings) implements Serializable {
    public static final int MAXIMUM_EXPERIENCE_BASIS = 10;

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
        experienceBasis = copy(experienceBasis).stream().limit(MAXIMUM_EXPERIENCE_BASIS).toList();
        experienceExcluded = copy(experienceExcluded).stream().limit(MAXIMUM_EXPERIENCE_BASIS).toList();
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

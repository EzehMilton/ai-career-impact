package com.chikere.futureai.assessment.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a university applicant tells us about a course.
 *
 * <p>This is deliberately not {@link ProfessionalAssessmentState}. It describes a course of study
 * rather than work a person already performs, and it never requires employment experience.</p>
 */
public record UniversityAssessmentState(
        String courseTitle,
        String courseDescription,
        List<String> keyModules,
        List<String> skillsDeveloped,
        List<String> preferredCareerAreas,
        Optional<String> institution,
        Optional<String> country) implements AssessmentState {

    public UniversityAssessmentState {
        courseTitle = DomainValues.requiredText(courseTitle, "courseTitle");
        courseDescription = DomainValues.requiredText(courseDescription, "courseDescription");
        keyModules = DomainValues.textList(keyModules, "keyModules");
        skillsDeveloped = DomainValues.textList(skillsDeveloped, "skillsDeveloped");
        preferredCareerAreas = DomainValues.textList(preferredCareerAreas, "preferredCareerAreas");
        Objects.requireNonNull(institution, "institution must not be null");
        institution = institution.map(value -> DomainValues.requiredText(value, "institution"));
        Objects.requireNonNull(country, "country must not be null");
        country = country.map(value -> DomainValues.requiredText(value, "country"));
    }

    @Override
    public JourneyType journey() {
        return JourneyType.UNIVERSITY;
    }
}

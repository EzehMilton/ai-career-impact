package com.chikere.futureai.assessment.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What a sixth-form student tells us about their A-level choices.
 *
 * <p>This is deliberately neither {@link ProfessionalAssessmentState} nor
 * {@link UniversityAssessmentState}. It describes a subject combination a student is taking or
 * considering, and it never requires the student to have chosen a career or a degree.</p>
 *
 * <p>Subject names are canonicalised on construction so the same combination is stored, judged and
 * displayed consistently however it was typed. Canonicalisation attaches no score to any
 * subject.</p>
 */
public record ALevelAssessmentState(
        List<String> selectedSubjects,
        Map<String, String> predictedGrades,
        List<String> strengthsAndInterests,
        List<String> preferredDegreeAreas,
        List<String> preferredCareerAreas,
        Optional<String> country,
        Optional<String> additionalContext) implements AssessmentState {

    /** A combination needs at least two subjects to be meaningful; UK students usually take three or four. */
    public static final int MINIMUM_SUBJECTS = 2;
    public static final int MAXIMUM_SUBJECTS = 5;

    public ALevelAssessmentState {
        selectedSubjects = canonicalSubjects(selectedSubjects);
        predictedGrades = validatedGrades(predictedGrades, selectedSubjects);
        strengthsAndInterests = DomainValues.textList(strengthsAndInterests, "strengthsAndInterests");
        preferredDegreeAreas = DomainValues.textList(preferredDegreeAreas, "preferredDegreeAreas");
        preferredCareerAreas = DomainValues.textList(preferredCareerAreas, "preferredCareerAreas");
        Objects.requireNonNull(country, "country must not be null");
        country = country.map(value -> DomainValues.requiredText(value, "country"));
        Objects.requireNonNull(additionalContext, "additionalContext must not be null");
        additionalContext = additionalContext
                .map(value -> DomainValues.requiredText(value, "additionalContext"));
    }

    @Override
    public JourneyType journey() {
        return JourneyType.A_LEVEL;
    }

    private static List<String> canonicalSubjects(List<String> subjects) {
        Objects.requireNonNull(subjects, "selectedSubjects must not be null");
        LinkedHashSet<String> canonical = new LinkedHashSet<>();
        for (String subject : subjects) {
            canonical.add(ALevelSubjects.canonicalise(
                    DomainValues.requiredText(subject, "selectedSubjects entry")));
        }
        if (canonical.size() < MINIMUM_SUBJECTS) {
            throw new IllegalArgumentException(
                    "selectedSubjects must contain at least " + MINIMUM_SUBJECTS + " distinct subjects");
        }
        if (canonical.size() > MAXIMUM_SUBJECTS) {
            throw new IllegalArgumentException(
                    "selectedSubjects must contain no more than " + MAXIMUM_SUBJECTS + " subjects");
        }
        return List.copyOf(canonical);
    }

    private static Map<String, String> validatedGrades(Map<String, String> grades, List<String> subjects) {
        Objects.requireNonNull(grades, "predictedGrades must not be null");
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        grades.forEach((subject, grade) -> {
            String canonical = ALevelSubjects.canonicalise(
                    DomainValues.requiredText(subject, "predictedGrades subject"));
            if (!subjects.contains(canonical)) {
                throw new IllegalArgumentException(
                        "predictedGrades may only contain selected subjects: " + canonical);
            }
            String value = DomainValues.requiredText(grade, "predictedGrades grade");
            if (value.length() > 12) {
                throw new IllegalArgumentException("predictedGrades grade must be short");
            }
            copy.put(canonical, value);
        });
        return Collections.unmodifiableMap(copy);
    }
}

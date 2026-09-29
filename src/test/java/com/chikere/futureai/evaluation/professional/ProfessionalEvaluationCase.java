package com.chikere.futureai.evaluation.professional;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

record ProfessionalEvaluationCase(
        String id,
        String profession,
        String workDescription,
        List<String> responsibilities,
        List<String> skills,
        Integer yearsExperience,
        String rationale) {

    ProfessionalEvaluationCase {
        id = requiredText(id, "id");
        profession = requiredText(profession, "profession");
        workDescription = requiredText(workDescription, "workDescription");
        responsibilities = List.copyOf(Objects.requireNonNull(responsibilities, "responsibilities must not be null"));
        skills = List.copyOf(Objects.requireNonNull(skills, "skills must not be null"));
        if (yearsExperience != null && yearsExperience < 0) {
            throw new IllegalArgumentException("yearsExperience must not be negative");
        }
        rationale = requiredText(rationale, "rationale");
    }

    ProfessionalAssessmentState toAssessmentState() {
        return new ProfessionalAssessmentState(
                profession,
                workDescription,
                responsibilities,
                skills,
                yearsExperience == null ? OptionalInt.empty() : OptionalInt.of(yearsExperience));
    }

    private static String requiredText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }
}

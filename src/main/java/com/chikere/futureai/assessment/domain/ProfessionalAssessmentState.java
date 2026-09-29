package com.chikere.futureai.assessment.domain;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

public record ProfessionalAssessmentState(
        String profession,
        String workDescription,
        List<String> responsibilities,
        List<String> skills,
        OptionalInt yearsOfExperience) implements AssessmentState {

    public ProfessionalAssessmentState {
        profession = DomainValues.requiredText(profession, "profession");
        workDescription = DomainValues.requiredText(workDescription, "workDescription");
        responsibilities = DomainValues.textList(responsibilities, "responsibilities");
        skills = DomainValues.textList(skills, "skills");
        Objects.requireNonNull(yearsOfExperience, "yearsOfExperience must not be null");
        if (yearsOfExperience.isPresent() && yearsOfExperience.getAsInt() < 0) {
            throw new IllegalArgumentException("yearsOfExperience must not be negative");
        }
    }

    @Override
    public JourneyType journey() {
        return JourneyType.PROFESSIONAL;
    }
}

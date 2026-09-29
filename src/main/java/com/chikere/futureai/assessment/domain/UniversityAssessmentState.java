package com.chikere.futureai.assessment.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record UniversityAssessmentState(
        String courseName,
        List<String> interests,
        Optional<String> careerGoals) implements AssessmentState {

    public UniversityAssessmentState {
        courseName = DomainValues.requiredText(courseName, "courseName");
        interests = DomainValues.textList(interests, "interests");
        Objects.requireNonNull(careerGoals, "careerGoals must not be null");
        careerGoals = careerGoals.map(value -> DomainValues.requiredText(value, "careerGoals"));
    }

    @Override
    public JourneyType journey() {
        return JourneyType.UNIVERSITY;
    }
}

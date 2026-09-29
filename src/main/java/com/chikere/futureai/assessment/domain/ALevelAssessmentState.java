package com.chikere.futureai.assessment.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ALevelAssessmentState(
        List<String> subjects,
        List<String> interests,
        List<String> strengths,
        Optional<String> careerInterests) implements AssessmentState {

    public ALevelAssessmentState {
        subjects = DomainValues.nonEmptyTextList(subjects, "subjects");
        interests = DomainValues.textList(interests, "interests");
        strengths = DomainValues.textList(strengths, "strengths");
        Objects.requireNonNull(careerInterests, "careerInterests must not be null");
        careerInterests = careerInterests.map(value -> DomainValues.requiredText(value, "careerInterests"));
    }

    @Override
    public JourneyType journey() {
        return JourneyType.A_LEVEL;
    }
}

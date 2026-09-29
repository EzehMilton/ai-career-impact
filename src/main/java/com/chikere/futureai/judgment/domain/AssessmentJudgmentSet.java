package com.chikere.futureai.judgment.domain;

import com.chikere.futureai.assessment.domain.JourneyType;

import java.util.List;
import java.util.Objects;

public record AssessmentJudgmentSet(
        JourneyType journey,
        String schemaVersion,
        List<DimensionJudgment> judgments) {

    public AssessmentJudgmentSet {
        Objects.requireNonNull(journey, "journey must not be null");
        Objects.requireNonNull(schemaVersion, "schemaVersion must not be null");
        if (schemaVersion.isBlank()) {
            throw new IllegalArgumentException("schemaVersion must not be blank");
        }
        schemaVersion = schemaVersion.strip();
        judgments = List.copyOf(Objects.requireNonNull(judgments, "judgments must not be null"));
    }
}

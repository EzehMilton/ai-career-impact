package com.chikere.futureai.assessment.domain;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

public record AssessmentDecision(
        JourneyType journey,
        OptionalDouble overallIndicator,
        String resultLabel,
        AssessmentConfidence confidence,
        AssessmentStatus status,
        List<String> keyDrivers,
        List<String> protectiveFactors) {

    public AssessmentDecision {
        Objects.requireNonNull(journey, "journey must not be null");
        Objects.requireNonNull(overallIndicator, "overallIndicator must not be null");
        if (overallIndicator.isPresent() && !Double.isFinite(overallIndicator.getAsDouble())) {
            throw new IllegalArgumentException("overallIndicator must be finite");
        }
        resultLabel = DomainValues.requiredText(resultLabel, "resultLabel");
        Objects.requireNonNull(confidence, "confidence must not be null");
        Objects.requireNonNull(status, "status must not be null");
        keyDrivers = DomainValues.textList(keyDrivers, "keyDrivers");
        protectiveFactors = DomainValues.textList(protectiveFactors, "protectiveFactors");
    }
}
